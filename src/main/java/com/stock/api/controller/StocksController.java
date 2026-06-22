package com.stock.api.controller;

import com.stock.dataprovider.DataFetcherManager;
import com.stock.service.MarketAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/**
 * 股票数据API控制器
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
}
