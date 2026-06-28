package io.leavesfly.stock.application.service.portfolio;

import io.leavesfly.stock.domain.model.entity.portfolio.CashLedgerEntry;
import io.leavesfly.stock.domain.model.entity.portfolio.PortfolioAccount;
import io.leavesfly.stock.domain.model.entity.portfolio.PortfolioPosition;
import io.leavesfly.stock.domain.model.entity.portfolio.PortfolioTrade;
import io.leavesfly.stock.infrastructure.dataprovider.DataFetcherManager;
import io.leavesfly.stock.infrastructure.persistence.portfolio.CashLedgerRepository;
import io.leavesfly.stock.infrastructure.persistence.portfolio.PortfolioAccountRepository;
import io.leavesfly.stock.infrastructure.persistence.portfolio.PortfolioRepository;
import io.leavesfly.stock.infrastructure.persistence.portfolio.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaperTradingService 模拟交易服务测试")
class PaperTradingServiceTest {

    @Mock
    private PortfolioAccountRepository accountRepo;
    @Mock
    private PortfolioRepository positionRepo;
    @Mock
    private TradeRepository tradeRepo;
    @Mock
    private CashLedgerRepository cashRepo;
    @Mock
    private DataFetcherManager dataFetcher;

    @InjectMocks
    private PaperTradingService service;

    private PortfolioAccount createPaperAccount(Long id, String name, double cash) {
        PortfolioAccount acc = new PortfolioAccount();
        acc.setId(id);
        acc.setName(name);
        acc.setCashBalance(cash);
        acc.setLoanBalance(0.0);
        acc.setLoanLimit(cash * 2);
        acc.setBaseCurrency("CNY");
        acc.setIsActive(true);
        return acc;
    }

    private Map<String, Object> createQuote(String code, String name, double price) {
        Map<String, Object> quote = new HashMap<>();
        quote.put("stock_code", code);
        quote.put("stock_name", name);
        quote.put("current_price", price);
        return quote;
    }

    // ========== 账户管理测试 ==========

    @Nested
    @DisplayName("createPaperAccount - 创建模拟账户")
    class CreatePaperAccountTests {

        @Test
        @DisplayName("创建账户并记录初始入金流水")
        void createAccountWithInitialCapital() {
            when(accountRepo.save(any())).thenAnswer(inv -> {
                PortfolioAccount acc = inv.getArgument(0);
                acc.setId(1L);
                return acc;
            });
            when(cashRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PortfolioAccount result = service.createPaperAccount("测试账户", "cn", 100000);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(100000, result.getCashBalance());
            verify(cashRepo).save(any(CashLedgerEntry.class));
        }

        @Test
        @DisplayName("零初始资金不记录入金流水")
        void zeroCapitalNoCashEntry() {
            when(accountRepo.save(any())).thenAnswer(inv -> {
                PortfolioAccount acc = inv.getArgument(0);
                acc.setId(1L);
                return acc;
            });

            service.createPaperAccount("零资金账户", "cn", 0);

            verify(cashRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAccountDetail - 账户详情")
    class GetAccountDetailTests {

        @Test
        @DisplayName("返回正确的资产概要")
        void returnsCorrectDetail() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 50000);
            when(accountRepo.findById(1L)).thenReturn(acc);

            PortfolioPosition p1 = new PortfolioPosition();
            p1.setStockCode("600519");
            p1.setQuantity(100);
            p1.setCostPrice(1800.0);
            p1.setMarketValue(190000.0);
            p1.setProfitLoss(10000.0);

            when(positionRepo.findByAccountId(1L)).thenReturn(List.of(p1));

            Map<String, Object> detail = service.getAccountDetail(1L);

            assertNotNull(detail);
            assertEquals(50000.0, (Double) detail.get("cashBalance"));
            assertEquals(190000.0, (Double) detail.get("positionsValue"));
            assertEquals(240000.0, (Double) detail.get("totalAssets"));
            assertEquals(10000.0, (Double) detail.get("totalProfitLoss"));
        }

        @Test
        @DisplayName("账户不存在抛出异常")
        void accountNotFoundThrows() {
            when(accountRepo.findById(999L)).thenReturn(null);
            assertThrows(IllegalArgumentException.class, () -> service.getAccountDetail(999L));
        }
    }

    // ========== 买入测试 ==========

    @Nested
    @DisplayName("buy - 模拟买入")
    class BuyTests {

        @Test
        @DisplayName("买入成功：扣减现金、创建持仓、记录交易")
        void buySuccess() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 200000);
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(dataFetcher.getRealtimeQuote("600519"))
                    .thenReturn(createQuote("600519", "贵州茅台", 1800.0));
            when(positionRepo.findByAccountIdAndStockCode(1L, "600519")).thenReturn(null);
            when(positionRepo.save(any())).thenAnswer(inv -> {
                PortfolioPosition p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tradeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.buy(1L, "600519", 100);

            assertEquals("buy", result.get("side"));
            assertEquals(100, result.get("quantity"));
            assertEquals(1800.0, (Double) result.get("price"));
            // 手续费 = 1800*100*0.0003 = 54 (大于最低5元)
            assertEquals(54.0, (Double) result.get("fee"), 0.01);
            // 现金 = 200000 - 180000 - 54 = 19946
            assertEquals(19946.0, (Double) result.get("cashBalance"), 0.01);
            verify(tradeRepo).save(any(PortfolioTrade.class));
        }

        @Test
        @DisplayName("加仓：加权平均成本价")
        void buyExistingPosition() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 200000);
            when(accountRepo.findById(1L)).thenReturn(acc);

            PortfolioPosition existing = new PortfolioPosition();
            existing.setId(1L);
            existing.setAccountId(1L);
            existing.setStockCode("600519");
            existing.setStockName("贵州茅台");
            existing.setQuantity(100);
            existing.setCostPrice(1800.0);

            when(positionRepo.findByAccountIdAndStockCode(1L, "600519")).thenReturn(existing);
            when(dataFetcher.getRealtimeQuote("600519"))
                    .thenReturn(createQuote("600519", "贵州茅台", 1900.0));
            when(positionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tradeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.buy(1L, "600519", 100);

            assertEquals(200, result.get("positionQuantity"));
            // 加权平均: (1800*100 + 1900*100) / 200 = 1850
            assertEquals(1850.0, (Double) result.get("positionCostPrice"), 0.01);
        }

        @Test
        @DisplayName("资金不足时抛出异常")
        void insufficientFundsThrows() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 10000);
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(dataFetcher.getRealtimeQuote("600519"))
                    .thenReturn(createQuote("600519", "贵州茅台", 1800.0));

            assertThrows(IllegalStateException.class, () -> service.buy(1L, "600519", 100));
        }

        @Test
        @DisplayName("非100整数倍数量抛出异常")
        void invalidQuantityThrows() {
            assertThrows(IllegalArgumentException.class, () -> service.buy(1L, "600519", 150));
        }

        @Test
        @DisplayName("无法获取行情时抛出异常")
        void noQuoteThrows() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 200000);
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(dataFetcher.getRealtimeQuote("600519")).thenReturn(Collections.emptyMap());

            assertThrows(IllegalStateException.class, () -> service.buy(1L, "600519", 100));
        }
    }

    // ========== 卖出测试 ==========

    @Nested
    @DisplayName("sell - 模拟卖出")
    class SellTests {

        @Test
        @DisplayName("卖出成功：增加现金、减仓、记录交易")
        void sellSuccess() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 50000);
            when(accountRepo.findById(1L)).thenReturn(acc);

            PortfolioPosition position = new PortfolioPosition();
            position.setId(1L);
            position.setAccountId(1L);
            position.setStockCode("600519");
            position.setStockName("贵州茅台");
            position.setQuantity(200);
            position.setCostPrice(1800.0);
            position.setCurrentPrice(1800.0);

            when(positionRepo.findByAccountIdAndStockCode(1L, "600519")).thenReturn(position);
            when(dataFetcher.getRealtimeQuote("600519"))
                    .thenReturn(createQuote("600519", "贵州茅台", 1900.0));
            when(positionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tradeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.sell(1L, "600519", 100);

            assertEquals("sell", result.get("side"));
            assertEquals(100, result.get("quantity"));
            assertEquals(1900.0, (Double) result.get("price"));
            // 手续费 = 1900*100*0.0003 = 57
            assertEquals(57.0, (Double) result.get("fee"), 0.01);
            // 印花税 = 1900*100*0.001 = 190
            assertEquals(190.0, (Double) result.get("tax"), 0.01);
            // 现金 = 50000 + 190000 - 57 - 190 = 239753
            assertEquals(239753.0, (Double) result.get("cashBalance"), 0.01);
            assertEquals(100, result.get("positionQuantity"));
            verify(tradeRepo).save(any(PortfolioTrade.class));
        }

        @Test
        @DisplayName("全部卖出后持仓删除")
        void sellAllDeletesPosition() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 50000);
            when(accountRepo.findById(1L)).thenReturn(acc);

            PortfolioPosition position = new PortfolioPosition();
            position.setId(1L);
            position.setAccountId(1L);
            position.setStockCode("600519");
            position.setStockName("贵州茅台");
            position.setQuantity(100);
            position.setCostPrice(1800.0);

            when(positionRepo.findByAccountIdAndStockCode(1L, "600519")).thenReturn(position);
            when(dataFetcher.getRealtimeQuote("600519"))
                    .thenReturn(createQuote("600519", "贵州茅台", 1900.0));
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tradeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.sell(1L, "600519", 100);

            assertNull(result.get("positionQuantity"));
            verify(positionRepo).deleteById(1L);
        }

        @Test
        @DisplayName("持仓不足时抛出异常")
        void insufficientPositionThrows() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 50000);
            when(accountRepo.findById(1L)).thenReturn(acc);

            PortfolioPosition position = new PortfolioPosition();
            position.setQuantity(50);
            when(positionRepo.findByAccountIdAndStockCode(1L, "600519")).thenReturn(position);

            assertThrows(IllegalStateException.class, () -> service.sell(1L, "600519", 100));
        }

        @Test
        @DisplayName("无持仓时抛出异常")
        void noPositionThrows() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 50000);
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(positionRepo.findByAccountIdAndStockCode(1L, "600519")).thenReturn(null);

            assertThrows(IllegalStateException.class, () -> service.sell(1L, "600519", 100));
        }
    }

    // ========== 重置测试 ==========

    @Nested
    @DisplayName("resetAccount - 重置账户")
    class ResetAccountTests {

        @Test
        @DisplayName("清空持仓、交易记录和流水，重置现金")
        void resetClearsAllAndSetsCapital() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 50000);
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cashRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.resetAccount(1L, 200000);

            assertEquals("ok", result.get("status"));
            assertEquals(200000.0, (Double) result.get("newCapital"));
            verify(positionRepo).deleteByAccountId(1L);
            verify(tradeRepo).deleteByAccountId(1L);
            verify(cashRepo).deleteByAccountId(1L);
            assertEquals(200000.0, acc.getCashBalance());
        }
    }

    // ========== 最低手续费测试 ==========

    @Nested
    @DisplayName("手续费计算")
    class FeeCalculationTests {

        @Test
        @DisplayName("小额交易使用最低手续费5元")
        void minimumCommissionApplied() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 100000);
            when(accountRepo.findById(1L)).thenReturn(acc);
            // 价格5元, 100股 = 500元, 佣金 = 500*0.0003 = 0.15 < 5, 应取5元
            when(dataFetcher.getRealtimeQuote("000001"))
                    .thenReturn(createQuote("000001", "平安银行", 5.0));
            when(positionRepo.findByAccountIdAndStockCode(1L, "000001")).thenReturn(null);
            when(positionRepo.save(any())).thenAnswer(inv -> {
                PortfolioPosition p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tradeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.buy(1L, "000001", 100);

            assertEquals(5.0, (Double) result.get("fee"), 0.01);
            // 现金 = 100000 - 500 - 5 = 99495
            assertEquals(99495.0, (Double) result.get("cashBalance"), 0.01);
        }
    }

    // ========== 融资测试 ==========

    @Nested
    @DisplayName("borrow - 模拟融资借款")
    class BorrowTests {

        @Test
        @DisplayName("借款成功：增加现金和负债")
        void borrowSuccess() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 100000);
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cashRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.borrow(1L, 50000);

            assertEquals("ok", result.get("status"));
            assertEquals(50000.0, (Double) result.get("amount"));
            assertEquals(150000.0, (Double) result.get("cashBalance"));
            assertEquals(50000.0, (Double) result.get("loanBalance"));
            // 额度 200000 - 50000 = 150000
            assertEquals(150000.0, (Double) result.get("availableLoan"));
            verify(cashRepo).save(any(CashLedgerEntry.class));
        }

        @Test
        @DisplayName("超过融资额度时抛出异常")
        void exceedLoanLimitThrows() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 100000);
            // loanLimit = 200000
            when(accountRepo.findById(1L)).thenReturn(acc);

            assertThrows(IllegalStateException.class, () -> service.borrow(1L, 250000));
        }

        @Test
        @DisplayName("多次借款累计不超过额度")
        void multipleBorrowsWithinLimit() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 100000);
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cashRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.borrow(1L, 80000);
            // 第二次借 120000, 总计 200000 = loanLimit, 刚好不超
            Map<String, Object> result = service.borrow(1L, 120000);
            assertEquals(200000.0, (Double) result.get("loanBalance"));
            assertEquals(0.0, (Double) result.get("availableLoan"));
        }
    }

    @Nested
    @DisplayName("repay - 归还融资")
    class RepayTests {

        @Test
        @DisplayName("归还成功：扣减现金和负债")
        void repaySuccess() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 100000);
            acc.setLoanBalance(50000.0);
            acc.setCashBalance(150000.0); // 100000 + 50000 借款
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cashRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.repay(1L, 30000);

            assertEquals("ok", result.get("status"));
            assertEquals(30000.0, (Double) result.get("amount"));
            assertEquals(120000.0, (Double) result.get("cashBalance"));
            assertEquals(20000.0, (Double) result.get("loanBalance"));
            verify(cashRepo).save(any(CashLedgerEntry.class));
        }

        @Test
        @DisplayName("归还金额超过负债时只还剩余负债")
        void repayMoreThanLoanOnlyRepaysRemaining() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 100000);
            acc.setLoanBalance(20000.0);
            acc.setCashBalance(120000.0);
            when(accountRepo.findById(1L)).thenReturn(acc);
            when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cashRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.repay(1L, 50000);

            // 只归还 20000
            assertEquals(20000.0, (Double) result.get("amount"));
            assertEquals(100000.0, (Double) result.get("cashBalance"));
            assertEquals(0.0, (Double) result.get("loanBalance"));
        }

        @Test
        @DisplayName("现金不足归还时抛出异常")
        void insufficientCashForRepayThrows() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 100000);
            acc.setLoanBalance(80000.0);
            acc.setCashBalance(10000.0);
            when(accountRepo.findById(1L)).thenReturn(acc);

            assertThrows(IllegalStateException.class, () -> service.repay(1L, 50000));
        }

        @Test
        @DisplayName("无负债时归还抛出异常")
        void noLoanToRepayThrows() {
            PortfolioAccount acc = createPaperAccount(1L, "测试", 100000);
            acc.setLoanBalance(0.0);
            when(accountRepo.findById(1L)).thenReturn(acc);

            assertThrows(IllegalStateException.class, () -> service.repay(1L, 10000));
        }
    }
}
