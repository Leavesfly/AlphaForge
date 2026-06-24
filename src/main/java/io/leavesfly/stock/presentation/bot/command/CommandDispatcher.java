package io.leavesfly.stock.presentation.bot.command;

import io.leavesfly.stock.presentation.bot.model.BotMessage;
import io.leavesfly.stock.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.stock.infrastructure.llm.LlmService;
import io.leavesfly.stock.application.service.AnalysisHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot命令分发器
 * 
 * 对应Python版本的 bot/dispatcher.py
 * 支持命令注册、别名路由、频率限制
 */
@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);
    
    private final StockAnalysisPipeline pipeline;
    private final LlmService llmService;
    private final AnalysisHistoryService historyService;
    
    /** 命令注册表 */
    private final Map<String, CommandHandler> commands = new LinkedHashMap<>();
    
    /** 命令别名 */
    private final Map<String, String> aliases = new HashMap<>();
    
    /** 频率限制: userId -> 上次命令时间 */
    private final Map<String, Long> rateLimiter = new ConcurrentHashMap<>();
    
    /** 频率限制间隔(毫秒) */
    private static final long RATE_LIMIT_MS = 3000;

    public CommandDispatcher(StockAnalysisPipeline pipeline, LlmService llmService, 
                           AnalysisHistoryService historyService) {
        this.pipeline = pipeline;
        this.llmService = llmService;
        this.historyService = historyService;
        registerCommands();
    }

    /**
     * 分发命令
     */
    public String dispatch(BotMessage message) {
        if (!message.isCommand()) {
            // 非命令消息，交给AI对话处理
            return handleChat(message);
        }

        String commandName = message.getCommandName();
        
        // 检查别名
        commandName = aliases.getOrDefault(commandName, commandName);
        
        // 频率限制
        if (isRateLimited(message.getSenderId())) {
            return "⚠️ 操作太频繁，请稍后再试";
        }

        // 查找命令处理器
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

    /**
     * 注册所有命令
     */
    private void registerCommands() {
        // /help - 帮助
        commands.put("help", msg -> buildHelpText());
        
        // /analyze - 分析股票
        commands.put("analyze", msg -> {
            String args = msg.getCommandArgs();
            if (args.isEmpty()) return "用法: /analyze <股票代码>\n示例: /analyze 600519";
            var report = pipeline.analyzeSingleStock(args.trim(), false, false);
            if (report != null && report.getFullReport() != null) {
                return report.getFullReport();
            }
            return "分析完成，但未获取到有效结果";
        });
        
        // /ask - 问答
        commands.put("ask", msg -> {
            String question = msg.getCommandArgs();
            if (question.isEmpty()) return "用法: /ask <问题>";
            return llmService.chat("你是一个专业的股票分析助手。", question);
        });
        
        // /history - 查看历史
        commands.put("history", msg -> {
            var reports = historyService.getRecentReports(null, 5);
            if (reports.isEmpty()) return "暂无分析历史";
            StringBuilder sb = new StringBuilder("📋 最近分析记录:\n\n");
            for (var r : reports) {
                sb.append(String.format("• %s(%s) - %s - %s\n", 
                        r.getStockName(), r.getStockCode(), r.getSignal(), r.getAnalysisDate()));
            }
            return sb.toString();
        });
        
        // /status - 系统状态
        commands.put("status", msg -> {
            long total = historyService.getTotalAnalysisCount();
            return String.format("📊 系统状态\n• 已分析次数: %d\n• 运行状态: 正常\n• LLM模型: %s", 
                    total, "已配置");
        });
        
        // /market - 大盘行情
        commands.put("market", msg -> {
            pipeline.runMarketReview();
            return "📈 大盘复盘任务已执行";
        });

        // 注册别名
        aliases.put("a", "analyze");
        aliases.put("分析", "analyze");
        aliases.put("h", "help");
        aliases.put("帮助", "help");
        aliases.put("问", "ask");
        aliases.put("历史", "history");
        aliases.put("大盘", "market");
    }

    /**
     * 非命令消息的AI对话处理
     */
    private String handleChat(BotMessage message) {
        return llmService.chat(
                "你是一个友好的股票分析助手。用户可能会问你各种投资相关的问题。",
                message.getContent());
    }

    /**
     * 检查频率限制
     */
    private boolean isRateLimited(String userId) {
        if (userId == null) return false;
        Long lastTime = rateLimiter.get(userId);
        long now = System.currentTimeMillis();
        if (lastTime != null && (now - lastTime) < RATE_LIMIT_MS) {
            return true;
        }
        rateLimiter.put(userId, now);
        return false;
    }

    /**
     * 构建帮助文本
     */
    private String buildHelpText() {
        return """
                📖 可用命令列表:
                
                /analyze <代码> - 分析指定股票
                /ask <问题>    - AI问答
                /history       - 查看分析历史
                /market        - 大盘复盘
                /status        - 系统状态
                /help          - 显示此帮助
                
                别名: /a=analyze, /问=ask, /大盘=market
                """;
    }

    /**
     * 命令处理器函数式接口
     */
    @FunctionalInterface
    public interface CommandHandler {
        String handle(BotMessage message);
    }
}
