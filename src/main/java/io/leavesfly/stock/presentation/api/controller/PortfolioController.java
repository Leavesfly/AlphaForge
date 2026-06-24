package io.leavesfly.stock.presentation.api.controller;

import io.leavesfly.stock.domain.model.entity.*;
import io.leavesfly.stock.application.service.PortfolioService;
import io.leavesfly.stock.application.service.PortfolioExtService;
import io.leavesfly.stock.application.service.CsvImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

/**
 * 投资组合API控制器 (对齐 dsa-web portfolioApi)
 * 对应Python版本的 api/v1/endpoints/portfolio.py
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioExtService extService;
    private final CsvImportService csvImportService;

    public PortfolioController(PortfolioService portfolioService, PortfolioExtService extService, CsvImportService csvImportService) {
        this.portfolioService = portfolioService;
        this.extService = extService;
        this.csvImportService = csvImportService;
    }

    // ========== 账户管理 ==========

    /** 获取账户列表 */
    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> getAccounts(
            @RequestParam(defaultValue = "false", name = "include_inactive") boolean includeInactive) {
        List<PortfolioAccount> items = extService.getAccounts(includeInactive);
        return ResponseEntity.ok(Map.of("items", items, "total", items.size()));
    }

    /** 创建账户 */
    @PostMapping("/accounts")
    public ResponseEntity<PortfolioAccount> createAccount(@RequestBody Map<String, Object> request) {
        PortfolioAccount account = new PortfolioAccount();
        account.setName((String) request.getOrDefault("name", "新账户"));
        account.setBroker((String) request.getOrDefault("broker", ""));
        account.setMarket((String) request.getOrDefault("market", "A"));
        account.setBaseCurrency((String) request.getOrDefault("base_currency", "CNY"));
        account.setOwnerId((String) request.get("owner_id"));
        return ResponseEntity.ok(extService.createAccount(account));
    }

    /** 删除账户 */
    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable Long accountId) {
        extService.deleteAccount(accountId);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", accountId));
    }

    // ========== 快照与风控 ==========

    /** 获取持仓快照 */
    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> getSnapshot(
            @RequestParam(required = false, name = "account_id") Long accountId,
            @RequestParam(required = false, name = "as_of") String asOf,
            @RequestParam(required = false, name = "cost_method") String costMethod) {
        List<PortfolioPosition> positions = portfolioService.getAllPositions();
        Map<String, Object> summary = portfolioService.getPortfolioSummary();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("account_id", accountId);
        snapshot.put("as_of", asOf != null ? asOf : java.time.LocalDate.now().toString());
        snapshot.put("cost_method", costMethod != null ? costMethod : "fifo");
        snapshot.put("positions", positions);
        snapshot.put("total_market_value", summary.getOrDefault("total_market_value", 0));
        snapshot.put("total_cost", summary.getOrDefault("total_cost", 0));
        snapshot.put("total_profit_loss", summary.getOrDefault("total_profit_loss", 0));
        snapshot.put("total_profit_loss_pct", summary.getOrDefault("total_profit_loss_pct", 0));
        snapshot.put("base_currency", "CNY");
        return ResponseEntity.ok(snapshot);
    }

    /** 持仓分析 */
    @PostMapping("/positions/{symbol}/analysis")
    public ResponseEntity<Map<String, Object>> analyzePosition(@PathVariable String symbol,
            @RequestBody(required = false) Map<String, Object> request) {
        String taskId = UUID.randomUUID().toString().substring(0, 12);
        return ResponseEntity.ok(Map.of("status", "accepted", "task_id", taskId, "stock_code", symbol));
    }

    /** 风险评伋 */
    @GetMapping("/risk")
    public ResponseEntity<Map<String, Object>> getRisk(
            @RequestParam(required = false, name = "account_id") Long accountId,
            @RequestParam(required = false, name = "as_of") String asOf,
            @RequestParam(required = false, name = "cost_method") String costMethod) {
        Map<String, Object> risk = portfolioService.assessRisk();
        risk.put("account_id", accountId);
        return ResponseEntity.ok(risk);
    }

    /** 刷新汇率 */
    @PostMapping("/fx/refresh")
    public ResponseEntity<Map<String, Object>> refreshFx(
            @RequestParam(required = false, name = "account_id") Long accountId,
            @RequestParam(required = false, name = "as_of") String asOf) {
        return ResponseEntity.ok(Map.of("status", "refreshed", "pairs_updated", 0, "as_of", asOf != null ? asOf : java.time.LocalDate.now().toString()));
    }

    // ========== 交易记录 ==========

    /** 创建交易记录 */
    @PostMapping("/trades")
    public ResponseEntity<Map<String, Object>> createTrade(@RequestBody Map<String, Object> request) {
        PortfolioTrade trade = new PortfolioTrade();
        trade.setAccountId(request.get("account_id") != null ? ((Number) request.get("account_id")).longValue() : null);
        trade.setSymbol((String) request.get("symbol"));
        trade.setTradeDate(request.get("trade_date") != null ? LocalDate.parse((String) request.get("trade_date")) : LocalDate.now());
        trade.setSide((String) request.get("side"));
        trade.setQuantity(request.get("quantity") != null ? ((Number) request.get("quantity")).doubleValue() : 0);
        trade.setPrice(request.get("price") != null ? ((Number) request.get("price")).doubleValue() : 0);
        trade.setFee(request.get("fee") != null ? ((Number) request.get("fee")).doubleValue() : 0);
        trade.setTax(request.get("tax") != null ? ((Number) request.get("tax")).doubleValue() : 0);
        trade.setMarket((String) request.get("market"));
        trade.setCurrency((String) request.getOrDefault("currency", "CNY"));
        trade.setTradeUid((String) request.get("trade_uid"));
        trade.setNote((String) request.get("note"));
        PortfolioTrade saved = extService.createTrade(trade);
        return ResponseEntity.ok(Map.of("status", "created", "id", saved.getId()));
    }

    /** 获取交易记录列表 */
    @GetMapping("/trades")
    public ResponseEntity<Map<String, Object>> listTrades(
            @RequestParam(required = false, name = "account_id") Long accountId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(required = false, name = "date_from") String dateFrom,
            @RequestParam(required = false, name = "date_to") String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
        List<PortfolioTrade> items = extService.listTrades(accountId, symbol, side, page, pageSize);
        long total = extService.countTrades();
        return ResponseEntity.ok(Map.of("items", items, "total", total, "page", page));
    }

    /** 删除交易记录 */
    @DeleteMapping("/trades/{tradeId}")
    public ResponseEntity<Map<String, Object>> deleteTrade(@PathVariable Long tradeId) {
        extService.deleteTrade(tradeId);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", tradeId));
    }

    // ========== 现金流水 ==========

    /** 创建现金流水 */
    @PostMapping("/cash-ledger")
    public ResponseEntity<Map<String, Object>> createCashLedger(@RequestBody Map<String, Object> request) {
        CashLedgerEntry entry = new CashLedgerEntry();
        entry.setAccountId(request.get("account_id") != null ? ((Number) request.get("account_id")).longValue() : null);
        entry.setEventDate(request.get("event_date") != null ? LocalDate.parse((String) request.get("event_date")) : LocalDate.now());
        entry.setDirection((String) request.get("direction"));
        entry.setAmount(request.get("amount") != null ? ((Number) request.get("amount")).doubleValue() : 0);
        entry.setCurrency((String) request.getOrDefault("currency", "CNY"));
        entry.setNote((String) request.get("note"));
        CashLedgerEntry saved = extService.createCashEntry(entry);
        return ResponseEntity.ok(Map.of("status", "created", "id", saved.getId()));
    }

    /** 获取现金流水列表 */
    @GetMapping("/cash-ledger")
    public ResponseEntity<Map<String, Object>> listCashLedger(
            @RequestParam(required = false, name = "account_id") Long accountId,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false, name = "date_from") String dateFrom,
            @RequestParam(required = false, name = "date_to") String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
        List<CashLedgerEntry> items = extService.listCashLedger(accountId, direction, page, pageSize);
        long total = extService.countCashEntries();
        return ResponseEntity.ok(Map.of("items", items, "total", total, "page", page));
    }

    /** 删除现金流水 */
    @DeleteMapping("/cash-ledger/{entryId}")
    public ResponseEntity<Map<String, Object>> deleteCashLedger(@PathVariable Long entryId) {
        extService.deleteCashEntry(entryId);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", entryId));
    }

    // ========== 公司行动 ==========

    /** 创建公司行动 */
    @PostMapping("/corporate-actions")
    public ResponseEntity<Map<String, Object>> createCorporateAction(@RequestBody Map<String, Object> request) {
        CorporateAction action = new CorporateAction();
        action.setAccountId(request.get("account_id") != null ? ((Number) request.get("account_id")).longValue() : null);
        action.setSymbol((String) request.get("symbol"));
        action.setEffectiveDate(request.get("effective_date") != null ? LocalDate.parse((String) request.get("effective_date")) : LocalDate.now());
        action.setActionType((String) request.get("action_type"));
        action.setMarket((String) request.get("market"));
        action.setCurrency((String) request.getOrDefault("currency", "CNY"));
        action.setCashDividendPerShare(request.get("cash_dividend_per_share") != null ? ((Number) request.get("cash_dividend_per_share")).doubleValue() : null);
        action.setSplitRatio(request.get("split_ratio") != null ? ((Number) request.get("split_ratio")).doubleValue() : null);
        action.setNote((String) request.get("note"));
        CorporateAction saved = extService.createCorporateAction(action);
        return ResponseEntity.ok(Map.of("status", "created", "id", saved.getId()));
    }

    /** 获取公司行动列表 */
    @GetMapping("/corporate-actions")
    public ResponseEntity<Map<String, Object>> listCorporateActions(
            @RequestParam(required = false, name = "account_id") Long accountId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, name = "action_type") String actionType,
            @RequestParam(required = false, name = "date_from") String dateFrom,
            @RequestParam(required = false, name = "date_to") String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20", name = "page_size") int pageSize) {
        List<CorporateAction> items = extService.listCorporateActions(accountId, symbol, actionType, page, pageSize);
        long total = extService.countCorporateActions();
        return ResponseEntity.ok(Map.of("items", items, "total", total, "page", page));
    }

    /** 删除公司行动 */
    @DeleteMapping("/corporate-actions/{actionId}")
    public ResponseEntity<Map<String, Object>> deleteCorporateAction(@PathVariable Long actionId) {
        extService.deleteCorporateAction(actionId);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", actionId));
    }

    // ========== CSV导入 ==========

    /** 获取支持的券商列表 */
    @GetMapping("/imports/csv/brokers")
    public ResponseEntity<Map<String, Object>> listImportBrokers() {
        List<Map<String, String>> brokers = List.of(
            Map.of("id", "eastmoney", "name", "东方财富", "market", "A"),
            Map.of("id", "tonghuashun", "name", "同花顺", "market", "A"),
            Map.of("id", "futu", "name", "富途牛牛", "market", "HK,US"),
            Map.of("id", "tiger", "name", "老虎证券", "market", "US,HK"),
            Map.of("id", "longbridge", "name", "长桥", "market", "HK,US")
        );
        return ResponseEntity.ok(Map.of("brokers", brokers));
    }

    /** 解析CSV导入文件 */
    @PostMapping("/imports/csv/parse")
    public ResponseEntity<Map<String, Object>> parseCsvImport(
            @RequestParam String broker,
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = csvImportService.parseCsv(broker, file);
        return ResponseEntity.ok(result);
    }

    /** 提交CSV导入 */
    @PostMapping("/imports/csv/commit")
    public ResponseEntity<Map<String, Object>> commitCsvImport(
            @RequestParam(name = "account_id") Long accountId,
            @RequestParam String broker,
            @RequestParam(defaultValue = "false", name = "dry_run") boolean dryRun,
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = csvImportService.commitCsv(accountId, broker, file, dryRun, extService);
        return ResponseEntity.ok(result);
    }

    // ========== 旧端点兼容 ==========

    @GetMapping
    public ResponseEntity<List<PortfolioPosition>> getPositions() {
        return ResponseEntity.ok(portfolioService.getAllPositions());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(portfolioService.getPortfolioSummary());
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
