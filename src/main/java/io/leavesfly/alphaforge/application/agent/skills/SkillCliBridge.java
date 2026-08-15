package io.leavesfly.alphaforge.application.agent.skills;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import io.leavesfly.alphaforge.config.EnvVarProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * alpha-forge-skill CLI 桥接 — 以子进程方式调用 skill 侧 run_*.py --json。
 *
 * <p>零改造复用 alpha-forge-skill 的量化研究能力（阶段定位、DCA/XIRR、走步+PBO 验证、
 * 方法论预设筛选等）：在 {@code SKILL_BRIDGE_DIR} 指向的 skill 仓库 scripts/ 目录下
 * 执行 {@code uv run python <script> <args> --json}，解析统一 JSON 契约。</p>
 *
 * <p>运行约束（与 skill 侧 SKILL.md 对齐）：</p>
 * <ul>
 *   <li>stdout 仅含纯 JSON，进度信息在 stderr（本类将 stderr 落临时文件，失败时附尾部）；</li>
 *   <li>退出码 0=成功 / 1=运行错误（数据、网络）/ 2=参数错误；</li>
 *   <li>首次使用须对 skill 仓库预热 {@code uv sync}，否则首跑会同步安装依赖远超超时预算。</li>
 * </ul>
 */
public class SkillCliBridge {

    private static final Logger log = LoggerFactory.getLogger(SkillCliBridge.class);

    /** stderr 尾部附带到错误信息的最大字符数 */
    private static final int STDERR_TAIL_CHARS = 800;

    private final Path scriptsDir;
    private final String uvBin;
    private final long timeoutSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillCliBridge(EnvVarProvider env) {
        this(resolveScriptsDir(env.get("SKILL_BRIDGE_DIR", "")),
                env.get("SKILL_BRIDGE_UV_BIN", "uv").trim(),
                env.getInt("SKILL_BRIDGE_TIMEOUT_SECONDS", 300));
    }

    private static Path resolveScriptsDir(String dir) {
        return dir == null || dir.isBlank() ? null : Path.of(dir.trim()).resolve("scripts");
    }

    /** 测试友好构造器 */
    SkillCliBridge(Path scriptsDir, String uvBin, long timeoutSeconds) {
        this.scriptsDir = scriptsDir;
        this.uvBin = uvBin.isEmpty() ? "uv" : uvBin;
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
    }

    /** skill 仓库是否已配置且 scripts/ 目录存在 */
    public boolean isAvailable() {
        return scriptsDir != null && Files.isDirectory(scriptsDir);
    }

    public Path getScriptsDir() {
        return scriptsDir;
    }

    /**
     * 执行 skill 侧脚本并解析 JSON 输出。
     *
     * @param script 脚本文件名，如 run_stage.py
     * @param args   命令行参数（不含 --json，由本方法统一追加）
     * @throws ToolException 未配置、超时、非零退出码或 JSON 解析失败
     */
    public SkillResult run(String script, List<String> args) {
        if (!isAvailable()) {
            throw new ToolException(
                    "alpha-forge-skill 未接入：请配置 SKILL_BRIDGE_DIR 指向 skill 仓库根目录（含 scripts/）",
                    "SKILL_BRIDGE_DISABLED");
        }
        List<String> cmd = buildCommand(script, args);
        log.info("Skill CLI 调用: {}", String.join(" ", cmd));

        Path stderrFile = null;
        try {
            stderrFile = Files.createTempFile("skill-bridge-", ".err");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(scriptsDir.toFile());
            pb.redirectError(stderrFile.toFile());
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            Thread drainer = drain(process.getInputStream(), stdout);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ToolException(
                        "Skill CLI 执行超时（" + timeoutSeconds + "s）: " + script
                                + "；全市场类命令较慢，可调大 SKILL_BRIDGE_TIMEOUT_SECONDS",
                        "SKILL_BRIDGE_TIMEOUT");
            }
            drainer.join(5000);

            int exit = process.exitValue();
            if (exit != 0) {
                String tail = readTail(stderrFile, STDERR_TAIL_CHARS);
                String kind = exit == 2 ? "参数错误" : "运行错误";
                String hint = exit == 2
                        ? "请修正参数后重试（stderr 中通常附近似候选建议）"
                        : "可在 skill 仓库执行 `uv run python run_list.py --doctor` 自检环境";
                throw new ToolException(
                        "Skill CLI " + kind + "（exit=" + exit + "）: " + script + "\n" + tail
                                + "\n建议：" + hint,
                        "SKILL_BRIDGE_FAILED");
            }
            SkillResult result = parseResult(stdout.toString());
            log.info("Skill CLI 执行成功: {} 输出 {}B", script, stdout.length());
            return result;
        } catch (ToolException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Skill CLI 调用被中断: " + script, e, "SKILL_BRIDGE_ERROR");
        } catch (IOException e) {
            throw new ToolException("Skill CLI 调用异常: " + script + "（" + e.getMessage()
                    + "）；请确认 uv 已安装且 skill 仓库已预热 uv sync", e, "SKILL_BRIDGE_ERROR");
        } finally {
            if (stderrFile != null) {
                try {
                    Files.deleteIfExists(stderrFile);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }

    /** 构造命令行：uv run python <script> <args> --json */
    List<String> buildCommand(String script, List<String> args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(uvBin);
        cmd.add("run");
        cmd.add("python");
        cmd.add(script);
        if (args != null) {
            cmd.addAll(args);
        }
        if (!cmd.contains("--json")) {
            cmd.add("--json");
        }
        return cmd;
    }

    /** 解析 --json 扁平契约：顶层 summary/next_steps + 业务字段 */
    @SuppressWarnings("unchecked")
    SkillResult parseResult(String stdout) {
        String trimmed = stdout == null ? "" : stdout.trim();
        if (trimmed.isEmpty()) {
            throw new ToolException("Skill CLI 返回空输出（--json 未生效或脚本异常退出）",
                    "SKILL_BRIDGE_EMPTY");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(trimmed, Map.class);
            String summary = root.get("summary") instanceof String s ? s : null;
            List<Map<String, Object>> nextSteps = root.get("next_steps") instanceof List<?> l
                    ? (List<Map<String, Object>>) l
                    : List.of();
            String schema = root.get("schema") instanceof String s ? s : null;
            return new SkillResult(schema, summary, nextSteps, root, trimmed);
        } catch (JsonProcessingException e) {
            throw new ToolException("Skill CLI JSON 输出解析失败: " + e.getMessage(),
                    "SKILL_BRIDGE_PARSE");
        }
    }

    /**
     * AlphaForge 标的代码 → skill 格式（代码.市场后缀）。
     *
     * <p>600519 → 600519.SH；0/3 开头六位 → SZ；4/8/9 开头六位 → BJ；
     * hk00700 / sh600519 前缀形式 → 00700.HK / 600519.SH；
     * 纯字母代码 → AAPL.US；已含点号（600519.SH / AAPL.US）原样保留。</p>
     */
    public static String toSkillSymbol(String code) {
        if (code == null) {
            return "";
        }
        String c = code.trim();
        if (c.isEmpty()) {
            return c;
        }
        if (c.contains(".")) {
            return c.toUpperCase();
        }
        String lower = c.toLowerCase();
        for (String prefix : new String[]{"hk", "sh", "sz", "bj"}) {
            if (lower.startsWith(prefix) && lower.substring(2).matches("\\d+")) {
                return lower.substring(2) + "." + prefix.toUpperCase();
            }
        }
        if (c.matches("\\d{6}")) {
            char first = c.charAt(0);
            if (first == '6') {
                return c + ".SH";
            }
            if (first == '4' || first == '8' || first == '9') {
                return c + ".BJ";
            }
            return c + ".SZ";
        }
        return c.toUpperCase() + ".US";
    }

    /** 排空输入流到 StringBuilder（守护线程，防管道写满阻塞子进程） */
    private Thread drain(InputStream in, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (sink) {
                        sink.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
                // 进程终止时流关闭属正常
            }
        }, "skill-cli-stdout");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 读取文件尾部（错误信息通常在末尾） */
    private String readTail(Path file, int maxChars) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return "（stderr 无输出）";
            }
            return text.length() <= maxChars ? text : "…" + text.substring(text.length() - maxChars);
        } catch (IOException e) {
            return "（stderr 读取失败）";
        }
    }
}
