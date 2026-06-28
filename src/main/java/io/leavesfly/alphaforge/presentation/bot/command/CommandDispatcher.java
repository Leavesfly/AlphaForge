package io.leavesfly.alphaforge.presentation.bot.command;

import io.leavesfly.alphaforge.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.alphaforge.application.service.report.AnalysisHistoryService;
import io.leavesfly.alphaforge.application.service.market.MarketLightService;
import io.leavesfly.alphaforge.domain.service.NameToCodeResolver;
import io.leavesfly.alphaforge.application.strategy.StrategyCatalog;
import io.leavesfly.alphaforge.application.strategy.model.StrategyDefinition;
import io.leavesfly.alphaforge.domain.model.entity.analysis.AnalysisReport;
import io.leavesfly.alphaforge.application.service.chat.ChatService;
import io.leavesfly.alphaforge.presentation.bot.model.BotMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot 命令分发器 — 统一命令注册、别名路由、频率限制。
 */
@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final StockAnalysisPipeline pipeline;
    private final ChatService chatService;
    private final AnalysisHistoryService historyService;
    private final NameToCodeResolver nameResolver;
    private final MarketLightService marketLightService;
    private final StrategyCatalog strategyCatalog;

    private final Map<String, CommandHandler> commands = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    private final Map<String, Long> rateLimiter = new ConcurrentHashMap<>();

    private static final long RATE_LIMIT_MS = 3000;

    public CommandDispatcher(StockAnalysisPipeline pipeline, ChatService chatService,
                             AnalysisHistoryService historyService, NameToCodeResolver nameResolver,
                             MarketLightService marketLightService, StrategyCatalog strategyCatalog) {
        this.pipeline = pipeline;
        this.chatService = chatService;
        this.historyService = historyService;
        this.nameResolver = nameResolver;
        this.marketLightService = marketLightService;
        this.strategyCatalog = strategyCatalog;
        registerCommands();
    }

    public String dispatch(BotMessage message) {
        if (!message.isCommand()) {
            return handleChat(message);
        }
        String commandName = aliases.getOrDefault(message.getCommandName(), message.getCommandName());
        if (isRateLimited(message.getSenderId())) {
            return "⚠️ 操作太频繁，请稍后再试";
        }
        CommandHandler handler = commands.get(commandName);
        if (handler == null) {
            return String.format("❌ 未知命令: /%s\n输入 /help 查看可用命令", commandName);
        }
        try {
            return handler.handle(message);
        } catch (Exception e) {
            log.error("命令执行失败: {} - {}", commandName, e.getMessage());
            return "❌ 命令执行失败: " + e.getMessage();
        }
    }

    private void registerCommands() {
        commands.put("help", msg -> buildHelpText());

        commands.put("analyze", msg -> {
            String args = msg.getCommandArgs();
            if (args.isEmpty()) {
                return "用法: /analyze <股票代码或名称>\n示例: /analyze 600519";
            }
            String code = nameResolver.resolve(args.trim());
            AnalysisReport report = pipeline.analyzeSingleStock(code, false, false);
            if (report == null) {
                return "❌ 分析失败: " + args;
            }
            return String.format("%s %s(%s)\n评分: %d/100\n信号: %s\n%s",
                    signalEmoji(report.getSignal()), report.getStockName(), report.getStockCode(),
                    report.getTotalScore() != null ? report.getTotalScore() : 0,
                    report.getSignal(),
                    report.getSummary() != null ? report.getSummary() : "");
        });

        commands.put("ask", msg -> {
            String question = msg.getCommandArgs();
            if (question.isEmpty()) {
                return "用法: /ask <问题>";
            }
            return chatService.chat("你是一个专业的股票分析助手。", question);
        });

        commands.put("history", msg -> {
            String code = msg.getCommandArgs().isBlank() ? null : nameResolver.resolve(msg.getCommandArgs().trim());
            var reports = historyService.getRecentReports(code, 5);
            if (reports.isEmpty()) {
                return "暂无分析历史";
            }
            StringBuilder sb = new StringBuilder("📋 最近分析记录:\n\n");
            for (var r : reports) {
                sb.append(String.format("• %s(%s) - %s - %s\n",
                        r.getStockName(), r.getStockCode(), r.getSignal(), r.getAnalysisDate()));
            }
            return sb.toString();
        });

        commands.put("status", msg -> {
            long total = historyService.getTotalAnalysisCount();
            return String.format("📊 系统状态\n• 已分析次数: %d\n• 策略数量: %d\n• 运行状态: 正常",
                    total, strategyCatalog.listAll().size());
        });

        commands.put("market", msg -> {
            Map<String, Object> light = marketLightService.getMarketLight();
            return String.format("🏛️ 市场信号灯: %s\n%s",
                    colorEmoji((String) light.get("color")), light.get("reason"));
        });

        commands.put("batch", msg -> {
            String args = msg.getCommandArgs();
            if (args.isBlank()) {
                return "用法: /batch <代码1,代码2,...>";
            }
            List<String> codes = nameResolver.resolveBatch(args);
            pipeline.runFullAnalysis(String.join(",", codes), false, false);
            return "✅ 已提交批量分析: " + codes.size() + " 只股票";
        });

        commands.put("strategies", msg -> {
            StringBuilder sb = new StringBuilder("📊 可用策略:\n");
            for (StrategyDefinition strategy : strategyCatalog.listAll()) {
                sb.append(String.format("  %s (%s) [%s] %s\n",
                        strategy.getLabel(), strategy.getId(),
                        String.join(",", strategy.getCapabilities()),
                        strategy.isAvailable() ? "✓" : "✗"));
            }
            return sb.toString().trim();
        });

        aliases.put("a", "analyze");
        aliases.put("分析", "analyze");
        aliases.put("h", "help");
        aliases.put("帮助", "help");
        aliases.put("问", "ask");
        aliases.put("历史", "history");
        aliases.put("大盘", "market");
        aliases.put("b", "batch");
    }

    private String handleChat(BotMessage message) {
        return chatService.chat(
                "你是一个友好的股票分析助手。用户可能会问你各种投资相关的问题。",
                message.getContent());
    }

    private boolean isRateLimited(String userId) {
        if (userId == null) {
            return false;
        }
        Long lastTime = rateLimiter.get(userId);
        long now = System.currentTimeMillis();
        if (lastTime != null && (now - lastTime) < RATE_LIMIT_MS) {
            return true;
        }
        rateLimiter.put(userId, now);
        return false;
    }

    private String buildHelpText() {
        return """
                📖 可用命令列表:

                /analyze <代码> - 分析指定股票（支持名称）
                /ask <问题>    - AI 问答
                /history       - 查看分析历史
                /market        - 大盘概览
                /batch <列表>  - 批量分析
                /strategies    - 策略列表
                /status        - 系统状态
                /help          - 显示此帮助

                别名: /a=analyze, /问=ask, /大盘=market
                """;
    }

    private String signalEmoji(String s) {
        if (s == null) {
            return "⚖️";
        }
        return switch (s) {
            case "strong_buy" -> "🔥";
            case "buy" -> "📈";
            case "sell" -> "📉";
            default -> "⚖️";
        };
    }

    private String colorEmoji(String c) {
        if (c == null) {
            return "⚪";
        }
        return switch (c) {
            case "red" -> "🔴";
            case "yellow" -> "🟡";
            case "green" -> "🟢";
            default -> "⚪";
        };
    }

    @FunctionalInterface
    public interface CommandHandler {
        String handle(BotMessage message);
    }
}
