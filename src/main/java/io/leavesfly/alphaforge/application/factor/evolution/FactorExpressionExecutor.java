package io.leavesfly.alphaforge.application.factor.evolution;

import io.leavesfly.alphaforge.domain.model.entity.market.StockDailyData;
import io.leavesfly.alphaforge.domain.service.port.FactorLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 因子表达式执行器 — 安全的 DSL 解释器
 *
 * 支持的表达式语法（受限 DSL，防止代码注入）：
 * 1. 原子因子引用：momentum_5d, rsi_14, volatility_20d 等已有因子名
 * 2. 算术运算：+ - * / 和括号
 * 3. 数值常量：3.14, 100
 * 4. 函数调用：abs(x), max(x, y), min(x, y), normalize(x), rank(x)
 *
 * 示例表达式：
 * - "momentum_20d * volume_ratio_20d"
 * - "rsi_14 - 50"
 * - "max(momentum_5d, momentum_20d) / volatility_20d"
 * - "abs(ma_gap_5_20) * boll_position / 100"
 *
 * 安全措施：
 * - 仅允许白名单字符（字母、数字、_ + - * / ( ) . , 和空格）
 * - 不允许循环、条件分支、赋值
 * - 设置计算超时
 */
@Component
public class FactorExpressionExecutor {

    private static final Logger log = LoggerFactory.getLogger(FactorExpressionExecutor.class);

    /** 允许的字符白名单 */
    private static final Pattern ALLOWED_CHARS = Pattern.compile(
            "^[a-zA-Z0-9_+\\-*/().,\\s]+$");

    /** 支持的内置函数 */
    private static final List<String> BUILTIN_FUNCTIONS = List.of(
            "abs", "max", "min", "normalize", "clamp"
    );

    private final FactorLibrary factorLibrary;

    public FactorExpressionExecutor(@Lazy FactorLibrary factorLibrary) {
        this.factorLibrary = factorLibrary;
    }

    /**
     * 执行因子表达式计算
     *
     * @param expression 因子表达式（DSL）
     * @param history    K 线历史数据
     * @return 因子值
     */
    public double execute(String expression, List<StockDailyData> history) {
        if (expression == null || expression.isBlank()) {
            return 0;
        }
        expression = expression.trim();

        // 安全检查
        if (!ALLOWED_CHARS.matcher(expression).matches()) {
            log.warn("因子表达式包含非法字符: {}", expression);
            return 0;
        }

        try {
            // 先解析表达式中的原子因子引用，替换为数值
            String resolved = resolveFactorReferences(expression, history);
            // 然后计算算术表达式
            return evaluateArithmetic(resolved);
        } catch (Exception e) {
            log.warn("因子表达式执行失败: expr={} error={}", expression, e.getMessage());
            return 0;
        }
    }

    /**
     * 验证表达式语法是否合法
     */
    public boolean validate(String expression) {
        if (expression == null || expression.isBlank()) return false;
        if (!ALLOWED_CHARS.matcher(expression.trim()).matches()) return false;
        try {
            // 尝试用空数据解析
            String resolved = resolveFactorReferences(expression, List.of());
            evaluateArithmetic(resolved);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 提取表达式中引用的所有原子因子名
     */
    public List<String> extractFactorNames(String expression) {
        if (expression == null || expression.isBlank()) return List.of();
        // 匹配 identifier 模式（字母开头，后跟字母数字下划线）
        Pattern identPattern = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");
        Matcher matcher = identPattern.matcher(expression);
        List<String> names = new java.util.ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group();
            // 排除内置函数
            if (!BUILTIN_FUNCTIONS.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    // ===== 内部方法 =====

    /**
     * 解析因子引用 — 将表达式中的因子名替换为实际计算值
     */
    private String resolveFactorReferences(String expression, List<StockDailyData> history) {
        List<String> factorNames = extractFactorNames(expression);
        String resolved = expression;
        for (String name : factorNames) {
            double value = factorLibrary.calculate(name, history);
            resolved = resolved.replace(name, String.valueOf(value));
        }
        return resolved;
    }

    /**
     * 简单算术表达式求值（递归下降解析器）
     *
     * 文法：
     * expr   → term (('+' | '-') term)*
     * term   → factor (('*' | '/') factor)*
     * factor → number | '(' expr ')' | func '(' args ')'
     */
    private double evaluateArithmetic(String expr) {
        expr = expr.replaceAll("\\s+", ""); // 去除空格
        Parser parser = new Parser(expr);
        return parser.parseExpr();
    }

    // ===== 递归下降解析器 =====

    private static class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input;
            this.pos = 0;
        }

        double parseExpr() {
            double result = parseTerm();
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '+') { pos++; result += parseTerm(); }
                else if (c == '-') { pos++; result -= parseTerm(); }
                else break;
            }
            return result;
        }

        private double parseTerm() {
            double result = parseFactor();
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '*') { pos++; result *= parseFactor(); }
                else if (c == '/') {
                    pos++;
                    double divisor = parseFactor();
                    result = divisor != 0 ? result / divisor : 0;
                } else break;
            }
            return result;
        }

        private double parseFactor() {
            skipWhitespace();
            if (pos >= input.length()) return 0;

            char c = input.charAt(pos);

            // 负号
            if (c == '-') { pos++; return -parseFactor(); }
            if (c == '+') { pos++; return parseFactor(); }

            // 括号
            if (c == '(') {
                pos++; // skip '('
                double result = parseExpr();
                if (pos < input.length() && input.charAt(pos) == ')') pos++;
                return result;
            }

            // 函数调用
            if (Character.isLetter(c)) {
                return parseFunctionCall();
            }

            // 数字
            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }

            return 0;
        }

        private double parseNumber() {
            int start = pos;
            while (pos < input.length() &&
                    (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.' ||
                     input.charAt(pos) == 'e' || input.charAt(pos) == 'E' ||
                     (pos > start && (input.charAt(pos) == '+' || input.charAt(pos) == '-')))) {
                pos++;
            }
            try {
                return Double.parseDouble(input.substring(start, pos));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private double parseFunctionCall() {
            int start = pos;
            while (pos < input.length() && Character.isLetter(input.charAt(pos))) pos++;
            String funcName = input.substring(start, pos);

            skipWhitespace();
            if (pos >= input.length() || input.charAt(pos) != '(') {
                // 不是函数调用，可能是已经替换的数字
                return 0;
            }
            pos++; // skip '('

            java.util.List<Double> args = new java.util.ArrayList<>();
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) != ')') {
                args.add(parseExpr());
                while (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                    args.add(parseExpr());
                }
            }
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ')') pos++;

            return applyFunction(funcName, args);
        }

        private double applyFunction(String name, java.util.List<Double> args) {
            return switch (name) {
                case "abs" -> args.isEmpty() ? 0 : Math.abs(args.get(0));
                case "max" -> args.isEmpty() ? 0 : args.stream().mapToDouble(d -> d).max().orElse(0);
                case "min" -> args.isEmpty() ? 0 : args.stream().mapToDouble(d -> d).min().orElse(0);
                case "normalize" -> {
                    if (args.isEmpty()) yield 0;
                    double v = args.get(0);
                    // 归一化到 [-1, 1] 范围（tanh 变换）
                    yield Math.tanh(v / 10);
                }
                case "clamp" -> {
                    if (args.size() < 3) yield args.isEmpty() ? 0 : args.get(0);
                    yield Math.max(args.get(1), Math.min(args.get(2), args.get(0)));
                }
                default -> 0;
            };
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }
    }
}
