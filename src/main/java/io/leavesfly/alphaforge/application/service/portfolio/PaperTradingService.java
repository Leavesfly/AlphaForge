package io.leavesfly.alphaforge.application.service.portfolio;

import io.leavesfly.alphaforge.domain.model.entity.portfolio.CashLedgerEntry;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioAccount;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.alphaforge.domain.model.entity.portfolio.PortfolioTrade;
import io.leavesfly.alphaforge.domain.service.port.MarketDataPort;
import io.leavesfly.alphaforge.infrastructure.persistence.portfolio.CashLedgerRepository;
import io.leavesfly.alphaforge.infrastructure.persistence.portfolio.PortfolioAccountRepository;
import io.leavesfly.alphaforge.infrastructure.persistence.portfolio.PortfolioRepository;
import io.leavesfly.alphaforge.infrastructure.persistence.portfolio.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 模拟交易服务
 *
 * 核心功能：创建模拟账户、模拟买入/卖出、自动更新持仓与资金、刷新实时盈亏
 * 交易流程闭环：下单 -> 获取实时行情 -> 扣减/增加资金 -> 更新持仓 -> 记录交易
 */
@Service
public class PaperTradingService {

    private static final Logger log = LoggerFactory.getLogger(PaperTradingService.class);

    /** 佣金费率：万分之三 */
    private static final double COMMISSION_RATE = 0.0003;
    /** 最低佣金 */
    private static final double MIN_COMMISSION = 5.0;
    /** 印花税税率：千分之一（卖出收取） */
    private static final double STAMP_TAX_RATE = 0.001;

    private final PortfolioAccountRepository accountRepo;
    private final PortfolioRepository positionRepo;
    private final TradeRepository tradeRepo;
    private final CashLedgerRepository cashRepo;
    private final MarketDataPort dataFetcher;

    public PaperTradingService(PortfolioAccountRepository accountRepo,
                               PortfolioRepository positionRepo,
                               TradeRepository tradeRepo,
                               CashLedgerRepository cashRepo,
                               MarketDataPort dataFetcher) {
        this.accountRepo = accountRepo;
        this.positionRepo = positionRepo;
        this.tradeRepo = tradeRepo;
        this.cashRepo = cashRepo;
        this.dataFetcher = dataFetcher;
    }

    // ========== 账户管理 ==========

    /**
     * 创建模拟账户
     */
    public PortfolioAccount createPaperAccount(String name, String market, double initialCapital) {
        PortfolioAccount account = new PortfolioAccount();
        account.setName(name);
        account.setMarket(market != null ? market : "cn");
        account.setBaseCurrency("CNY");
        account.setCashBalance(initialCapital);
        account.setLoanBalance(0.0);
        account.setLoanLimit(initialCapital * 2); // 默认融资额度为初始资金2倍
        account.setIsActive(true);
        account = accountRepo.save(account);

        // 记录初始入金流水
        if (initialCapital > 0) {
            CashLedgerEntry entry = new CashLedgerEntry();
            entry.setAccountId(account.getId());
            entry.setEventDate(LocalDate.now());
            entry.setDirection("deposit");
            entry.setAmount(initialCapital);
            entry.setCurrency("CNY");
            entry.setNote("模拟账户初始资金");
            cashRepo.save(entry);
        }

        log.info("创建模拟账户: {} 初始资金: {}", name, initialCapital);
        return account;
    }

    /**
     * 获取所有模拟账户
     */
    public List<PortfolioAccount> getPaperAccounts() {
        return accountRepo.findAll();
    }

    /**
     * 获取账户详情（含资金、持仓、总资产）
     */
    public Map<String, Object> getAccountDetail(Long accountId) {
        PortfolioAccount account = accountRepo.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账户不存在: " + accountId);
        }

        List<PortfolioPosition> positions = positionRepo.findByAccountId(accountId);
        double positionsValue = positions.stream()
                .mapToDouble(p -> p.getMarketValue() != null ? p.getMarketValue() : 0)
                .sum();
        double totalCost = positions.stream()
                .mapToDouble(p -> p.getCostPrice() != null ? p.getCostPrice() * p.getQuantity() : 0)
                .sum();
        double cashBalance = account.getCashBalance() != null ? account.getCashBalance() : 0;
        double loanBalance = account.getLoanBalance() != null ? account.getLoanBalance() : 0;
        double loanLimit = account.getLoanLimit() != null ? account.getLoanLimit() : 0;
        double totalAssets = cashBalance + positionsValue;
        double netAssets = totalAssets - loanBalance;
        double totalProfitLoss = positionsValue - totalCost;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("account", account);
        detail.put("cashBalance", cashBalance);
        detail.put("positionsValue", positionsValue);
        detail.put("totalAssets", totalAssets);
        detail.put("loanBalance", loanBalance);
        detail.put("loanLimit", loanLimit);
        detail.put("availableLoan", loanLimit - loanBalance);
        detail.put("netAssets", netAssets);
        detail.put("totalCost", totalCost);
        detail.put("totalProfitLoss", totalProfitLoss);
        detail.put("totalReturnPct", totalCost > 0 ? (totalProfitLoss / totalCost * 100) : 0);
        detail.put("positions", positions);
        return detail;
    }

    // ========== 模拟交易 ==========

    /**
     * 模拟买入
     */
    public Map<String, Object> buy(Long accountId, String stockCode, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("买入数量必须大于0");
        }
        if (quantity % 100 != 0) {
            throw new IllegalArgumentException("买入数量须为100的整数倍");
        }

        PortfolioAccount account = getPaperAccount(accountId);
        double price = getRealtimePrice(stockCode);
        double amount = price * quantity;
        double commission = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
        double totalCost = amount + commission;

        double cashBalance = account.getCashBalance() != null ? account.getCashBalance() : 0;
        if (cashBalance < totalCost) {
            throw new IllegalStateException(
                    String.format("资金不足: 需要 %.2f (含手续费 %.2f), 可用 %.2f", totalCost, commission, cashBalance));
        }

        // 1. 扣减现金
        account.setCashBalance(cashBalance - totalCost);
        accountRepo.save(account);

        // 2. 创建/更新持仓
        Map<String, Object> stockInfo = dataFetcher.getRealtimeQuote(stockCode);
        String stockName = (String) stockInfo.getOrDefault("stock_name", stockCode);
        PortfolioPosition position = createOrUpdatePosition(accountId, stockCode, stockName, quantity, price, true);

        // 3. 记录交易
        recordTrade(accountId, stockCode, "buy", quantity, price, commission, 0, stockName);

        log.info("模拟买入: 账户={} 股票={} 数量={} 价格={} 手续费={}",
                accountId, stockCode, quantity, String.format("%.2f", price), String.format("%.2f", commission));

        return buildTradeResult("buy", stockCode, stockName, quantity, price, amount, commission, 0,
                account.getCashBalance(), position);
    }

    /**
     * 模拟卖出
     */
    public Map<String, Object> sell(Long accountId, String stockCode, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("卖出数量必须大于0");
        }

        PortfolioAccount account = getPaperAccount(accountId);

        // 检查持仓
        PortfolioPosition position = positionRepo.findByAccountIdAndStockCode(accountId, stockCode);
        if (position == null || position.getQuantity() < quantity) {
            int holdQty = position != null ? position.getQuantity() : 0;
            throw new IllegalStateException(
                    String.format("持仓不足: 持有 %d 股, 尝试卖出 %d 股", holdQty, quantity));
        }

        double price = getRealtimePrice(stockCode);
        double amount = price * quantity;
        double commission = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
        double stampTax = amount * STAMP_TAX_RATE;
        double netProceeds = amount - commission - stampTax;

        // 1. 增加现金
        double cashBalance = account.getCashBalance() != null ? account.getCashBalance() : 0;
        account.setCashBalance(cashBalance + netProceeds);
        accountRepo.save(account);

        // 2. 减仓/清仓
        String stockName = position.getStockName() != null ? position.getStockName() : stockCode;
        int newQty = position.getQuantity() - quantity;
        if (newQty <= 0) {
            positionRepo.deleteById(position.getId());
        } else {
            position.setQuantity(newQty);
            // 成本价不变，更新市值和盈亏
            updatePositionMarketData(position, price);
            positionRepo.save(position);
        }

        // 3. 记录交易
        recordTrade(accountId, stockCode, "sell", quantity, price, commission, stampTax, stockName);

        log.info("模拟卖出: 账户={} 股票={} 数量={} 价格={} 手续费={} 印花税={}",
                accountId, stockCode, quantity, String.format("%.2f", price), String.format("%.2f", commission), String.format("%.2f", stampTax));

        return buildTradeResult("sell", stockCode, stockName, quantity, price, amount, commission, stampTax,
                account.getCashBalance(), newQty > 0 ? position : null);
    }

    // ========== 持仓刷新 ==========

    /**
     * 刷新模拟账户所有持仓的实时盈亏
     */
    public void refreshPaperPositions(Long accountId) {
        List<PortfolioPosition> positions = positionRepo.findByAccountId(accountId);
        double totalValue = 0;

        for (PortfolioPosition p : positions) {
            try {
                double currentPrice = getRealtimePrice(p.getStockCode());
                updatePositionMarketData(p, currentPrice);
                positionRepo.save(p);
                totalValue += p.getMarketValue() != null ? p.getMarketValue() : 0;
            } catch (Exception e) {
                log.error("刷新持仓失败: {}", p.getStockCode());
            }
        }

        // 更新仓位占比
        for (PortfolioPosition p : positions) {
            if (totalValue > 0 && p.getMarketValue() != null) {
                p.setPositionPct(p.getMarketValue() / totalValue * 100);
                positionRepo.save(p);
            }
        }
    }

    // ========== 账户重置 ==========

    /**
     * 重置模拟账户：清空持仓和交易记录，重置现金
     */
    public Map<String, Object> resetAccount(Long accountId, double newCapital) {
        PortfolioAccount account = getPaperAccount(accountId);

        // 清空持仓
        positionRepo.deleteByAccountId(accountId);
        // 清空交易记录
        tradeRepo.deleteByAccountId(accountId);
        // 清空资金流水
        cashRepo.deleteByAccountId(accountId);

        // 重置现金和融资
        account.setCashBalance(newCapital);
        account.setLoanBalance(0.0);
        account.setLoanLimit(newCapital * 2);
        accountRepo.save(account);

        // 记录新的初始入金
        if (newCapital > 0) {
            CashLedgerEntry entry = new CashLedgerEntry();
            entry.setAccountId(accountId);
            entry.setEventDate(LocalDate.now());
            entry.setDirection("deposit");
            entry.setAmount(newCapital);
            entry.setCurrency("CNY");
            entry.setNote("账户重置 - 初始资金");
            cashRepo.save(entry);
        }

        log.info("重置模拟账户: {} 新资金: {}", accountId, newCapital);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("accountId", accountId);
        result.put("newCapital", newCapital);
        return result;
    }

    // ========== 交易记录查询 ==========

    /**
     * 获取账户交易记录
     */
    public List<PortfolioTrade> getAccountTrades(Long accountId) {
        return tradeRepo.findByAccountId(accountId);
    }

    // ========== 融资管理 ==========

    /**
     * 模拟融资借款：增加现金，增加负债
     */
    public Map<String, Object> borrow(Long accountId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("借款金额必须大于0");
        }
        PortfolioAccount account = getPaperAccount(accountId);
        double currentLoan = account.getLoanBalance() != null ? account.getLoanBalance() : 0;
        double loanLimit = account.getLoanLimit() != null ? account.getLoanLimit() : 0;
        if (currentLoan + amount > loanLimit) {
            throw new IllegalStateException(
                    String.format("超过融资额度: 已借 %.2f + 本次 %.2f > 额度 %.2f", currentLoan, amount, loanLimit));
        }

        // 增加现金和负债
        double cash = account.getCashBalance() != null ? account.getCashBalance() : 0;
        account.setCashBalance(cash + amount);
        account.setLoanBalance(currentLoan + amount);
        accountRepo.save(account);

        // 记录资金流水
        CashLedgerEntry entry = new CashLedgerEntry();
        entry.setAccountId(accountId);
        entry.setEventDate(LocalDate.now());
        entry.setDirection("borrow");
        entry.setAmount(amount);
        entry.setCurrency("CNY");
        entry.setNote("模拟融资借款");
        cashRepo.save(entry);

        log.info("融资借款: 账户={} 金额={} 当前负债={}", accountId, amount, currentLoan + amount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("action", "borrow");
        result.put("amount", amount);
        result.put("cashBalance", account.getCashBalance());
        result.put("loanBalance", account.getLoanBalance());
        result.put("availableLoan", loanLimit - account.getLoanBalance());
        return result;
    }

    /**
     * 模拟归还融资：扣减现金，减少负债
     */
    public Map<String, Object> repay(Long accountId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("归还金额必须大于0");
        }
        PortfolioAccount account = getPaperAccount(accountId);
        double currentLoan = account.getLoanBalance() != null ? account.getLoanBalance() : 0;
        if (currentLoan <= 0) {
            throw new IllegalStateException("当前无融资负债");
        }
        double repayAmount = Math.min(amount, currentLoan);
        double cash = account.getCashBalance() != null ? account.getCashBalance() : 0;
        if (cash < repayAmount) {
            throw new IllegalStateException(
                    String.format("现金不足: 需要 %.2f, 可用 %.2f", repayAmount, cash));
        }

        // 扣减现金和负债
        account.setCashBalance(cash - repayAmount);
        account.setLoanBalance(currentLoan - repayAmount);
        accountRepo.save(account);

        // 记录资金流水
        CashLedgerEntry entry = new CashLedgerEntry();
        entry.setAccountId(accountId);
        entry.setEventDate(LocalDate.now());
        entry.setDirection("repay");
        entry.setAmount(repayAmount);
        entry.setCurrency("CNY");
        entry.setNote("归还模拟融资");
        cashRepo.save(entry);

        log.info("归还融资: 账户={} 金额={} 剩余负债={}", accountId, repayAmount, currentLoan - repayAmount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("action", "repay");
        result.put("amount", repayAmount);
        result.put("cashBalance", account.getCashBalance());
        result.put("loanBalance", account.getLoanBalance());
        return result;
    }

    // ========== 私有方法 ==========

    private PortfolioAccount getPaperAccount(Long accountId) {
        PortfolioAccount account = accountRepo.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账户不存在: " + accountId);
        }
        return account;
    }

    private double getRealtimePrice(String stockCode) {
        Map<String, Object> quote = dataFetcher.getRealtimeQuote(stockCode);
        if (quote == null || quote.isEmpty()) {
            throw new IllegalStateException("无法获取实时行情: " + stockCode);
        }
        Object priceObj = quote.get("current_price");
        if (priceObj instanceof Number) {
            double price = ((Number) priceObj).doubleValue();
            if (price <= 0) {
                throw new IllegalStateException("行情价格异常: " + stockCode);
            }
            return price;
        }
        throw new IllegalStateException("行情数据格式异常: " + stockCode);
    }

    private PortfolioPosition createOrUpdatePosition(Long accountId, String stockCode, String stockName,
                                                      int quantity, double price, boolean isBuy) {
        PortfolioPosition position = positionRepo.findByAccountIdAndStockCode(accountId, stockCode);
        if (position == null) {
            // 新建持仓
            position = new PortfolioPosition();
            position.setAccountId(accountId);
            position.setStockCode(stockCode);
            position.setStockName(stockName);
            position.setQuantity(quantity);
            position.setCostPrice(price);
            position.setCurrentPrice(price);
            position.setBuyDate(LocalDateTime.now());
        } else {
            // 加仓：加权平均成本
            int totalQty = position.getQuantity() + quantity;
            double avgCost = (position.getCostPrice() * position.getQuantity() + price * quantity) / totalQty;
            position.setQuantity(totalQty);
            position.setCostPrice(avgCost);
            position.setCurrentPrice(price);
        }
        updatePositionMarketData(position, price);
        return positionRepo.save(position);
    }

    private void updatePositionMarketData(PortfolioPosition position, double currentPrice) {
        position.setCurrentPrice(currentPrice);
        double marketValue = currentPrice * position.getQuantity();
        position.setMarketValue(marketValue);
        double cost = position.getCostPrice() != null ? position.getCostPrice() : 0;
        double profitLoss = (currentPrice - cost) * position.getQuantity();
        position.setProfitLoss(profitLoss);
        position.setProfitLossPct(cost > 0 ? (currentPrice - cost) / cost * 100 : 0);
    }

    private void recordTrade(Long accountId, String stockCode, String side, int quantity,
                             double price, double fee, double tax, String stockName) {
        PortfolioTrade trade = new PortfolioTrade();
        trade.setAccountId(accountId);
        trade.setSymbol(stockCode);
        trade.setTradeDate(LocalDate.now());
        trade.setSide(side);
        trade.setQuantity((double) quantity);
        trade.setPrice(price);
        trade.setFee(fee);
        trade.setTax(tax);
        trade.setCurrency("CNY");
        trade.setTradeUid(UUID.randomUUID().toString());
        trade.setNote(stockName + " - 模拟" + ("buy".equals(side) ? "买入" : "卖出"));
        tradeRepo.save(trade);
    }

    private Map<String, Object> buildTradeResult(String side, String stockCode, String stockName,
                                                  int quantity, double price, double amount,
                                                  double fee, double tax, Double cashBalance,
                                                  PortfolioPosition position) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("side", side);
        result.put("stockCode", stockCode);
        result.put("stockName", stockName);
        result.put("quantity", quantity);
        result.put("price", price);
        result.put("amount", amount);
        result.put("fee", fee);
        result.put("tax", tax);
        result.put("cashBalance", cashBalance);
        if (position != null) {
            result.put("positionQuantity", position.getQuantity());
            result.put("positionCostPrice", position.getCostPrice());
        }
        result.put("status", "ok");
        return result;
    }
}
