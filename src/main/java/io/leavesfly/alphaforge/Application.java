package io.leavesfly.alphaforge;

import io.leavesfly.alphaforge.config.AppConfig;
import io.leavesfly.alphaforge.application.pipeline.StockAnalysisPipeline;
import io.leavesfly.alphaforge.application.service.market.MarketAnalysisService;
import io.leavesfly.alphaforge.presentation.scheduler.AnalysisScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 股票智能分析系统 - Java版主入口
 * 
 * 支持运行模式:
 * 1. 单次分析: --stocks 600519,hk00700,AAPL
 * 2. 定时调度: --schedule
 * 3. Web服务: --serve
 * 4. 大盘复盘: --market-review
 * 5. 回测模式: --backtest
 */
@SpringBootApplication
@EnableScheduling
public class Application implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private final AppConfig appConfig;
    private final StockAnalysisPipeline pipeline;
    private final AnalysisScheduler scheduler;
    private final MarketAnalysisService marketAnalysisService;

    public Application(AppConfig appConfig, StockAnalysisPipeline pipeline, AnalysisScheduler scheduler,
                        MarketAnalysisService marketAnalysisService) {
        this.appConfig = appConfig;
        this.pipeline = pipeline;
        this.scheduler = scheduler;
        this.marketAnalysisService = marketAnalysisService;
    }

    public static void main(String[] args) {
        // 在Spring启动前执行初始化: 创建目录、环境预检查、设置系统属性
        AppInitializer.init();
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 解析命令行参数
        CommandLineArgs parsedArgs = parseArguments(args);

        log.info("=== 股票智能分析系统启动 ===");
        log.info("运行模式: {}", parsedArgs.getMode());

        switch (parsedArgs.getMode()) {
            case SERVE:
                // Web服务模式由Spring Boot自动处理
                log.info("Web API服务已启动, 端口: {}", appConfig.getServerPort());
                log.info("访问地址: http://localhost:{}", appConfig.getServerPort());
                break;
            case SERVE_ONLY:
                log.info("纯API服务模式已启动, 端口: {}", appConfig.getServerPort());
                log.info("访问地址: http://localhost:{}", appConfig.getServerPort());
                break;
            case SCHEDULE:
                log.info("定时调度模式启动");
                scheduler.start();
                break;
            case MARKET_REVIEW:
                log.info("大盘复盘模式");
                var review = marketAnalysisService.marketReview();
                log.info("大盘复盘完成: 情绪={}", review.get("market_sentiment"));
                break;
            case BACKTEST:
                log.info("回测模式请通过 Web API 使用: POST /api/backtest/run");
                break;
            case SINGLE:
            default:
                log.info("单次分析模式, 股票列表: {}", parsedArgs.getStocks());
                pipeline.runFullAnalysis(parsedArgs.getStocks(), parsedArgs.isDryRun(), parsedArgs.isDebug());
                break;
        }
    }

    /**
     * 解析命令行参数
     */
    private CommandLineArgs parseArguments(String[] args) {
        CommandLineArgs parsed = new CommandLineArgs();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--serve":
                    parsed.setMode(RunMode.SERVE);
                    break;
                case "--serve-only":
                    parsed.setMode(RunMode.SERVE_ONLY);
                    break;
                case "--schedule":
                    parsed.setMode(RunMode.SCHEDULE);
                    break;
                case "--market-review":
                    parsed.setMode(RunMode.MARKET_REVIEW);
                    break;
                case "--backtest":
                    parsed.setMode(RunMode.BACKTEST);
                    break;
                case "--stocks":
                    if (i + 1 < args.length) {
                        parsed.setStocks(args[++i]);
                    }
                    break;
                case "--dry-run":
                    parsed.setDryRun(true);
                    break;
                case "--debug":
                    parsed.setDebug(true);
                    break;
                case "--backtest-days":
                    if (i + 1 < args.length) {
                        parsed.setBacktestDays(Integer.parseInt(args[++i]));
                    }
                    break;
            }
        }
        return parsed;
    }

    /**
     * 运行模式枚举
     */
    public enum RunMode {
        SINGLE,      // 单次分析
        SCHEDULE,    // 定时调度
        SERVE,       // Web服务(含分析)
        SERVE_ONLY,  // 纯Web服务
        MARKET_REVIEW, // 大盘复盘
        BACKTEST     // 回测
    }

    /**
     * 命令行参数封装
     */
    public static class CommandLineArgs {
        private RunMode mode = RunMode.SERVE;
        private String stocks;
        private boolean dryRun = false;
        private boolean debug = false;
        private int backtestDays = 30;

        public RunMode getMode() { return mode; }
        public void setMode(RunMode mode) { this.mode = mode; }
        public String getStocks() { return stocks; }
        public void setStocks(String stocks) { this.stocks = stocks; }
        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
        public boolean isDebug() { return debug; }
        public void setDebug(boolean debug) { this.debug = debug; }
        public int getBacktestDays() { return backtestDays; }
        public void setBacktestDays(int backtestDays) { this.backtestDays = backtestDays; }
    }
}
