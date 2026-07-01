package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Bot 机器人配置 — 飞书/钉钉/企微/Discord 等 Bot 凭据，独立 Spring Bean
 */
@Component
public class BotConfig {

    private final EnvVarProvider env;

    public BotConfig(EnvVarProvider env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        botEnabled = env.getBool("BOT_ENABLED", false);
        feishuAppId = env.get("FEISHU_APP_ID", "");
        feishuAppSecret = env.get("FEISHU_APP_SECRET", "");
        dingtalkAppKey = env.get("DINGTALK_APP_KEY", "");
        dingtalkAppSecret = env.get("DINGTALK_APP_SECRET", "");
        wecomCorpId = env.get("WECOM_CORP_ID", "");
        wecomAgentId = env.get("WECOM_AGENT_ID", "");
        wecomSecret = env.get("WECOM_SECRET", "");
        discordBotToken = env.get("DISCORD_BOT_TOKEN", "");
    }

    private boolean botEnabled = false;
    private String feishuAppId = "";
    private String feishuAppSecret = "";
    private String dingtalkAppKey = "";
    private String dingtalkAppSecret = "";
    private String wecomCorpId = "";
    private String wecomAgentId = "";
    private String wecomSecret = "";
    private String discordBotToken = "";

    public boolean isBotEnabled() { return botEnabled; }
    public String getFeishuAppId() { return feishuAppId; }
    public String getFeishuAppSecret() { return feishuAppSecret; }
    public String getDingtalkAppKey() { return dingtalkAppKey; }
    public String getDingtalkAppSecret() { return dingtalkAppSecret; }
    public String getWecomCorpId() { return wecomCorpId; }
    public String getWecomAgentId() { return wecomAgentId; }
    public String getWecomSecret() { return wecomSecret; }
    public String getDiscordBotToken() { return discordBotToken; }
}
