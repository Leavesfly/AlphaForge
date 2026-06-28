package io.leavesfly.stock.application.service.portfolio;

import io.leavesfly.stock.domain.model.entity.portfolio.CashLedgerEntry;
import io.leavesfly.stock.domain.model.entity.portfolio.CorporateAction;
import io.leavesfly.stock.domain.model.entity.portfolio.PortfolioAccount;
import io.leavesfly.stock.domain.model.entity.portfolio.PortfolioTrade;
import io.leavesfly.stock.infrastructure.persistence.portfolio.CashLedgerRepository;
import io.leavesfly.stock.infrastructure.persistence.portfolio.CorporateActionRepository;
import io.leavesfly.stock.infrastructure.persistence.portfolio.PortfolioAccountRepository;
import io.leavesfly.stock.infrastructure.persistence.portfolio.TradeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 投资组合扩展服务
 * 管理账户、交易记录、现金流水、公司行动
 */
@Service
public class PortfolioExtService {

    private final PortfolioAccountRepository accountRepo;
    private final TradeRepository tradeRepo;
    private final CashLedgerRepository cashRepo;
    private final CorporateActionRepository corpRepo;

    public PortfolioExtService(PortfolioAccountRepository accountRepo,
                               TradeRepository tradeRepo,
                               CashLedgerRepository cashRepo,
                               CorporateActionRepository corpRepo) {
        this.accountRepo = accountRepo;
        this.tradeRepo = tradeRepo;
        this.cashRepo = cashRepo;
        this.corpRepo = corpRepo;
    }

    // ========== 账户管理 ==========

    public List<PortfolioAccount> getAccounts(boolean includeInactive) {
        if (includeInactive) {
            return accountRepo.findAll();
        }
        return accountRepo.findByIsActive(true);
    }

    public PortfolioAccount createAccount(PortfolioAccount account) {
        if (account.getIsActive() == null) account.setIsActive(true);
        if (account.getBaseCurrency() == null) account.setBaseCurrency("CNY");
        return accountRepo.save(account);
    }

    public Optional<PortfolioAccount> getAccountById(Long id) {
        return accountRepo.findByIdOpt(id);
    }

    public void deleteAccount(Long id) {
        accountRepo.deleteById(id);
    }

    // ========== 交易记录 ==========

    public PortfolioTrade createTrade(PortfolioTrade trade) {
        return tradeRepo.save(trade);
    }

    public List<PortfolioTrade> listTrades(Long accountId, String symbol, String side, int page, int pageSize) {
        List<PortfolioTrade> all;
        if (accountId != null && symbol != null && !symbol.isEmpty()) {
            all = tradeRepo.findByAccountIdAndSymbol(accountId, symbol);
        } else if (accountId != null) {
            all = tradeRepo.findByAccountId(accountId);
        } else if (symbol != null && !symbol.isEmpty()) {
            all = tradeRepo.findBySymbol(symbol);
        } else {
            all = tradeRepo.findAll();
        }
        // 筛选 side
        if (side != null && !side.isEmpty()) {
            all = all.stream().filter(t -> side.equals(t.getSide())).collect(Collectors.toList());
        }
        // 分页
        int start = (page - 1) * pageSize;
        if (start >= all.size()) return List.of();
        return all.subList(start, Math.min(start + pageSize, all.size()));
    }

    public long countTrades() {
        return tradeRepo.count();
    }

    public void deleteTrade(Long id) {
        tradeRepo.deleteById(id);
    }

    // ========== 现金流水 ==========

    public CashLedgerEntry createCashEntry(CashLedgerEntry entry) {
        return cashRepo.save(entry);
    }

    public List<CashLedgerEntry> listCashLedger(Long accountId, String direction, int page, int pageSize) {
        List<CashLedgerEntry> all;
        if (accountId != null) {
            all = cashRepo.findByAccountId(accountId);
        } else if (direction != null && !direction.isEmpty()) {
            all = cashRepo.findByDirection(direction);
        } else {
            all = cashRepo.findAll();
        }
        if (direction != null && !direction.isEmpty() && accountId != null) {
            all = all.stream().filter(e -> direction.equals(e.getDirection())).collect(Collectors.toList());
        }
        int start = (page - 1) * pageSize;
        if (start >= all.size()) return List.of();
        return all.subList(start, Math.min(start + pageSize, all.size()));
    }

    public long countCashEntries() {
        return cashRepo.count();
    }

    public void deleteCashEntry(Long id) {
        cashRepo.deleteById(id);
    }

    // ========== 公司行动 ==========

    public CorporateAction createCorporateAction(CorporateAction action) {
        return corpRepo.save(action);
    }

    public List<CorporateAction> listCorporateActions(Long accountId, String symbol, String actionType, int page, int pageSize) {
        List<CorporateAction> all;
        if (accountId != null) {
            all = corpRepo.findByAccountId(accountId);
        } else if (symbol != null && !symbol.isEmpty()) {
            all = corpRepo.findBySymbol(symbol);
        } else {
            all = corpRepo.findAll();
        }
        if (actionType != null && !actionType.isEmpty()) {
            all = all.stream().filter(a -> actionType.equals(a.getActionType())).collect(Collectors.toList());
        }
        int start = (page - 1) * pageSize;
        if (start >= all.size()) return List.of();
        return all.subList(start, Math.min(start + pageSize, all.size()));
    }

    public long countCorporateActions() {
        return corpRepo.count();
    }

    public void deleteCorporateAction(Long id) {
        corpRepo.deleteById(id);
    }
}
