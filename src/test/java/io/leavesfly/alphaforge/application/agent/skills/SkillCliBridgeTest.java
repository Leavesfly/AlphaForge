package io.leavesfly.alphaforge.application.agent.skills;

import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SkillCliBridge 单元测试 — 标的转换、命令构造、JSON 解析与退出码映射。
 *
 * <p>进程链路用临时目录下的桩脚本（sh 伪装 uv）验证，不依赖真实 uv/Python 环境。</p>
 */
class SkillCliBridgeTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("标的代码转换 toSkillSymbol")
    class SymbolConversion {

        @ParameterizedTest
        @CsvSource({
                "600519,     600519.SH",
                "000001,     000001.SZ",
                "300750,     300750.SZ",
                "688111,     688111.SH",
                "830799,     830799.BJ",
                "hk00700,    00700.HK",
                "sh600519,   600519.SH",
                "sz000001,   000001.SZ",
                "AAPL,       AAPL.US",
                "600519.SH,  600519.SH",
                "aapl.us,    AAPL.US",
        })
        @DisplayName("各类代码格式统一转为 代码.市场后缀")
        void convertsToSkillFormat(String input, String expected) {
            assertEquals(expected, SkillCliBridge.toSkillSymbol(input));
        }

        @Test
        @DisplayName("空白输入原样返回")
        void blankInput() {
            assertEquals("", SkillCliBridge.toSkillSymbol(null));
            assertEquals("", SkillCliBridge.toSkillSymbol(""));
            assertEquals("", SkillCliBridge.toSkillSymbol("   "));
        }
    }

    @Nested
    @DisplayName("命令构造 buildCommand")
    class CommandBuilding {

        @Test
        @DisplayName("标准拼接：uv run python <script> <args> --json")
        void standardCommand() {
            SkillCliBridge bridge = new SkillCliBridge(tempDir, "uv", 60);
            List<String> cmd = bridge.buildCommand("run_stage.py", List.of("--symbol", "600519.SH"));
            assertEquals(List.of("uv", "run", "python", "run_stage.py",
                    "--symbol", "600519.SH", "--json"), cmd);
        }

        @Test
        @DisplayName("已含 --json 时不重复追加")
        void noDuplicateJsonFlag() {
            SkillCliBridge bridge = new SkillCliBridge(tempDir, "uv", 60);
            List<String> cmd = bridge.buildCommand("run_stage.py", List.of("--json"));
            assertEquals(List.of("uv", "run", "python", "run_stage.py", "--json"), cmd);
        }

        @Test
        @DisplayName("null 参数仅保留脚本与 --json")
        void nullArgs() {
            SkillCliBridge bridge = new SkillCliBridge(tempDir, "uv", 60);
            List<String> cmd = bridge.buildCommand("run_dca.py", null);
            assertEquals(List.of("uv", "run", "python", "run_dca.py", "--json"), cmd);
        }
    }

    @Nested
    @DisplayName("JSON 解析 parseResult")
    class JsonParsing {

        @Test
        @DisplayName("扁平契约：summary/next_steps 与业务字段同级")
        void flatPayload() {
            SkillCliBridge bridge = new SkillCliBridge(tempDir, "uv", 60);
            String json = """
                    {"schema":"alpha-forge/stage/v1","command":"run_stage.py",
                     "summary":"处于上升推进","next_steps":[{"action":"score","reason":"复核买点"}],
                     "stage":"advance","stage_cn":"推进","confidence":"high"}""";
            SkillResult result = bridge.parseResult(json);
            assertEquals("alpha-forge/stage/v1", result.schema());
            assertEquals("处于上升推进", result.summary());
            assertEquals(1, result.nextSteps().size());
            assertEquals("推进", result.str("stage_cn"));
            assertEquals("score", result.nextSteps().get(0).get("action"));
            assertTrue(result.formatNextSteps().contains("复核买点"));
        }

        @Test
        @DisplayName("空输出映射为 SKILL_BRIDGE_EMPTY")
        void emptyOutput() {
            SkillCliBridge bridge = new SkillCliBridge(tempDir, "uv", 60);
            ToolException e = assertThrows(ToolException.class, () -> bridge.parseResult("  "));
            assertEquals("SKILL_BRIDGE_EMPTY", e.getErrorCode());
        }

        @Test
        @DisplayName("非法 JSON 映射为 SKILL_BRIDGE_PARSE")
        void invalidJson() {
            SkillCliBridge bridge = new SkillCliBridge(tempDir, "uv", 60);
            ToolException e = assertThrows(ToolException.class, () -> bridge.parseResult("{bad"));
            assertEquals("SKILL_BRIDGE_PARSE", e.getErrorCode());
        }

        @Test
        @DisplayName("缺 summary/next_steps 时安全降级")
        void missingOptionalFields() {
            SkillCliBridge bridge = new SkillCliBridge(tempDir, "uv", 60);
            SkillResult result = bridge.parseResult("{\"foo\":1}");
            assertEquals(null, result.summary());
            assertTrue(result.nextSteps().isEmpty());
            assertEquals("", result.formatNextSteps());
        }
    }

    @Nested
    @DisplayName("进程执行 run")
    class ProcessExecution {

        private Path writeStub(String name, String body) throws IOException {
            Path stub = tempDir.resolve(name);
            Files.writeString(stub, "#!/bin/sh\n" + body + "\n");
            Files.setPosixFilePermissions(stub, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
            return stub;
        }

        @Test
        @DisplayName("未配置 scripts 目录时报 SKILL_BRIDGE_DISABLED")
        void disabledWhenUnavailable() {
            SkillCliBridge bridge = new SkillCliBridge(null, "uv", 60);
            assertFalse(bridge.isAvailable());
            ToolException e = assertThrows(ToolException.class,
                    () -> bridge.run("run_stage.py", List.of()));
            assertEquals("SKILL_BRIDGE_DISABLED", e.getErrorCode());
        }

        @Test
        @DisplayName("成功退出：解析 stdout JSON（stderr 进度信息不干扰）")
        void successPath() throws IOException {
            Path stub = writeStub("fake-uv", """
                    echo '进度：拉取K线...' 1>&2
                    echo '{"summary":"ok","stage_cn":"筑底","next_steps":[]}'""");
            SkillCliBridge bridge = new SkillCliBridge(tempDir, stub.toString(), 60);
            assertTrue(bridge.isAvailable());
            SkillResult result = bridge.run("run_stage.py", List.of("--symbol", "600519.SH"));
            assertEquals("ok", result.summary());
            assertEquals("筑底", result.str("stage_cn"));
        }

        @Test
        @DisplayName("退出码 2 映射参数错误并附 stderr 尾部")
        void paramErrorExitCode() throws IOException {
            Path stub = writeStub("fake-uv", """
                    echo '[error] 未知策略: foo（近似候选: macd）' 1>&2
                    exit 2""");
            SkillCliBridge bridge = new SkillCliBridge(tempDir, stub.toString(), 60);
            ToolException e = assertThrows(ToolException.class,
                    () -> bridge.run("run_validate.py", List.of()));
            assertEquals("SKILL_BRIDGE_FAILED", e.getErrorCode());
            assertTrue(e.getMessage().contains("参数错误"));
            assertTrue(e.getMessage().contains("近似候选"));
        }

        @Test
        @DisplayName("退出码 1 映射运行错误并给出 doctor 自检建议")
        void runtimeErrorExitCode() throws IOException {
            Path stub = writeStub("fake-uv", """
                    echo '[error] 数据源全部失败' 1>&2
                    exit 1""");
            SkillCliBridge bridge = new SkillCliBridge(tempDir, stub.toString(), 60);
            ToolException e = assertThrows(ToolException.class,
                    () -> bridge.run("run_backtest.py", List.of()));
            assertEquals("SKILL_BRIDGE_FAILED", e.getErrorCode());
            assertTrue(e.getMessage().contains("运行错误"));
            assertTrue(e.getMessage().contains("doctor"));
        }
    }

    @Nested
    @DisplayName("真实环境冒烟（需 SKILL_BRIDGE_DIR + uv 预热）")
    @EnabledIfEnvironmentVariable(named = "SKILL_BRIDGE_DIR", matches = ".+")
    class RealEnvironmentSmoke {

        @Test
        @DisplayName("run_stage.py 端到端：标的转换 → 进程执行 → 阶段结论")
        void stageEndToEnd() {
            SkillCliBridge bridge = new SkillCliBridge(new io.leavesfly.alphaforge.config.EnvVarProvider() {{
                init();
            }});
            assertTrue(bridge.isAvailable());
            SkillResult result = bridge.run("run_stage.py", List.of(
                    "--symbol", SkillCliBridge.toSkillSymbol("600000"), "--brief"));
            assertNotNull(result.summary());
            assertNotNull(result.str("stage_cn"));
        }
    }
}
