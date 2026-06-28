package io.leavesfly.alphaforge.domain.service.port;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import java.util.List;
import java.util.Map;

/**
 * 量化因子端口（依赖倒置）
 *
 * 定义量化因子的计算、评估和管理接口。
 * 具体实现可对接自研因子库或第三方因子服务。
 */
public interface FactorLibrary {

    /**
     * 计算指定因子值
     *
     * @param factorName 因子名称（如 "momentum_20d", "rsi_14", "volume_ratio"）
     * @param history    K 线历史数据
     * @return 因子值（归一化到合理范围）
     */
    double calculate(String factorName, List<StockDailyData> history);

    /**
     * 批量计算多个因子
     *
     * @param factorNames 因子名称列表
     * @param history     K 线历史数据
     * @return 因子名 -> 因子值
     */
    Map<String, Double> calculateBatch(List<String> factorNames, List<StockDailyData> history);

    /**
     * 获取因子的 IC（Information Coefficient）历史值
     *
     * @param factorName 因子名称
     * @param lookbackDays 回溯天数
     * @return IC 值序列（按时间排序）
     */
    List<Double> getFactorIC(String factorName, int lookbackDays);

    /**
     * 获取因子的 IR（Information Ratio）
     *
     * @param factorName 因子名称
     * @param lookbackDays 回溯天数
     * @return IR 值（IC 均值 / IC 标准差）
     */
    double getFactorIR(String factorName, int lookbackDays);

    /**
     * 获取所有可用因子名
     */
    List<String> listAvailableFactors();

    /**
     * 获取因子分类
     *
     * @return 分类名 -> 因子名列表
     */
    Map<String, List<String>> getFactorCategories();
}
