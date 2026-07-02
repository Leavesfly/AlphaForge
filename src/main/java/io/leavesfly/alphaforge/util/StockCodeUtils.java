package io.leavesfly.alphaforge.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 股票代码工具类
 */
public class StockCodeUtils {

    private static final Logger log = LoggerFactory.getLogger(StockCodeUtils.class);

    /** A股代码正则 */
    private static final Pattern A_SHARE_PATTERN = Pattern.compile("^(sh|sz|SH|SZ)?(\\d{6})$");
    /** 港股代码正则 */
    private static final Pattern HK_PATTERN = Pattern.compile("^(hk|HK)?(\\d{4,5})$");
    /** 美股代码正则 */
    private static final Pattern US_PATTERN = Pattern.compile("^[A-Za-z]{1,5}$");

    /**
     * 标准化股票代码
     * 统一去除前缀，保留纯代码
     */
    public static String normalize(String code) {
        if (code == null || code.isEmpty()) return "";
        code = code.trim();
        // 去除SH/SZ前缀
        code = code.replaceAll("^(sh|sz|SH|SZ)", "");
        return code;
    }

    /**
     * 判断是否为A股代码
     */
    public static boolean isAShare(String code) {
        if (code == null) return false;
        String normalized = normalize(code);
        return normalized.matches("^\\d{6}$");
    }

    /**
     * 判断是否为港股代码
     */
    public static boolean isHKStock(String code) {
        if (code == null) return false;
        return code.toLowerCase().startsWith("hk") || code.endsWith(".HK");
    }

    /**
     * 判断是否为美股代码
     */
    public static boolean isUSStock(String code) {
        if (code == null) return false;
        return US_PATTERN.matcher(code).matches();
    }

    /**
     * 获取股票所在交易所
     * 沪市: 6/9开头; 深市: 0/3/2开头; 北交所: 4/8开头
     */
    public static String getExchange(String code) {
        String normalized = normalize(code);
        if (!normalized.matches("^\\d{6}$")) return "UNKNOWN";
        char first = normalized.charAt(0);
        switch (first) {
            case '6': case '9': return "SSE";   // 上交所
            case '0': case '3': case '2': return "SZSE"; // 深交所
            case '4': case '8': return "BSE";   // 北交所
            default: return "UNKNOWN";
        }
    }

    // ========== 各数据源代码格式转换 ==========

    /**
     * 转为 Tushare 代码格式 (600519.SH / 000001.SZ)
     */
    public static String toTsCode(String stockCode) {
        String code = normalize(stockCode);
        if (code.startsWith("6") || code.startsWith("9")) return code + ".SH";
        return code + ".SZ";
    }

    /**
     * 转为东方财富 secId 格式 (1.600519 / 0.000001)
     * 沪市: 1.code, 深市/北交所: 0.code
     */
    public static String toSecId(String stockCode) {
        String code = normalize(stockCode);
        if (code.startsWith("6") || code.startsWith("9")
                || code.startsWith("11") || code.startsWith("13")) {
            return "1." + code;
        }
        return "0." + code;
    }

    /**
     * 转为新浪/腾讯带前缀格式 (sh600519 / sz000001)
     */
    public static String toPrefixedSymbol(String stockCode) {
        String code = normalize(stockCode);
        if (code.startsWith("6") || code.startsWith("9")) return "sh" + code;
        return "sz" + code;
    }

    /**
     * 判断是否为沪市代码 (6/9开头)
     */
    public static boolean isSSE(String code) {
        String normalized = normalize(code);
        return !normalized.isEmpty() && (normalized.startsWith("6") || normalized.startsWith("9"));
    }

    /**
     * 判断是否为深市代码 (0/3/2开头)
     */
    public static boolean isSZSE(String code) {
        String normalized = normalize(code);
        return !normalized.isEmpty() &&
                (normalized.startsWith("0") || normalized.startsWith("3") || normalized.startsWith("2"));
    }

    /**
     * 转为 TickFlow 代码格式 (600000.SH / 000001.SZ / AAPL.US / 00700.HK)
     * TickFlow 统一使用 代码.市场后缀 格式
     */
    public static String toTickFlowSymbol(String stockCode) {
        if (stockCode == null || stockCode.isEmpty()) return "";
        String code = stockCode.trim();

        // 已含市场后缀，直接返回
        if (code.contains(".")) return code.toUpperCase();

        // 港股前缀 hk / HK
        if (code.toLowerCase().startsWith("hk")) {
            String num = code.substring(2);
            return num + ".HK";
        }

        // 美股（纯字母）
        if (code.matches("^[A-Za-z]{1,5}$")) {
            return code.toUpperCase() + ".US";
        }

        // A股（6位数字）
        String normalized = normalize(code);
        if (normalized.matches("^\\d{6}$")) {
            if (normalized.startsWith("6") || normalized.startsWith("9")) return normalized + ".SH";
            if (normalized.startsWith("4") || normalized.startsWith("8")) return normalized + ".BJ";
            return normalized + ".SZ";
        }

        // 港股数字代码（4-5位）
        if (normalized.matches("^\\d{4,5}$")) {
            return normalized + ".HK";
        }

        return code;
    }

    /**
     * 格式化显示价格
     */
    public static String formatPrice(Double price) {
        if (price == null) return "-";
        return String.format("%.2f", price);
    }

    /**
     * 格式化显示涨跌幅
     */
    public static String formatChangePct(Double pct) {
        if (pct == null) return "-";
        return String.format("%+.2f%%", pct);
    }

    /**
     * 格式化成交量(万手/亿)
     */
    public static String formatVolume(Long volume) {
        if (volume == null) return "-";
        if (volume >= 100000000) {
            return String.format("%.2f亿", volume / 100000000.0);
        } else if (volume >= 10000) {
            return String.format("%.2f万", volume / 10000.0);
        }
        return String.valueOf(volume);
    }

    /**
     * 格式化金额(万/亿)
     */
    public static String formatAmount(Double amount) {
        if (amount == null) return "-";
        if (amount >= 100000000) {
            return String.format("%.2f亿", amount / 100000000.0);
        } else if (amount >= 10000) {
            return String.format("%.2f万", amount / 10000.0);
        }
        return String.format("%.0f", amount);
    }
}
