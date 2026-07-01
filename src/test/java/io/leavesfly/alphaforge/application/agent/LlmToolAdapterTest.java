package io.leavesfly.alphaforge.application.agent;

import io.leavesfly.alphaforge.application.agent.tools.ToolRegistry;
import io.leavesfly.alphaforge.domain.service.port.LlmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LlmToolAdapter 单元测试
 */
class LlmToolAdapterTest {

    private LlmPort llmService;
    private ToolRegistry toolRegistry;
    private LlmToolAdapter toolAdapter;

    @BeforeEach
    void setUp() {
        llmService = Mockito.mock(LlmPort.class);
        toolRegistry = Mockito.mock(ToolRegistry.class);
        toolAdapter = new LlmToolAdapter(llmService, toolRegistry, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    @DisplayName("无工具定义时使用 Legacy 模式，LLM 直接返回文本")
    void testNoToolsLegacyMode() {
        when(toolRegistry.getDefinitions()).thenReturn(List.of());
        when(llmService.chatWithMessages(anyList())).thenReturn("分析结果：买入");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "分析股票"));

        LlmToolAdapter.ToolCallResult result = toolAdapter.chatWithTools(messages, 5);

        assertEquals("分析结果：买入", result.getResponse());
        assertEquals(0, result.getTotalToolCalls());
    }

    @Test
    @DisplayName("原生 FC 模式 - LLM 无 tool_calls 直接返回")
    void testNativeFcNoToolCalls() {
        when(toolRegistry.getDefinitions()).thenReturn(List.of(
                Map.of("type", "function", "function", Map.of("name", "get_stock_price"))
        ));
        when(llmService.chatWithFunctionCalling(anyList(), anyList()))
                .thenReturn(LlmPort.LlmResponse.textOnly("直接分析结果"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "分析股票"));

        LlmToolAdapter.ToolCallResult result = toolAdapter.chatWithTools(messages, 5);

        assertEquals("直接分析结果", result.getResponse());
        assertEquals(0, result.getTotalToolCalls());
    }

    @Test
    @DisplayName("原生 FC 模式 - LLM 返回 tool_calls 后执行并返回最终结果")
    void testNativeFcWithToolCalls() {
        when(toolRegistry.getDefinitions()).thenReturn(List.of(
                Map.of("type", "function", "function", Map.of("name", "get_stock_price"))
        ));

        // 第一次调用：LLM 返回 tool_calls
        LlmPort.FunctionCall fc = new LlmPort.FunctionCall("call_1", "get_stock_price", "{\"code\":\"600519\"}");
        LlmPort.LlmResponse toolCallResponse = new LlmPort.LlmResponse("", List.of(fc));

        // 第二次调用：LLM 返回最终结果
        LlmPort.LlmResponse finalResponse = LlmPort.LlmResponse.textOnly("贵州茅台当前价格1800元");

        when(llmService.chatWithFunctionCalling(anyList(), anyList()))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        when(toolRegistry.execute(eq("get_stock_price"), anyMap()))
                .thenReturn("{\"price\":1800}");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "查询贵州茅台价格"));

        LlmToolAdapter.ToolCallResult result = toolAdapter.chatWithTools(messages, 5);

        assertEquals("贵州茅台当前价格1800元", result.getResponse());
        assertEquals(1, result.getTotalToolCalls());
        verify(toolRegistry, times(1)).execute(eq("get_stock_price"), anyMap());
    }

    @Test
    @DisplayName("原生 FC 模式 - 工具执行异常时不中断循环")
    void testNativeFcToolException() {
        when(toolRegistry.getDefinitions()).thenReturn(List.of(
                Map.of("type", "function", "function", Map.of("name", "get_stock_price"))
        ));

        LlmPort.FunctionCall fc = new LlmPort.FunctionCall("call_1", "get_stock_price", "{}");
        LlmPort.LlmResponse toolCallResponse = new LlmPort.LlmResponse("", List.of(fc));
        LlmPort.LlmResponse finalResponse = LlmPort.LlmResponse.textOnly("无法获取价格数据");

        when(llmService.chatWithFunctionCalling(anyList(), anyList()))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        when(toolRegistry.execute(eq("get_stock_price"), anyMap()))
                .thenThrow(new RuntimeException("工具执行失败"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "查询股票价格"));

        LlmToolAdapter.ToolCallResult result = toolAdapter.chatWithTools(messages, 5);

        assertEquals("无法获取价格数据", result.getResponse());
        assertEquals(1, result.getTotalToolCalls());
    }

    @Test
    @DisplayName("Legacy 模式 - 文本标记解析工具调用")
    void testLegacyModeTextMarker() {
        when(toolRegistry.getDefinitions()).thenReturn(List.of());
        when(llmService.chatWithMessages(anyList()))
                .thenReturn("[TOOL_CALL]{\"name\":\"get_stock_price\",\"args\":{}}[/TOOL_CALL]")
                .thenReturn("最终分析结果");

        when(toolRegistry.execute(eq("get_stock_price"), anyMap()))
                .thenReturn("{\"price\":100}");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "查询股票"));

        LlmToolAdapter.ToolCallResult result = toolAdapter.chatWithTools(messages, 5);

        assertEquals("最终分析结果", result.getResponse());
        assertEquals(1, result.getTotalToolCalls());
    }

    @Test
    @DisplayName("executeToolLoop - 无工具调用返回 null finalResponse")
    void testExecuteToolLoopNoToolCalls() {
        when(toolRegistry.getDefinitions()).thenReturn(List.of(
                Map.of("type", "function", "function", Map.of("name", "test_tool"))
        ));
        when(llmService.chatWithFunctionCalling(anyList(), anyList()))
                .thenReturn(LlmPort.LlmResponse.textOnly("直接回复"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "你好"));

        LlmToolAdapter.ToolCallSession session = toolAdapter.executeToolLoop(messages, 5, null);

        assertFalse(session.hasToolCalls());
        assertNull(session.getFinalResponse());
    }
}
