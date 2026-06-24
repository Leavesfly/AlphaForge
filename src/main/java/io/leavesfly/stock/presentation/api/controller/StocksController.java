package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.application.service.MarketAnalysisService;
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

    public StocksController(DataFetcherManager dataFetcher, MarketAnalysisService marketService) {
        this.dataFetcher = dataFetcher;
        this.marketService = marketService;
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
        // 简化实现: 实际需接入Vision API进行OCR识别
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("codes", List.of());
        result.put("items", List.of());
        result.put("raw_text", "图片识别功能待接入Vision API (" + filename + ")");
        return ResponseEntity.ok(result);
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
