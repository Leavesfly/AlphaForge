package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.application.service.MarketAnalysisService;
import io.leavesfly.stock.infrastructure.llm.LlmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

/**
 * 股票数据API控制器 (对齐 dsa-web stocksApi)
 * 对应Python版本的 api/v1/endpoints/stocks.py
 */
@RestController
@RequestMapping("/api/v1/stocks")
public class StocksController {

    private final DataFetcherManager dataFetcher;
    private final MarketAnalysisService marketService;
    private final LlmService llmService;

    public StocksController(DataFetcherManager dataFetcher, MarketAnalysisService marketService, LlmService llmService) {
        this.dataFetcher = dataFetcher;
        this.marketService = marketService;
        this.llmService = llmService;
    }

    /** 获取股票实时行情 */
    @GetMapping("/{code}/quote")
    public ResponseEntity<?> getQuote(@PathVariable String code) {
        Map<String, Object> quote = dataFetcher.getRealtimeQuote(code);
        return quote.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(quote);
    }

    /** 获取股票历史数据 */
    @GetMapping("/{code}/history")
    public ResponseEntity<?> getHistory(@PathVariable String code, @RequestParam(defaultValue = "60") int days) {
        var data = dataFetcher.getHistoryData(code, LocalDate.now().minusDays(days), LocalDate.now());
        return ResponseEntity.ok(data);
    }

    /** 获取股票基本信息 */
    @GetMapping("/{code}/info")
    public ResponseEntity<?> getInfo(@PathVariable String code) {
        Map<String, Object> info = dataFetcher.getStockInfo(code);
        return info.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(info);
    }

    /** 大盘行情概览 */
    @GetMapping("/market/overview")
    public ResponseEntity<Map<String, Object>> getMarketOverview() {
        return ResponseEntity.ok(marketService.getMarketOverview());
    }

    /** 大盘复盘 */
    @GetMapping("/market/review")
    public ResponseEntity<Map<String, Object>> getMarketReview() {
        return ResponseEntity.ok(marketService.marketReview());
    }

    /** 图片识别股票代码 (对齐 dsa-web stocksApi.extractFromImage) */
    @PostMapping("/extract-from-image")
    public ResponseEntity<Map<String, Object>> extractFromImage(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> codes = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        String rawText = "";

        try {
            // 通过LLM Vision API识别图片中的股票代码
            byte[] imageBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/png";

            String prompt = "请识别这张图片中的所有股票代码和股票名称。" +
                    "请以JSON数组格式返回，每个元素包含 code(股票代码) 和 name(股票名称) 字段。" +
                    "只返回JSON数组，不要其他内容。如果没有识别到股票代码，返回空数组[]";

            String response = llmService.chatWithVision(prompt, base64Image, mimeType);
            rawText = response;

            // 解析LLM返回的JSON
            String jsonStr = extractJsonArray(response);
            if (jsonStr != null && !jsonStr.equals("[]")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, String>> parsed = mapper.readValue(jsonStr,
                    mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                for (Map<String, String> stock : parsed) {
                    String code = stock.getOrDefault("code", "").trim();
                    String name = stock.getOrDefault("name", "").trim();
                    if (!code.isEmpty()) {
                        codes.add(code);
                        items.add(Map.of("code", code, "name", name, "confidence", "high"));
                    }
                }
            }
        } catch (Exception e) {
            rawText = "图片识别失败: " + e.getMessage() + " (" + filename + ")";
        }

        result.put("codes", codes);
        result.put("items", items);
        result.put("raw_text", rawText);
        return ResponseEntity.ok(result);
    }

    /** 从AI响应中提取JSON数组 */
    private String extractJsonArray(String text) {
        if (text == null) return "[]";
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return "[]";
    }

    /** 解析导入文件/文本 (对齐 dsa-web stocksApi.parseImport) */
    @PostMapping("/parse-import")
    public ResponseEntity<Map<String, Object>> parseImport(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) Map<String, Object> body) {
        List<String> codes = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();

        // 从文本解析股票代码
        String text = null;
        if (body != null && body.containsKey("text")) {
            text = (String) body.get("text");
        }
        if (text != null && !text.isEmpty()) {
            // 简化解析: 按逗号/空格/换行分割
            String[] parts = text.split("[,\\s\\n]+");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && trimmed.matches(".*\\d+.*")) {
                    codes.add(trimmed);
                    items.add(Map.of("code", trimmed, "name", "", "confidence", "medium"));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("codes", codes);
        result.put("items", items);
        return ResponseEntity.ok(result);
    }
}
