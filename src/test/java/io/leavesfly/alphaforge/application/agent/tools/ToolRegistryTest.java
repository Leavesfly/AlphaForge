package io.leavesfly.alphaforge.application.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolRegistry 工具注册表测试")
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    /** 创建简易测试工具 */
    private Tool createTool(String name, String description) {
        return new Tool() {
            @Override
            public String name() { return name; }

            @Override
            public String description() { return description; }

            @Override
            public Map<String, Object> parameters() {
                Map<String, Object> params = new LinkedHashMap<>();
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("code", Map.of("type", "string", "description", "股票代码"));
                params.put("properties", properties);
                params.put("required", new String[]{"code"});
                params.put("type", "object");
                return params;
            }

            @Override
            public String execute(Map<String, Object> args) {
                return "result for " + args.getOrDefault("code", "unknown");
            }
        };
    }

    /** 创建会抛异常的工具 */
    private Tool createFailingTool(String name) {
        return new Tool() {
            @Override
            public String name() { return name; }

            @Override
            public String description() { return "always fails"; }

            @Override
            public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public String execute(Map<String, Object> args) throws ToolException {
                throw new ToolException("execution failed", "EXEC_ERROR");
            }
        };
    }

    @Nested
    @DisplayName("register & get - 注册与获取")
    class RegisterAndGetTests {

        @Test
        @DisplayName("注册后可通过名称获取")
        void registerAndGet() {
            Tool tool = createTool("get_price", "获取股价");
            registry.register(tool);
            Optional<Tool> result = registry.get("get_price");
            assertTrue(result.isPresent());
            assertEquals("get_price", result.get().name());
        }

        @Test
        @DisplayName("未注册工具返回empty Optional")
        void unregisteredToolReturnsEmpty() {
            Optional<Tool> result = registry.get("nonexistent");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("hasTool检查工具是否存在")
        void hasToolChecksExistence() {
            registry.register(createTool("get_price", "获取股价"));
            assertTrue(registry.hasTool("get_price"));
            assertFalse(registry.hasTool("nonexistent"));
        }

        @Test
        @DisplayName("count返回已注册工具数量")
        void countReturnsRegisteredCount() {
            assertEquals(0, registry.count());
            registry.register(createTool("tool1", "desc1"));
            assertEquals(1, registry.count());
            registry.register(createTool("tool2", "desc2"));
            assertEquals(2, registry.count());
        }
    }

    @Nested
    @DisplayName("unregister - 注销工具")
    class UnregisterTests {

        @Test
        @DisplayName("注销已注册工具")
        void unregisterExistingTool() {
            registry.register(createTool("get_price", "获取股价"));
            assertTrue(registry.hasTool("get_price"));
            registry.unregister("get_price");
            assertFalse(registry.hasTool("get_price"));
        }

        @Test
        @DisplayName("注销不存在的工具不抛异常")
        void unregisterNonExistentTool() {
            assertDoesNotThrow(() -> registry.unregister("nonexistent"));
        }
    }

    @Nested
    @DisplayName("execute - 执行工具")
    class ExecuteTests {

        @Test
        @DisplayName("正常执行工具返回结果")
        void executeReturnsResult() {
            registry.register(createTool("get_price", "获取股价"));
            String result = registry.execute("get_price", Map.of("code", "600519"));
            assertEquals("result for 600519", result);
        }

        @Test
        @DisplayName("执行未注册工具抛ToolException")
        void executeUnregisteredThrowsException() {
            ToolException ex = assertThrows(ToolException.class, () ->
                registry.execute("nonexistent", Map.of())
            );
            assertEquals("TOOL_NOT_FOUND", ex.getErrorCode());
        }

        @Test
        @DisplayName("工具抛ToolException时原样抛出")
        void toolThrowsToolException() {
            registry.register(createFailingTool("failing_tool"));
            ToolException ex = assertThrows(ToolException.class, () ->
                registry.execute("failing_tool", Map.of())
            );
            assertEquals("EXEC_ERROR", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("list - 列出工具名称")
    class ListTests {

        @Test
        @DisplayName("list返回所有已注册工具名称")
        void listReturnsAllNames() {
            registry.register(createTool("tool1", "desc1"));
            registry.register(createTool("tool2", "desc2"));
            List<String> names = registry.list();
            assertEquals(2, names.size());
            assertTrue(names.contains("tool1"));
            assertTrue(names.contains("tool2"));
        }

        @Test
        @DisplayName("空注册表list返回空列表")
        void emptyRegistryListReturnsEmpty() {
            List<String> names = registry.list();
            assertNotNull(names);
            assertTrue(names.isEmpty());
        }
    }

    @Nested
    @DisplayName("getDefinitions - OpenAI Function Calling格式")
    class GetDefinitionsTests {

        @Test
        @DisplayName("生成正确格式的工具定义")
        void generatesCorrectDefinitions() {
            registry.register(createTool("get_price", "获取股价"));
            List<Map<String, Object>> defs = registry.getDefinitions();
            assertEquals(1, defs.size());

            Map<String, Object> def = defs.get(0);
            assertEquals("function", def.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> fn = (Map<String, Object>) def.get("function");
            assertEquals("get_price", fn.get("name"));
            assertEquals("获取股价", fn.get("description"));
            assertNotNull(fn.get("parameters"));
        }
    }

    @Nested
    @DisplayName("getSummaries & getToolSummaryText - 工具摘要")
    class SummaryTests {

        @Test
        @DisplayName("getSummaries返回工具摘要列表")
        void getSummariesReturnsList() {
            registry.register(createTool("get_price", "获取股价"));
            List<String> summaries = registry.getSummaries();
            assertEquals(1, summaries.size());
            assertTrue(summaries.get(0).contains("get_price"));
            assertTrue(summaries.get(0).contains("获取股价"));
        }

        @Test
        @DisplayName("getToolSummaryText包含工具名称和描述")
        void getToolSummaryTextContainsNameAndDescription() {
            registry.register(createTool("get_price", "获取股价"));
            String text = registry.getToolSummaryText();
            assertNotNull(text);
            assertTrue(text.contains("get_price"));
            assertTrue(text.contains("获取股价"));
        }

        @Test
        @DisplayName("getToolSummaryText包含参数信息")
        void getToolSummaryTextContainsParameters() {
            registry.register(createTool("get_price", "获取股价"));
            String text = registry.getToolSummaryText();
            assertTrue(text.contains("code"));
            assertTrue(text.contains("必填"));
        }
    }

    @Nested
    @DisplayName("filter - 工具白名单过滤")
    class FilterTests {

        @Test
        @DisplayName("空白名单返回完整副本")
        void emptyWhitelistReturnsFullCopy() {
            registry.register(createTool("tool1", "desc1"));
            registry.register(createTool("tool2", "desc2"));
            ToolRegistry filtered = registry.filter(Collections.emptyList());
            assertEquals(2, filtered.count());
        }

        @Test
        @DisplayName("null白名单返回完整副本")
        void nullWhitelistReturnsFullCopy() {
            registry.register(createTool("tool1", "desc1"));
            registry.register(createTool("tool2", "desc2"));
            ToolRegistry filtered = registry.filter(null);
            assertEquals(2, filtered.count());
        }

        @Test
        @DisplayName("白名单过滤只保留指定工具")
        void whitelistFiltersTools() {
            registry.register(createTool("tool1", "desc1"));
            registry.register(createTool("tool2", "desc2"));
            registry.register(createTool("tool3", "desc3"));
            ToolRegistry filtered = registry.filter(List.of("tool1", "tool3"));
            assertEquals(2, filtered.count());
            assertTrue(filtered.hasTool("tool1"));
            assertTrue(filtered.hasTool("tool3"));
            assertFalse(filtered.hasTool("tool2"));
        }

        @Test
        @DisplayName("白名单中不存在的工具被忽略")
        void whitelistIgnoresNonExistent() {
            registry.register(createTool("tool1", "desc1"));
            ToolRegistry filtered = registry.filter(List.of("tool1", "nonexistent"));
            assertEquals(1, filtered.count());
        }
    }

    @Nested
    @DisplayName("clear - 清除工具")
    class ClearTests {

        @Test
        @DisplayName("clear后注册表为空")
        void clearEmptiesRegistry() {
            registry.register(createTool("tool1", "desc1"));
            registry.register(createTool("tool2", "desc2"));
            assertEquals(2, registry.count());
            registry.clear();
            assertEquals(0, registry.count());
        }
    }
}
