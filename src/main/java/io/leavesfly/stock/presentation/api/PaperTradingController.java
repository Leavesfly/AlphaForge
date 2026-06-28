package io.leavesfly.stock.presentation.api;

import io.leavesfly.stock.application.service.portfolio.PaperTradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 模拟交易 API
 * 创建模拟账户、模拟买卖下单、持仓管理、交易记录
 */
@RestController
@RequestMapping("/api/v1/paper-trading")
public class PaperTradingController {

    private final PaperTradingService paperTradingService;

    public PaperTradingController(PaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
    }

    // ==================== 账户 ====================

    /** 模拟账户列表 */
    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> accounts() {
        var accountList = paperTradingService.getPaperAccounts();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var acc : accountList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", acc.getId());
            item.put("name", acc.getName());
            item.put("market", acc.getMarket());
            item.put("cashBalance", acc.getCashBalance());
            item.put("isActive", acc.getIsActive());
            item.put("createdAt", acc.getCreatedAt());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    /** 创建模拟账户 */
    @PostMapping("/accounts")
    public ResponseEntity<?> createAccount(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "账户名称不能为空"));
        }
        String market = (String) body.getOrDefault("market", "cn");
        double initialCapital = 100000;
        Object capitalObj = body.get("initialCapital");
        if (capitalObj instanceof Number) {
            initialCapital = ((Number) capitalObj).doubleValue();
        }
        var account = paperTradingService.createPaperAccount(name, market, initialCapital);
        return ResponseEntity.ok(account);
    }

    /** 账户详情 */
    @GetMapping("/accounts/{id}")
    public ResponseEntity<?> accountDetail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(paperTradingService.getAccountDetail(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 交易 ====================

    /** 模拟买入 */
    @PostMapping("/accounts/{id}/buy")
    public ResponseEntity<?> buy(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String stockCode = (String) body.get("stockCode");
        if (stockCode == null || stockCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "股票代码不能为空"));
        }
        Object qtyObj = body.get("quantity");
        if (!(qtyObj instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "数量不能为空"));
        }
        int quantity = ((Number) qtyObj).intValue();
        try {
            return ResponseEntity.ok(paperTradingService.buy(id, stockCode, quantity));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 模拟卖出 */
    @PostMapping("/accounts/{id}/sell")
    public ResponseEntity<?> sell(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String stockCode = (String) body.get("stockCode");
        if (stockCode == null || stockCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "股票代码不能为空"));
        }
        Object qtyObj = body.get("quantity");
        if (!(qtyObj instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "数量不能为空"));
        }
        int quantity = ((Number) qtyObj).intValue();
        try {
            return ResponseEntity.ok(paperTradingService.sell(id, stockCode, quantity));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 刷新持仓实时数据 */
    @PostMapping("/accounts/{id}/positions/refresh")
    public ResponseEntity<?> refreshPositions(@PathVariable Long id) {
        try {
            paperTradingService.refreshPaperPositions(id);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "持仓数据已刷新"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 重置账户 */
    @PostMapping("/accounts/{id}/reset")
    public ResponseEntity<?> reset(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        double capital = 100000;
        Object capitalObj = body.get("capital");
        if (capitalObj instanceof Number) {
            capital = ((Number) capitalObj).doubleValue();
        }
        try {
            return ResponseEntity.ok(paperTradingService.resetAccount(id, capital));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 交易记录 */
    @GetMapping("/accounts/{id}/trades")
    public ResponseEntity<?> trades(@PathVariable Long id) {
        return ResponseEntity.ok(paperTradingService.getAccountTrades(id));
    }

    // ==================== 融资管理 ====================

    /** 模拟融资借款 */
    @PostMapping("/accounts/{id}/borrow")
    public ResponseEntity<?> borrow(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object amtObj = body.get("amount");
        if (!(amtObj instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "借款金额不能为空"));
        }
        double amount = ((Number) amtObj).doubleValue();
        try {
            return ResponseEntity.ok(paperTradingService.borrow(id, amount));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 归还模拟融资 */
    @PostMapping("/accounts/{id}/repay")
    public ResponseEntity<?> repay(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object amtObj = body.get("amount");
        if (!(amtObj instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "归还金额不能为空"));
        }
        double amount = ((Number) amtObj).doubleValue();
        try {
            return ResponseEntity.ok(paperTradingService.repay(id, amount));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
