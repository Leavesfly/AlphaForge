package io.leavesfly.alphaforge.domain.service;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 技术分析服务
 *
 * 计算: MA、MACD、KDJ、RSI、BOLL、成交量分析等
 */
public class TechnicalAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(TechnicalAnalysisService.class);

    /** 纯算法委托，消除与 AlertIndicators 等处的重复实现 */
    private final TechnicalIndicatorCalculator calculator = new TechnicalIndicatorCalculator();

    /**
     * 执行完整技术分析
     *
     * @param historyData 历史K线数据
     * @return 技术分析结果
     */
    public Map<String, Object> analyze(List<StockDailyData> historyData) {
        if (historyData == null || historyData.size() < 5) {
            return Map.of("error", "数据不足，无法进行技术分析");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        
        double[] closes = historyData.stream().mapToDouble(d -> d.getClosePrice() != null ? d.getClosePrice() : 0).toArray();
        double[] highs = historyData.stream().mapToDouble(d -> d.getHighPrice() != null ? d.getHighPrice() : 0).toArray();
        double[] lows = historyData.stream().mapToDouble(d -> d.getLowPrice() != null ? d.getLowPrice() : 0).toArray();
        long[] volumes = historyData.stream().mapToLong(d -> d.getVolume() != null ? d.getVolume() : 0).toArray();

        // 均线分析
        result.put("ma_analysis", calculateMA(closes));
        
        // MACD分析
        result.put("macd", calculateMACD(closes));
        
        // KDJ分析
        result.put("kdj", calculateKDJ(highs, lows, closes));
        
        // RSI分析
        result.put("rsi", calculateRSI(closes));
        
        // 布林带分析
        result.put("boll", calculateBOLL(closes));
        
        // 量能分析
        result.put("volume_analysis", analyzeVolume(volumes));
        
        // 趋势判断
        result.put("trend", judgeTrend(closes));
        
        // 综合评分
        int totalScore = calculateTotalScore(result);
        result.put("total_score", totalScore);
        
        return result;
    }

    /**
     * 计算移动平均线
     */
    private Map<String, Object> calculateMA(double[] closes) {
        Map<String, Object> ma = new LinkedHashMap<>();
        int len = closes.length;
        
        if (len >= 5) ma.put("MA5", calculateSMA(closes, 5));
        if (len >= 10) ma.put("MA10", calculateSMA(closes, 10));
        if (len >= 20) ma.put("MA20", calculateSMA(closes, 20));
        if (len >= 30) ma.put("MA30", calculateSMA(closes, 30));
        if (len >= 60) ma.put("MA60", calculateSMA(closes, 60));

        // 判断均线排列
        double currentPrice = closes[len - 1];
        double ma5 = len >= 5 ? calculateSMA(closes, 5) : currentPrice;
        double ma10 = len >= 10 ? calculateSMA(closes, 10) : currentPrice;
        double ma20 = len >= 20 ? calculateSMA(closes, 20) : currentPrice;

        if (ma5 > ma10 && ma10 > ma20) {
            ma.put("arrangement", "多头排列");
        } else if (ma5 < ma10 && ma10 < ma20) {
            ma.put("arrangement", "空头排列");
        } else {
            ma.put("arrangement", "交叉纠缠");
        }
        
        return ma;
    }

    /**
     * 计算MACD指标
     */
    private Map<String, Object> calculateMACD(double[] closes) {
        Map<String, Object> macd = new LinkedHashMap<>();
        int len = closes.length;
        if (len < 26) {
            macd.put("status", "数据不足");
            return macd;
        }

        // 计算EMA12和EMA26
        double[] ema12 = calculateEMA(closes, 12);
        double[] ema26 = calculateEMA(closes, 26);

        // DIF = EMA12 - EMA26
        double[] dif = new double[len];
        for (int i = 0; i < len; i++) {
            dif[i] = ema12[i] - ema26[i];
        }

        // DEA = DIF的9日EMA
        double[] dea = calculateEMA(dif, 9);

        // MACD柱 = 2 * (DIF - DEA)
        double currentDif = dif[len - 1];
        double currentDea = dea[len - 1];
        double currentMacd = 2 * (currentDif - currentDea);

        macd.put("DIF", String.format("%.4f", currentDif));
        macd.put("DEA", String.format("%.4f", currentDea));
        macd.put("MACD", String.format("%.4f", currentMacd));
        
        // 判断金叉死叉
        if (len >= 2) {
            double prevDif = dif[len - 2];
            double prevDea = dea[len - 2];
            if (prevDif < prevDea && currentDif > currentDea) {
                macd.put("cross", "金叉");
            } else if (prevDif > prevDea && currentDif < currentDea) {
                macd.put("cross", "死叉");
            } else {
                macd.put("cross", currentDif > currentDea ? "多头" : "空头");
            }
        }

        return macd;
    }

    /**
     * 计算KDJ指标
     */
    private Map<String, Object> calculateKDJ(double[] highs, double[] lows, double[] closes) {
        Map<String, Object> kdj = new LinkedHashMap<>();
        int len = closes.length;
        if (len < 9) {
            kdj.put("status", "数据不足");
            return kdj;
        }

        double k = 50, d = 50;
        for (int i = 8; i < len; i++) {
            double highest = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
            for (int j = i - 8; j <= i; j++) {
                highest = Math.max(highest, highs[j]);
                lowest = Math.min(lowest, lows[j]);
            }
            double rsv = (highest == lowest) ? 50 : (closes[i] - lowest) / (highest - lowest) * 100;
            k = 2.0 / 3 * k + 1.0 / 3 * rsv;
            d = 2.0 / 3 * d + 1.0 / 3 * k;
        }
        double j = 3 * k - 2 * d;

        kdj.put("K", String.format("%.2f", k));
        kdj.put("D", String.format("%.2f", d));
        kdj.put("J", String.format("%.2f", j));

        if (k > 80 && d > 80) {
            kdj.put("status", "超买区间");
        } else if (k < 20 && d < 20) {
            kdj.put("status", "超卖区间");
        } else if (k > d) {
            kdj.put("status", "偏多");
        } else {
            kdj.put("status", "偏空");
        }

        return kdj;
    }

    /**
     * 计算RSI指标
     */
    private Map<String, Object> calculateRSI(double[] closes) {
        Map<String, Object> rsi = new LinkedHashMap<>();
        int len = closes.length;
        if (len < 15) {
            rsi.put("status", "数据不足");
            return rsi;
        }

        rsi.put("RSI6", String.format("%.2f", calculateRSIValue(closes, 6)));
        rsi.put("RSI12", String.format("%.2f", calculateRSIValue(closes, 12)));
        rsi.put("RSI24", String.format("%.2f", calculateRSIValue(closes, 24)));

        double rsi6 = calculateRSIValue(closes, 6);
        if (rsi6 > 80) {
            rsi.put("status", "超买");
        } else if (rsi6 < 20) {
            rsi.put("status", "超卖");
        } else {
            rsi.put("status", "中性");
        }

        return rsi;
    }

    /**
     * 计算布林带
     */
    private Map<String, Object> calculateBOLL(double[] closes) {
        Map<String, Object> boll = new LinkedHashMap<>();
        int len = closes.length;
        if (len < 20) {
            boll.put("status", "数据不足");
            return boll;
        }

        double ma20 = calculateSMA(closes, 20);
        double std20 = calculateStd(closes, 20);
        double upper = ma20 + 2 * std20;
        double lower = ma20 - 2 * std20;
        double current = closes[len - 1];

        boll.put("upper", String.format("%.2f", upper));
        boll.put("middle", String.format("%.2f", ma20));
        boll.put("lower", String.format("%.2f", lower));

        double width = (upper - lower) / ma20 * 100;
        boll.put("bandwidth", String.format("%.2f%%", width));

        if (current > upper) {
            boll.put("position", "突破上轨");
        } else if (current < lower) {
            boll.put("position", "跌破下轨");
        } else if (current > ma20) {
            boll.put("position", "中轨上方");
        } else {
            boll.put("position", "中轨下方");
        }

        return boll;
    }

    /**
     * 量能分析
     */
    private Map<String, Object> analyzeVolume(long[] volumes) {
        Map<String, Object> result = new LinkedHashMap<>();
        int len = volumes.length;
        if (len < 5) return result;

        // 近5日平均成交量
        long sum5 = 0;
        for (int i = len - 5; i < len; i++) sum5 += volumes[i];
        long avg5 = sum5 / 5;

        // 近20日平均成交量
        long avg20 = avg5;
        if (len >= 20) {
            long sum20 = 0;
            for (int i = len - 20; i < len; i++) sum20 += volumes[i];
            avg20 = sum20 / 20;
        }

        double volumeRatio = avg20 > 0 ? (double) avg5 / avg20 : 1.0;
        result.put("volume_ratio", String.format("%.2f", volumeRatio));
        result.put("avg5_volume", avg5);
        result.put("avg20_volume", avg20);

        if (volumeRatio > 2.0) {
            result.put("status", "显著放量");
        } else if (volumeRatio > 1.5) {
            result.put("status", "温和放量");
        } else if (volumeRatio < 0.5) {
            result.put("status", "显著缩量");
        } else if (volumeRatio < 0.7) {
            result.put("status", "温和缩量");
        } else {
            result.put("status", "量能平稳");
        }

        return result;
    }

    /**
     * 趋势判断
     */
    private String judgeTrend(double[] closes) {
        int len = closes.length;
        if (len < 20) return "数据不足";

        double ma5 = calculateSMA(closes, 5);
        double ma10 = calculateSMA(closes, 10);
        double ma20 = calculateSMA(closes, 20);
        double current = closes[len - 1];

        if (current > ma5 && ma5 > ma10 && ma10 > ma20) {
            return "强势上涨";
        } else if (current > ma20 && ma5 > ma20) {
            return "震荡偏多";
        } else if (current < ma5 && ma5 < ma10 && ma10 < ma20) {
            return "弱势下跌";
        } else if (current < ma20 && ma5 < ma20) {
            return "震荡偏空";
        } else {
            return "横盘整理";
        }
    }

    /**
     * 计算综合评分
     */
    private int calculateTotalScore(Map<String, Object> result) {
        int score = 50; // 基础分
        
        // 趋势加分
        String trend = (String) result.get("trend");
        if ("强势上涨".equals(trend)) score += 20;
        else if ("震荡偏多".equals(trend)) score += 10;
        else if ("弱势下跌".equals(trend)) score -= 20;
        else if ("震荡偏空".equals(trend)) score -= 10;

        // MACD加分
        Map<?, ?> macd = (Map<?, ?>) result.get("macd");
        if (macd != null) {
            String cross = (String) macd.get("cross");
            if ("金叉".equals(cross)) score += 10;
            else if ("死叉".equals(cross)) score -= 10;
        }

        // KDJ加分
        Map<?, ?> kdj = (Map<?, ?>) result.get("kdj");
        if (kdj != null) {
            String status = (String) kdj.get("status");
            if ("超卖区间".equals(status)) score += 10;
            else if ("超买区间".equals(status)) score -= 10;
        }

        return Math.max(0, Math.min(100, score));
    }

    // ========== 数学计算辅助方法（委托 TechnicalIndicatorCalculator，避免重复实现） ==========

    private double calculateSMA(double[] data, int period) {
        return calculator.sma(data, period);
    }

    private double[] calculateEMA(double[] data, int period) {
        return calculator.ema(data, period);
    }

    private double calculateRSIValue(double[] closes, int period) {
        return calculator.rsi(closes, period);
    }

    private double calculateStd(double[] data, int period) {
        return calculator.std(data, period);
    }
}
