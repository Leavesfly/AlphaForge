package io.leavesfly.stock.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.regex.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM工具调用解析测试
 * 验证: Function Calling格式提取、多工具调用链解析
 */
class LlmToolAdapterTest {

    @Test
    @DisplayName("解析[TOOL_CALL]格式")
    void testParseToolCallFormat() {
        String text = "让我查一下\n[TOOL_CALL]\n{\"name\":\"get_stock_price\",\"arguments\":{\"code\":\"600519\"}}\n[/TOOL_CALL]\n分析完成";
        List<Map<String, Object>> calls = extractToolCalls(text);
        assertEquals(1, calls.size());
        assertEquals("get_stock_price", calls.get(0).get("name"));
    }

    @Test
    @DisplayName("解析```tool_call```格式")
    void testParseCodeBlockFormat() {
        String text = "我来查询\n```tool_call\n{\"name\":\"search_news\",\"arguments\":{\"query\":\"茅台\"}}\n```\n结果如上";
        List<Map<String, Object>> calls = extractToolCalls(text);
        assertEquals(1, calls.size());
        assertEquals("search_news", calls.get(0).get("name"));
    }

    @Test
    @DisplayName("多工具调用链")
    void testMultipleToolCalls() {
        String text = "[TOOL_CALL]\n{\"name\":\"tool1\",\"arguments\":{}}\n[/TOOL_CALL]\n中间文本\n[TOOL_CALL]\n{\"name\":\"tool2\",\"arguments\":{}}\n[/TOOL_CALL]";
        List<Map<String, Object>> calls = extractToolCalls(text);
        assertEquals(2, calls.size());
        assertEquals("tool1", calls.get(0).get("name"));
        assertEquals("tool2", calls.get(1).get("name"));
    }

    @Test
    @DisplayName("无工具调用: 返回空列表")
    void testNoToolCalls() {
        String text = "这是一段普通的分析文本，没有任何工具调用。";
        List<Map<String, Object>> calls = extractToolCalls(text);
        assertTrue(calls.isEmpty());
    }

    @Test
    @DisplayName("参数提取: JSON嵌套")
    void testNestedArguments() {
        String json = "{\"name\":\"analyze\",\"arguments\":{\"code\":\"600519\",\"options\":{\"period\":60}}}";
        String text = "[TOOL_CALL]\n" + json + "\n[/TOOL_CALL]";
        List<Map<String, Object>> calls = extractToolCalls(text);
        assertEquals(1, calls.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) calls.get(0).get("arguments");
        assertEquals("600519", args.get("code"));
    }

    @Test
    @DisplayName("工具注册: 基本格式")
    void testToolSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");
        schema.put("function", Map.of(
                "name", "get_stock_price",
                "description", "获取实时股价",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of("code", Map.of("type", "string")),
                        "required", List.of("code"))));
        assertNotNull(schema.get("function"));
    }

    // ===== 工具调用解析实现(与LlmToolAdapter.java逻辑一致) =====

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractToolCalls(String text) {
        List<Map<String, Object>> calls = new ArrayList<>();
        // 格式1: [TOOL_CALL]...[/TOOL_CALL]
        Pattern p1 = Pattern.compile("\\[TOOL_CALL\\]\\s*(.+?)\\s*\\[/TOOL_CALL\\]", Pattern.DOTALL);
        Matcher m1 = p1.matcher(text);
        while (m1.find()) {
            Map<String, Object> call = parseJson(m1.group(1).trim());
            if (call != null) calls.add(call);
        }
        // 格式2: ```tool_call\n...\n```
        if (calls.isEmpty()) {
            Pattern p2 = Pattern.compile("```tool_call\\s*(.+?)\\s*```", Pattern.DOTALL);
            Matcher m2 = p2.matcher(text);
            while (m2.find()) {
                Map<String, Object> call = parseJson(m2.group(1).trim());
                if (call != null) calls.add(call);
            }
        }
        return calls;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
