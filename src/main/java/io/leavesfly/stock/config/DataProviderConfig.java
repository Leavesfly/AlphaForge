package io.leavesfly.stock.config;

/**
 * 数据源相关配置
 */
public class DataProviderConfig {

    private String dataProvider = "auto";
    private String tushareToken = "";
    private String longbridgeAppKey = "";
    private String longbridgeAppSecret = "";
    private String longbridgeAccessToken = "";
    private String alphavantageApiKey = "";
    private String finnhubApiKey = "";
    private String tickflowApiKey = "";
    private String tickflowBaseUrl = "https://api.tickflow.io";

    // Getters & Setters
    public String getDataProvider() { return dataProvider; }
    public void setDataProvider(String dataProvider) { this.dataProvider = dataProvider; }
    public String getTushareToken() { return tushareToken; }
    public void setTushareToken(String tushareToken) { this.tushareToken = tushareToken; }
    public String getLongbridgeAppKey() { return longbridgeAppKey; }
    public void setLongbridgeAppKey(String longbridgeAppKey) { this.longbridgeAppKey = longbridgeAppKey; }
    public String getLongbridgeAppSecret() { return longbridgeAppSecret; }
    public void setLongbridgeAppSecret(String longbridgeAppSecret) { this.longbridgeAppSecret = longbridgeAppSecret; }
    public String getLongbridgeAccessToken() { return longbridgeAccessToken; }
    public void setLongbridgeAccessToken(String longbridgeAccessToken) { this.longbridgeAccessToken = longbridgeAccessToken; }
    public String getAlphavantageApiKey() { return alphavantageApiKey; }
    public void setAlphavantageApiKey(String alphavantageApiKey) { this.alphavantageApiKey = alphavantageApiKey; }
    public String getFinnhubApiKey() { return finnhubApiKey; }
    public void setFinnhubApiKey(String finnhubApiKey) { this.finnhubApiKey = finnhubApiKey; }
    public String getTickflowApiKey() { return tickflowApiKey; }
    public void setTickflowApiKey(String tickflowApiKey) { this.tickflowApiKey = tickflowApiKey; }
    public String getTickflowBaseUrl() { return tickflowBaseUrl; }
    public void setTickflowBaseUrl(String tickflowBaseUrl) { this.tickflowBaseUrl = tickflowBaseUrl; }
}
