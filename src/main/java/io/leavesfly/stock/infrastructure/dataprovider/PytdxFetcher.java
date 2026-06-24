package io.leavesfly.stock.infrastructure.dataprovider;

import io.leavesfly.stock.config.AppConfig;
import io.leavesfly.stock.domain.model.entity.StockDailyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.util.*;

/**
 * 通达信(TDX)数据源适配器(Java版)
 * 
 * 对应Python版本的 pytdx_fetcher.py
 * 原理: pytdx通过TCP连接通达信行情服务器获取数据
 * Java实现: 直接用Socket实现TDX二进制协议
 * 
 * TDX协议简介:
 * - 连接端口: 7709
 * - 协议: 自定义二进制(小端序)
 * - 命令: 0x0FC6(日线), 0x0547(实时行情)等
 * 
 * 备选方案: 当TDX服务器不可用时，降级到HTTP接口
 */
@Component
public class PytdxFetcher implements BaseDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(PytdxFetcher.class);
    
    /** 通达信免费行情服务器列表 */
    private static final String[][] TDX_SERVERS = {
            {"119.147.212.81", "7709"},
            {"114.80.63.12", "7709"},
            {"218.75.126.9", "7709"},
            {"115.238.56.198", "7709"},
            {"124.160.88.183", "7709"},
    };
    
    /** 备用HTTP接口(当TCP不可用时) */
    private static final String FALLBACK_URL = "https://q.stock.sohu.com/hisHq";

    private final AppConfig config;
    private final okhttp3.OkHttpClient httpClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public PytdxFetcher(AppConfig config) {
        this.config = config;
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        this.httpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @Override public String getName() { return "pytdx"; }
    @Override public int getPriority() { return 3; }
    @Override public boolean isAvailable() { return true; }

    @Override
    public List<StockDailyData> getHistoryData(String stockCode, LocalDate startDate, LocalDate endDate) {
        // 优先尝试TCP协议(速度更快)
        List<StockDailyData> result = getHistoryViaTcp(stockCode, startDate, endDate);
        if (!result.isEmpty()) return result;
        
        // TCP失败则降级到HTTP
        return getHistoryViaHttp(stockCode, startDate, endDate);
    }

    /**
     * 通过TDX TCP协议获取历史数据
     */
    private List<StockDailyData> getHistoryViaTcp(String stockCode, LocalDate startDate, LocalDate endDate) {
        for (String[] server : TDX_SERVERS) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(server[0], Integer.parseInt(server[1])), 3000);
                socket.setSoTimeout(5000);
                
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                // 发送握手包
                sendHandshake(out);
                readResponse(in);

                // 请求日K线数据
                int market = getMarketCode(stockCode);
                String code = stockCode.replaceAll("^(sh|sz|SH|SZ)", "");
                int count = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 10;
                count = Math.min(count, 800);

                byte[] request = buildKlineRequest(market, code, 0, count);
                out.write(request);
                out.flush();

                byte[] response = readResponse(in);
                if (response != null && response.length > 0) {
                    return parseKlineResponse(stockCode, response, startDate, endDate);
                }
            } catch (Exception e) {
                log.debug("TDX服务器 {}:{} 连接失败: {}", server[0], server[1], e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    /**
     * 通过HTTP降级获取
     */
    private List<StockDailyData> getHistoryViaHttp(String stockCode, LocalDate startDate, LocalDate endDate) {
        try {
            String code = stockCode.replaceAll("^(sh|sz|SH|SZ)", "");
            String prefix = code.startsWith("6") ? "cn_" : "cn_";
            String url = FALLBACK_URL + "?code=" + prefix + code +
                    "&start=" + startDate.toString().replace("-", "") +
                    "&end=" + endDate.toString().replace("-", "") +
                    "&stat=1&order=D&period=d";

            okhttp3.Request request = new okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
                String body = response.body().string();
                // 解析搜狐历史数据JSON
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                if (!root.isArray() || root.isEmpty()) return Collections.emptyList();
                
                com.fasterxml.jackson.databind.JsonNode hq = root.get(0).path("hq");
                if (!hq.isArray()) return Collections.emptyList();

                List<StockDailyData> result = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode row : hq) {
                    if (!row.isArray() || row.size() < 6) continue;
                    StockDailyData d = new StockDailyData();
                    d.setStockCode(stockCode);
                    d.setTradeDate(LocalDate.parse(row.get(0).asText()));
                    d.setOpenPrice(row.get(1).asDouble());
                    d.setClosePrice(row.get(2).asDouble());
                    d.setChangePct(Double.parseDouble(row.get(4).asText().replace("%", "")));
                    d.setVolume((long) (row.get(7).asDouble() * 100)); // 手->股
                    d.setDataSource("pytdx");
                    result.add(d);
                }
                Collections.reverse(result);
                return result;
            }
        } catch (Exception e) {
            log.error("pytdx HTTP降级获取失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getRealtimeQuote(String stockCode) {
        // 实时行情使用新浪接口
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> getStockInfo(String stockCode) { return Collections.emptyMap(); }

    // ========== TDX协议实现 ==========

    private void sendHandshake(OutputStream out) throws IOException {
        // TDX握手包(简化)
        byte[] handshake = new byte[]{0x0c, 0x02, 0x18, (byte) 0x93, 0x00, 0x01, 0x03, 0x00, 0x03, 0x00, 0x0d, 0x00, 0x01};
        out.write(handshake);
        out.flush();
    }

    private byte[] buildKlineRequest(int market, String code, int start, int count) {
        // 构造日线数据请求(简化版)
        ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0x0c);
        buf.putShort((short) 0x0124);
        buf.putShort((short) 0x0fc6); // 日线命令
        buf.putShort((short) market);
        // 填充代码(6字节)
        byte[] codeBytes = new byte[6];
        System.arraycopy(code.getBytes(), 0, codeBytes, 0, Math.min(6, code.length()));
        buf.put(codeBytes);
        buf.putShort((short) start);
        buf.putShort((short) count);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    private byte[] readResponse(InputStream in) throws IOException {
        byte[] header = new byte[16];
        int read = in.read(header);
        if (read < 16) return new byte[0];
        int bodyLen = ByteBuffer.wrap(header, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (bodyLen <= 0 || bodyLen > 1000000) return new byte[0];
        byte[] body = new byte[bodyLen];
        int totalRead = 0;
        while (totalRead < bodyLen) {
            int n = in.read(body, totalRead, bodyLen - totalRead);
            if (n < 0) break;
            totalRead += n;
        }
        return body;
    }

    private List<StockDailyData> parseKlineResponse(String stockCode, byte[] data, LocalDate start, LocalDate end) {
        // TDX K线数据每条32字节(简化解析)
        List<StockDailyData> result = new ArrayList<>();
        int offset = 4; // 跳过头部
        while (offset + 32 <= data.length) {
            try {
                ByteBuffer buf = ByteBuffer.wrap(data, offset, 32).order(ByteOrder.LITTLE_ENDIAN);
                int dateInt = buf.getInt();
                int year = dateInt / 10000;
                int month = (dateInt % 10000) / 100;
                int day = dateInt % 100;
                if (year < 2000 || year > 2030) { offset += 32; continue; }
                
                LocalDate date = LocalDate.of(year, month, day);
                if (date.isBefore(start) || date.isAfter(end)) { offset += 32; continue; }

                StockDailyData d = new StockDailyData();
                d.setStockCode(stockCode);
                d.setTradeDate(date);
                d.setOpenPrice(buf.getInt() / 100.0);
                d.setHighPrice(buf.getInt() / 100.0);
                d.setLowPrice(buf.getInt() / 100.0);
                d.setClosePrice(buf.getInt() / 100.0);
                d.setAmount((double) buf.getFloat());
                d.setVolume((long) buf.getInt());
                d.setDataSource("pytdx");
                result.add(d);
            } catch (Exception e) {
                // 解析单条失败，继续下一条
            }
            offset += 32;
        }
        return result;
    }

    private int getMarketCode(String stockCode) {
        String code = stockCode.replaceAll("^(sh|sz|SH|SZ)", "");
        if (code.startsWith("6") || code.startsWith("9")) return 1; // 沪市
        return 0; // 深市
    }
}
