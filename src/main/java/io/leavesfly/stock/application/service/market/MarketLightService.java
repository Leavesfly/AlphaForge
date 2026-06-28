package io.leavesfly.stock.application.service.market;

import io.leavesfly.stock.domain.service.port.MarketDataPort;
import io.leavesfly.stock.domain.model.entity.market.StockDailyData;
import io.leavesfly.stock.domain.service.port.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 市场信号灯服务 + 告警
 * 红灯=高风险, 黄灯=谨慎, 绿灯=可操作
 */
@Service
public class MarketLightService {

    private static final Logger log = LoggerFactory.getLogger(MarketLightService.class);
    private final MarketDataPort dataFetcher;
    private final NotificationPort notificationService;

    public MarketLightService(MarketDataPort dataFetcher, NotificationPort notificationService) {
        this.dataFetcher = dataFetcher;
        this.notificationService = notificationService;
    }

    /** 获取当前市场信号灯状态 */
    public Map<String, Object> getMarketLight() {
        Map<String, Object> light = new LinkedHashMap<>();
        List<StockDailyData> shIndex = dataFetcher.getHistoryData("000001", LocalDate.now().minusDays(20), LocalDate.now());
        if (shIndex.isEmpty()) {
            light.put("color", "gray"); light.put("reason", "数据不可用"); return light;
        }
        StockDailyData latest = shIndex.get(shIndex.size() - 1);
        double changePct = latest.getChangePct() != null ? latest.getChangePct() : 0;

        // MA5 vs MA20 判断
        double ma5 = avgClose(shIndex, 5), ma20 = avgClose(shIndex, 20);
        String color;
        String reason;
        if (changePct < -3 || (ma5 < ma20 && changePct < -1)) {
            color = "red"; reason = "大盘大幅下跌，高风险";
        } else if (changePct < -1 || ma5 < ma20) {
            color = "yellow"; reason = "市场偏弱，建议谨慎";
        } else {
            color = "green"; reason = "市场正常，可正常操作";
        }
        light.put("color", color);
        light.put("reason", reason);
        light.put("index_change", changePct);
        light.put("ma5_above_ma20", ma5 > ma20);
        return light;
    }

    /** 信号灯变化时发送告警 */
    public void checkAndAlert(String previousColor) {
        Map<String, Object> current = getMarketLight();
        String newColor = (String) current.get("color");
        if (!newColor.equals(previousColor) && "red".equals(newColor)) {
            notificationService.sendMessage("🔴 市场信号灯: 红灯",
                    "大盘进入高风险区域: " + current.get("reason"));
        }
    }

    private double avgClose(List<StockDailyData> data, int period) {
        int start = Math.max(0, data.size() - period);
        double sum = 0; int count = 0;
        for (int i = start; i < data.size(); i++) { sum += data.get(i).getClosePrice(); count++; }
        return count > 0 ? sum / count : 0;
    }
}
