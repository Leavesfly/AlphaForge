package com.stock.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API端点集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("健康检查: 返回200 OK")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("分析历史: 返回列表")
    void testHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("系统配置: 返回配置概要")
    void testConfigEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/system-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llm_model").exists());
    }

    @Test
    @DisplayName("用量统计: 返回结构")
    void testUsageEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/usage"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Agent聊天: POST请求正确处理")
    void testChatEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"贵州茅台最近走势如何\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("分析触发: 缺少参数返回400")
    void testAnalysisRunMissingParams() throws Exception {
        mockMvc.perform(post("/api/v1/analysis/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("实时行情: 有效代码返回200")
    void testRealtimeQuote() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/600519/quote"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("告警列表: 返回列表")
    void testAlertsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("决策信号: 返回列表")
    void testDecisionSignalsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/decision-signals"))
                .andExpect(status().isOk());
    }
}
