package io.leavesfly.alphaforge.domain.service.port;

import java.util.Map;

/**
 * 订单执行端口 — 纸面/实盘适配器抽象（本次仅 Paper 实现）。
 */
public interface OrderExecutionPort {

    Map<String, Object> buy(Long accountId, String stockCode, int quantity);

    Map<String, Object> sell(Long accountId, String stockCode, int quantity);

    /** 账户权益摘要：cash、totalAssets、positionsValue、netAssets 等 */
    Map<String, Object> getAccountEquity(Long accountId);

    /** 持仓数量，无持仓返回 0 */
    int getPositionQuantity(Long accountId, String stockCode);
}
