package io.leavesfly.stock.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用配置管理器 - 门面模式
 * 
 * 从.env文件和环境变量加载配置。
 * 内部委托给职责分离的子配置类（LlmConfig、DataProviderConfig、NotificationConfig、SchedulerAuthConfig）。
 * 对外保持原有 getter 接口不变，确保向后兼容。
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private Dotenv dotenv;

    // ========== 子配置组合 ==========
    private final LlmConfig llmConfig = new LlmConfig();
    private final DataProviderConfig dataProviderConfig = new DataProviderConfig();
    private final NotificationConfig notificationConfig = new NotificationConfig();
    private final SchedulerAuthConfig schedulerAuthConfig = new SchedulerAuthConfig();

    // ========== 服务配置 ==========
    private int serverPort = 8000;
    private String serverHost = "0.0.0.0";

    // ========== LLM配置 ==========
    private String llmApi;
    private String llmModel;
    private String llmApiKey;
    private double llmTemperature = 0.7;
    private int llmMaxTokens = 8000;
    private int llmTimeout = 120;

    // ========== 多LLM渠道 ==========
    private List<LlmChannelConfig> llmChannels = new ArrayList<>();

    // ========== 股票配置 ==========
    private List<String> stockList = new ArrayList<>();
    private String market = "A";  // A/HK/US/JP/KR
    private int historyDays = 60;

    // ========== 数据源配置 ==========
    private String dataProvider = "auto";
    private String tushareToken;
    private String longbridgeAppKey;
    private String longbridgeAppSecret;
    private String longbridgeAccessToken;
    private String alphavantageApiKey;
    private String finnhubApiKey;
    private String tickflowApiKey;
    private String tickflowBaseUrl = "https://api.tickflow.io";

    // ========== 通知配置 ==========
    private String notificationChannels = "";
    private String wecomWebhook;
    private String feishuWebhook;
    private String telegramBotToken;
    private String telegramChatId;
    private String emailSmtpHost;
    private int emailSmtpPort = 465;
    private String emailUser;
    private String emailPassword;
    private String emailTo;
    private String discordWebhook;
    private String slackWebhook;
    private String pushoverUserKey;
    private String pushoverAppToken;
    private String ntfyTopic;
    private String ntfyServer;
    private String gotifyUrl;
    private String gotifyToken;
    private String pushplusToken;
    private String serverchan3Key;
    private String customWebhookUrl;
    private String customWebhookMethod = "POST";
    private Map<String, String> customWebhookHeaders = new HashMap<>();
    private String astrbotWebhook;

    // ========== Bot配置 ==========
    private boolean botEnabled = false;
    private String feishuAppId;
    private String feishuAppSecret;
    private String dingtalkAppKey;
    private String dingtalkAppSecret;
    private String wecomCorpId;
    private String wecomAgentId;
    private String wecomSecret;
    private String discordBotToken;

    // ========== 搜索配置 ==========
    private String searchProvider = "tavily";
    private String tavilyApiKey;
    private String anspireApiKey;
    private int newsMaxResults = 5;

    // ========== 认证配置 ==========
    private String authSecret;
    private String authPassword;
    private boolean authEnabled = false;

    // ========== 调度配置 ==========
    private String scheduleCron = "0 0 18 * * MON-FRI";
    private String timezone = "Asia/Shanghai";

    // ========== Agent配置 ==========
    private String agentMode = "standard"; // quick/standard/full/specialist
    private int agentMaxIterations = 10;

    @PostConstruct
    public void init() {
        try {
            dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
        } catch (Exception e) {
            log.warn("未找到.env文件，使用系统环境变量");
            dotenv = null;
        }
        loadConfig();
        validateConfig();
        log.info("配置加载完成: market={}, stocks={}, provider={}", market, stockList.size(), dataProvider);
    }

    /**
     * 校验必填配置项，缺失时打印警告信息
     */
    private void validateConfig() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 必填项校验 - 缺失将导致核心功能不可用
        if (llmApiKey == null || llmApiKey.isEmpty()) {
            errors.add("LLM_API_KEY 未配置 - AI分析功能将不可用，请配置阿里云百炼API Key");
        }
        if (stockList.isEmpty()) {
            warnings.add("STOCK_LIST 未配置 - Web模式下可通过API传入，其他模式需要配置股票列表");
        }

        // 警告项 - 不影响启动但可能影响部分功能
        if (notificationChannels != null && !notificationChannels.isEmpty()) {
            // 检查配置的通知渠道是否有对应的凭据
            for (String channel : notificationChannels.split(",")) {
                String ch = channel.trim().toLowerCase();
                switch (ch) {
                    case "wecom":
                        if (wecomWebhook == null || wecomWebhook.isEmpty())
                            warnings.add("通知渠道 wecom 已启用但 WECOM_WEBHOOK 未配置");
                        break;
                    case "feishu":
                        if (feishuWebhook == null || feishuWebhook.isEmpty())
                            warnings.add("通知渠道 feishu 已启用但 FEISHU_WEBHOOK 未配置");
                        break;
                    case "telegram":
                        if (telegramBotToken == null || telegramBotToken.isEmpty())
                            warnings.add("通知渠道 telegram 已启用但 TELEGRAM_BOT_TOKEN 未配置");
                        break;
                    case "email":
                        if (emailSmtpHost == null || emailSmtpHost.isEmpty())
                            warnings.add("通知渠道 email 已启用但 EMAIL_SMTP_HOST 未配置");
                        break;
                }
            }
        }

        // 打印校验结果
        if (!errors.isEmpty()) {
            log.error("========================================");
            log.error("配置校验失败 - 以下必填配置缺失:");
            for (String error : errors) {
                log.error("  ✗ {}", error);
            }
            log.error("请参考 .env.example 文件配置必填项");
            log.error("========================================");
        }
        if (!warnings.isEmpty()) {
            log.warn("----------------------------------------");
            log.warn("配置提醒 - 以下配置可能影响部分功能:");
            for (String warning : warnings) {
                log.warn("  ⚠ {}", warning);
            }
            log.warn("----------------------------------------");
        }
    }

    /**
     * 加载所有配置项
     */
    private void loadConfig() {
        // 服务配置
        serverPort = getIntEnv("SERVER_PORT", 8000);
        serverHost = getEnv("SERVER_HOST", "0.0.0.0");

        // LLM配置
        llmApi = getEnv("LLM_API", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        llmModel = getEnv("LLM_MODEL", "qwen-plus");
        llmApiKey = getEnv("LLM_API_KEY", "");
        llmTemperature = getDoubleEnv("LLM_TEMPERATURE", 0.7);
        llmMaxTokens = getIntEnv("LLM_MAX_TOKENS", 8000);
        llmTimeout = getIntEnv("LLM_TIMEOUT", 120);

        // 解析多渠道LLM配置
        parseLlmChannels();

        // 股票配置
        String stockStr = getEnv("STOCK_LIST", "");
        if (!stockStr.isEmpty()) {
            stockList = Arrays.stream(stockStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        market = getEnv("MARKET", "A");
        historyDays = getIntEnv("HISTORY_DAYS", 60);

        // 数据源配置
        dataProvider = getEnv("DATA_PROVIDER", "auto");
        tushareToken = getEnv("TUSHARE_TOKEN", "");
        longbridgeAppKey = getEnv("LONGBRIDGE_APP_KEY", "");
        longbridgeAppSecret = getEnv("LONGBRIDGE_APP_SECRET", "");
        longbridgeAccessToken = getEnv("LONGBRIDGE_ACCESS_TOKEN", "");
        alphavantageApiKey = getEnv("ALPHAVANTAGE_API_KEY", "");
        finnhubApiKey = getEnv("FINNHUB_API_KEY", "");
        tickflowApiKey = getEnv("TICKFLOW_API_KEY", "");
        tickflowBaseUrl = getEnv("TICKFLOW_BASE_URL", "https://api.tickflow.io");

        // 通知配置
        notificationChannels = getEnv("NOTIFICATION_CHANNELS", "");
        wecomWebhook = getEnv("WECOM_WEBHOOK", "");
        feishuWebhook = getEnv("FEISHU_WEBHOOK", "");
        telegramBotToken = getEnv("TELEGRAM_BOT_TOKEN", "");
        telegramChatId = getEnv("TELEGRAM_CHAT_ID", "");
        emailSmtpHost = getEnv("EMAIL_SMTP_HOST", "");
        emailSmtpPort = getIntEnv("EMAIL_SMTP_PORT", 465);
        emailUser = getEnv("EMAIL_USER", "");
        emailPassword = getEnv("EMAIL_PASSWORD", "");
        emailTo = getEnv("EMAIL_TO", "");
        discordWebhook = getEnv("DISCORD_WEBHOOK", "");
        slackWebhook = getEnv("SLACK_WEBHOOK", "");
        pushoverUserKey = getEnv("PUSHOVER_USER_KEY", "");
        pushoverAppToken = getEnv("PUSHOVER_APP_TOKEN", "");
        ntfyTopic = getEnv("NTFY_TOPIC", "");
        ntfyServer = getEnv("NTFY_SERVER", "https://ntfy.sh");
        gotifyUrl = getEnv("GOTIFY_URL", "");
        gotifyToken = getEnv("GOTIFY_TOKEN", "");
        pushplusToken = getEnv("PUSHPLUS_TOKEN", "");
        serverchan3Key = getEnv("SERVERCHAN3_KEY", "");
        customWebhookUrl = getEnv("CUSTOM_WEBHOOK_URL", "");
        customWebhookMethod = getEnv("CUSTOM_WEBHOOK_METHOD", "POST");
        astrbotWebhook = getEnv("ASTRBOT_WEBHOOK", "");

        // Bot配置
        botEnabled = getBoolEnv("BOT_ENABLED", false);
        feishuAppId = getEnv("FEISHU_APP_ID", "");
        feishuAppSecret = getEnv("FEISHU_APP_SECRET", "");
        dingtalkAppKey = getEnv("DINGTALK_APP_KEY", "");
        dingtalkAppSecret = getEnv("DINGTALK_APP_SECRET", "");
        wecomCorpId = getEnv("WECOM_CORP_ID", "");
        wecomAgentId = getEnv("WECOM_AGENT_ID", "");
        wecomSecret = getEnv("WECOM_SECRET", "");
        discordBotToken = getEnv("DISCORD_BOT_TOKEN", "");

        // 搜索配置
        searchProvider = getEnv("SEARCH_PROVIDER", "tavily");
        tavilyApiKey = getEnv("TAVILY_API_KEY", "");
        anspireApiKey = getEnv("ANSPIRE_API_KEY", "");
        newsMaxResults = getIntEnv("NEWS_MAX_RESULTS", 5);

        // 认证配置
        authSecret = getEnv("AUTH_SECRET", UUID.randomUUID().toString());
        authPassword = getEnv("AUTH_PASSWORD", "");
        authEnabled = getBoolEnv("AUTH_ENABLED", false);

        // 调度配置
        scheduleCron = getEnv("SCHEDULE_CRON", "0 0 18 * * MON-FRI");
        timezone = getEnv("TIMEZONE", "Asia/Shanghai");

        // Agent配置
        agentMode = getEnv("AGENT_MODE", "standard");
        agentMaxIterations = getIntEnv("AGENT_MAX_ITERATIONS", 10);
    }

    /**
     * 解析多渠道LLM配置协议
     * 格式: provider://api_key@endpoint/model
     */
    private void parseLlmChannels() {
        llmChannels.clear();
        String channelsStr = getEnv("LLM_CHANNELS", "");
        if (channelsStr.isEmpty() && !llmApi.isEmpty()) {
            // 单渠道模式
            LlmChannelConfig channel = new LlmChannelConfig();
            channel.setApi(llmApi);
            channel.setModel(llmModel);
            channel.setApiKey(llmApiKey);
            channel.setTemperature(llmTemperature);
            channel.setMaxTokens(llmMaxTokens);
            channel.setTimeout(llmTimeout);
            llmChannels.add(channel);
        } else if (!channelsStr.isEmpty()) {
            // 多渠道模式: 按逗号分隔
            for (String channelUri : channelsStr.split(",")) {
                LlmChannelConfig channel = parseLlmUri(channelUri.trim());
                if (channel != null) {
                    llmChannels.add(channel);
                }
            }
        }
    }

    /**
     * 解析单个LLM渠道URI
     * 格式: provider://api_key@endpoint/model
     */
    private LlmChannelConfig parseLlmUri(String uri) {
        try {
            LlmChannelConfig config = new LlmChannelConfig();
            // 简单格式: model_name 或 完整URI
            if (!uri.contains("://")) {
                config.setModel(uri);
                config.setApi(llmApi);
                config.setApiKey(llmApiKey);
                config.setTemperature(llmTemperature);
                config.setMaxTokens(llmMaxTokens);
                config.setTimeout(llmTimeout);
                return config;
            }
            // URI格式解析
            int schemeEnd = uri.indexOf("://");
            String provider = uri.substring(0, schemeEnd);
            String rest = uri.substring(schemeEnd + 3);
            
            String apiKey = "";
            String endpoint = "";
            String model = "";
            
            if (rest.contains("@")) {
                apiKey = rest.substring(0, rest.indexOf("@"));
                rest = rest.substring(rest.indexOf("@") + 1);
            }
            if (rest.contains("/")) {
                int slashIdx = rest.lastIndexOf("/");
                endpoint = rest.substring(0, slashIdx);
                model = rest.substring(slashIdx + 1);
            } else {
                model = rest;
            }
            
            config.setProvider(provider);
            config.setApi(endpoint.isEmpty() ? llmApi : "https://" + endpoint);
            config.setModel(model);
            config.setApiKey(apiKey.isEmpty() ? llmApiKey : apiKey);
            config.setTemperature(llmTemperature);
            config.setMaxTokens(llmMaxTokens);
            config.setTimeout(llmTimeout);
            return config;
        } catch (Exception e) {
            log.error("解析LLM渠道URI失败: {}", uri, e);
            return null;
        }
    }

    // ========== 环境变量读取辅助方法 ==========

    public String getEnv(String key, String defaultValue) {
        // 优先系统环境变量，其次.env文件
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) return value;
        if (dotenv != null) {
            value = dotenv.get(key);
            if (value != null && !value.isEmpty()) return value;
        }
        return defaultValue;
    }

    public int getIntEnv(String key, int defaultValue) {
        String value = getEnv(key, "");
        if (value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDoubleEnv(String key, double defaultValue) {
        String value = getEnv(key, "");
        if (value.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolEnv(String key, boolean defaultValue) {
        String value = getEnv(key, "");
        if (value.isEmpty()) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    // ========== Getter方法 ==========
    public int getServerPort() { return serverPort; }
    public String getServerHost() { return serverHost; }
    public String getLlmApi() { return llmApi; }
    public String getLlmModel() { return llmModel; }
    public String getLlmApiKey() { return llmApiKey; }
    public double getLlmTemperature() { return llmTemperature; }
    public int getLlmMaxTokens() { return llmMaxTokens; }
    public int getLlmTimeout() { return llmTimeout; }
    public List<LlmChannelConfig> getLlmChannels() { return llmChannels; }
    public List<String> getStockList() { return stockList; }
    public String getMarket() { return market; }
    public int getHistoryDays() { return historyDays; }
    public String getDataProvider() { return dataProvider; }
    public String getTushareToken() { return tushareToken; }
    public String getLongbridgeAppKey() { return longbridgeAppKey; }
    public String getLongbridgeAppSecret() { return longbridgeAppSecret; }
    public String getLongbridgeAccessToken() { return longbridgeAccessToken; }
    public String getAlphavantageApiKey() { return alphavantageApiKey; }
    public String getFinnhubApiKey() { return finnhubApiKey; }
    public String getTickflowApiKey() { return tickflowApiKey; }
    public String getTickflowBaseUrl() { return tickflowBaseUrl; }
    public String getNotificationChannels() { return notificationChannels; }
    public String getWecomWebhook() { return wecomWebhook; }
    public String getFeishuWebhook() { return feishuWebhook; }
    public String getTelegramBotToken() { return telegramBotToken; }
    public String getTelegramChatId() { return telegramChatId; }
    public String getDiscordWebhook() { return discordWebhook; }
    public String getSlackWebhook() { return slackWebhook; }
    public String getPushoverUserKey() { return pushoverUserKey; }
    public String getPushoverAppToken() { return pushoverAppToken; }
    public String getNtfyTopic() { return ntfyTopic; }
    public String getNtfyServer() { return ntfyServer; }
    public String getGotifyUrl() { return gotifyUrl; }
    public String getGotifyToken() { return gotifyToken; }
    public String getPushplusToken() { return pushplusToken; }
    public String getServerchan3Key() { return serverchan3Key; }
    public String getCustomWebhookUrl() { return customWebhookUrl; }
    public String getCustomWebhookMethod() { return customWebhookMethod; }
    public String getAstrbotWebhook() { return astrbotWebhook; }
    public boolean isBotEnabled() { return botEnabled; }
    public String getFeishuAppId() { return feishuAppId; }
    public String getFeishuAppSecret() { return feishuAppSecret; }
    public String getDingtalkAppKey() { return dingtalkAppKey; }
    public String getDingtalkAppSecret() { return dingtalkAppSecret; }
    public String getWecomCorpId() { return wecomCorpId; }
    public String getWecomAgentId() { return wecomAgentId; }
    public String getWecomSecret() { return wecomSecret; }
    public String getDiscordBotToken() { return discordBotToken; }
    public String getSearchProvider() { return searchProvider; }
    public String getTavilyApiKey() { return tavilyApiKey; }
    public String getAnspireApiKey() { return anspireApiKey; }
    public int getNewsMaxResults() { return newsMaxResults; }
    public String getAuthSecret() { return authSecret; }
    public String getAuthPassword() { return authPassword; }
    public boolean isAuthEnabled() { return authEnabled; }
    public String getScheduleCron() { return scheduleCron; }
    public String getTimezone() { return timezone; }
    public String getAgentMode() { return agentMode; }
    public int getAgentMaxIterations() { return agentMaxIterations; }
    public String getEmailSmtpHost() { return emailSmtpHost; }
    public int getEmailSmtpPort() { return emailSmtpPort; }
    public String getEmailUser() { return emailUser; }
    public String getEmailPassword() { return emailPassword; }
    public String getEmailTo() { return emailTo; }

    /**
     * LLM渠道配置
     */
    public static class LlmChannelConfig {
        private String provider;
        private String api;
        private String model;
        private String apiKey;
        private double temperature = 0.7;
        private int maxTokens = 8000;
        private int timeout = 120;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApi() { return api; }
        public void setApi(String api) { this.api = api; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
    }
}
