package io.leavesfly.alphaforge.presentation.api;

import io.leavesfly.alphaforge.application.service.portfolio.CsvImportService;
import io.leavesfly.alphaforge.application.service.portfolio.PortfolioExtService;
import io.leavesfly.alphaforge.application.service.portfolio.PortfolioRiskService;
import io.leavesfly.alphaforge.application.service.portfolio.PortfolioService;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 投资组合 API
 * 持仓管理、交易记录、资金流水、公司行动、风险评估
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioExtService portfolioExtService;
    private final PortfolioRiskService riskService;
    private final CsvImportService csvImportService;

    public PortfolioController(PortfolioService portfolioService,
                              PortfolioExtService portfolioExtService,
                              PortfolioRiskService riskService,
                              CsvImportService csvImportService) {
        this.portfolioService = portfolioService;
        this.portfolioExtService = portfolioExtService;
        this.riskService = riskService;
        this.csvImportService = csvImportService;
    }

    // ==================== 持仓 ====================

    /** 持仓列表 */
    @GetMapping("/positions")
    public ResponseEntity<List<PortfolioPosition>> positions() {
        return ResponseEntity.ok(portfolioService.getAllPositions());
    }

    /** 组合概要 */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(portfolioService.getPortfolioSummary());
    }

    /** 添加持仓 */
    @PostMapping("/positions")
    public ResponseEntity<?> addPosition(@RequestBody PortfolioPosition position) {
        return ResponseEntity.ok(portfolioService.addPosition(position));
    }

    /** 删除持仓 */
    @DeleteMapping("/positions/{id}")
    public ResponseEntity<Map<String, Object>> deletePosition(@PathVariable Long id) {
        portfolioService.deletePosition(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** 刷新持仓实时数据 */
    @PostMapping("/positions/refresh")
    public ResponseEntity<Map<String, Object>> refreshPositions() {
        portfolioService.refreshPositions();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "持仓数据已刷新"));
    }

    // ==================== 账户 ====================

    /** 账户列表 */
    @GetMapping("/accounts")
    public ResponseEntity<List<PortfolioAccount>> accounts(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(portfolioExtService.getAccounts(includeInactive));
    }

    /** 创建账户 */
    @PostMapping("/accounts")
    public ResponseEntity<?> createAccount(@RequestBody PortfolioAccount account) {
        return ResponseEntity.ok(portfolioExtService.createAccount(account));
    }

    // ==================== 交易记录 ====================

    /** 交易记录列表 */
    @GetMapping("/trades")
    public ResponseEntity<List<PortfolioTrade>> trades(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(portfolioExtService.listTrades(accountId, symbol, side, page, pageSize));
    }

    /** 录入交易 */
    @PostMapping("/trades")
    public ResponseEntity<?> createTrade(@RequestBody PortfolioTrade trade) {
        return ResponseEntity.ok(portfolioExtService.createTrade(trade));
    }

    /** 删除交易 */
    @DeleteMapping("/trades/{id}")
    public ResponseEntity<Map<String, Object>> deleteTrade(@PathVariable Long id) {
        portfolioExtService.deleteTrade(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ==================== 资金流水 ====================

    /** 资金流水列表 */
    @GetMapping("/cash-ledger")
    public ResponseEntity<List<CashLedgerEntry>> cashLedger(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(portfolioExtService.listCashLedger(accountId, direction, page, pageSize));
    }

    /** 记录出入金 */
    @PostMapping("/cash-ledger")
    public ResponseEntity<?> createCashEntry(@RequestBody CashLedgerEntry entry) {
        return ResponseEntity.ok(portfolioExtService.createCashEntry(entry));
    }

    // ==================== 公司行动 ====================

    /** 公司行动列表 */
    @GetMapping("/corporate-actions")
    public ResponseEntity<List<CorporateAction>> corporateActions(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(portfolioExtService.listCorporateActions(accountId, symbol, actionType, page, pageSize));
    }

    /** 创建公司行动 */
    @PostMapping("/corporate-actions")
    public ResponseEntity<?> createCorporateAction(@RequestBody CorporateAction action) {
        return ResponseEntity.ok(portfolioExtService.createCorporateAction(action));
    }

    // ==================== 风险评估 ====================

    /** 组合风险评估 */
    @GetMapping("/risk")
    public ResponseEntity<Map<String, Object>> risk() {
        return ResponseEntity.ok(riskService.assessRisk());
    }

    // ==================== CSV导入 ====================

    /** 获取支持的券商列表 */
    @GetMapping("/import/brokers")
    public ResponseEntity<List<Map<String, String>>> supportedBrokers() {
        return ResponseEntity.ok(List.of(
            Map.of("id", "eastmoney", "name", "东方财富"),
            Map.of("id", "tonghuashun", "name", "同花顺"),
            Map.of("id", "futu", "name", "富途"),
            Map.of("id", "tiger", "name", "老虎证券"),
            Map.of("id", "longbridge", "name", "长桥证券"),
            Map.of("id", "huatai", "name", "华泰证券"),
            Map.of("id", "citic", "name", "中信证券"),
            Map.of("id", "cmb", "name", "招商证券")
        ));
    }

    /** CSV导入预览(解析不落库) */
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importPreview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "eastmoney") String broker) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        try {
            Map<String, Object> result = csvImportService.parseCsv(
                    broker, file.getBytes(), file.getOriginalFilename());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** CSV导入确认(落库) */
    @PostMapping(value = "/import/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importCommit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "eastmoney") String broker,
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        try {
            Map<String, Object> result = csvImportService.commitCsv(
                    accountId, broker, file.getBytes(), file.getOriginalFilename(), dryRun,
                    portfolioExtService);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
