package io.leavesfly.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 股票名称到代码解析器
 * 对应Python版本的 src/services/name_to_code_resolver.py
 * 支持中文名/拼音/简写解析到标准代码
 */
@Service
public class NameToCodeResolver {

    private static final Logger log = LoggerFactory.getLogger(NameToCodeResolver.class);

    /** 热门股票别名映射 */
    private static final Map<String, String> ALIAS_MAP = new LinkedHashMap<>() {{
        put("茅台", "600519"); put("贵州茅台", "600519");
        put("腾讯", "hk00700"); put("阿里", "BABA"); put("阿里巴巴", "BABA");
        put("苹果", "AAPL"); put("特斯拉", "TSLA"); put("英伟达", "NVDA");
        put("比亚迪", "002594"); put("宁德时代", "300750");
        put("招商银行", "600036"); put("中国平安", "601318");
        put("美团", "hk03690"); put("京东", "JD"); put("百度", "BIDU");
        put("小米", "hk01810"); put("华为", "未上市");
        put("中芯国际", "688981"); put("海康威视", "002415");
    }};

    /**
     * 解析用户输入为标准股票代码
     *
     * @param input 用户输入(可能是代码、名称、简写)
     * @return 标准股票代码，无法解析返回原始输入
     */
    public String resolve(String input) {
        if (input == null || input.isEmpty()) return input;
        input = input.trim();

        // 已经是标准代码格式
        if (input.matches("^\\d{6}$") || input.matches("^[A-Z]{1,5}$") ||
            input.toLowerCase().startsWith("hk")) {
            return input;
        }

        // 别名匹配
        String code = ALIAS_MAP.get(input);
        if (code != null) return code;

        // 模糊匹配
        for (Map.Entry<String, String> entry : ALIAS_MAP.entrySet()) {
            if (entry.getKey().contains(input) || input.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 无法解析，返回原始输入
        log.debug("无法解析股票名称: {}", input);
        return input;
    }

    /**
     * 批量解析
     */
    public List<String> resolveBatch(String input) {
        List<String> codes = new ArrayList<>();
        // 支持逗号、空格、中文顿号分隔
        String[] parts = input.split("[,，、\\s]+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                codes.add(resolve(part));
            }
        }
        return codes;
    }

    /**
     * 检查是否为有效的股票代码格式
     */
    public boolean isValidCode(String code) {
        if (code == null || code.isEmpty()) return false;
        return code.matches("^\\d{6}$") ||       // A股
               code.matches("^[A-Z]{1,5}$") ||   // 美股
               code.toLowerCase().matches("^hk\\d{4,5}$"); // 港股
    }
}
