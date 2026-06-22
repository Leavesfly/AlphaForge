package com.stock.api.controller;

import com.stock.model.entity.PortfolioPosition;
import com.stock.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 投资组合API控制器
 * 对应Python版本的 api/v1/endpoints/portfolio.py
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<List<PortfolioPosition>> getPositions() {
        return ResponseEntity.ok(portfolioService.getAllPositions());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(portfolioService.getPortfolioSummary());
    }

    @GetMapping("/risk")
    public ResponseEntity<Map<String, Object>> getRisk() {
        return ResponseEntity.ok(portfolioService.assessRisk());
    }

    @PostMapping
    public ResponseEntity<PortfolioPosition> addPosition(@RequestBody PortfolioPosition position) {
        return ResponseEntity.ok(portfolioService.addPosition(position));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        portfolioService.refreshPositions();
        return ResponseEntity.ok(Map.of("status", "refreshed"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePosition(@PathVariable Long id) {
        portfolioService.deletePosition(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
