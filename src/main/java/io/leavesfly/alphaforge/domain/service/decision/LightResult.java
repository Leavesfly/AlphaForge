package io.leavesfly.alphaforge.domain.service.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单盏灯的评估结果：灯色 + 理由链 + 结构化明细。
 */
public class LightResult {

    private LightColor color;
    private final List<String> reasons = new ArrayList<>();
    private final Map<String, Object> detail = new LinkedHashMap<>();

    public LightResult(LightColor color) {
        this.color = color;
    }

    public LightColor getColor() {
        return color;
    }

    public void setColor(LightColor color) {
        this.color = color;
    }

    /** 封顶：仅在当前为红时降为黄（大盘 risk-off 场景）；灰灯不参与封顶，保持诚实降级语义 */
    public void capAt(LightColor ceiling) {
        if (color == LightColor.RED && ceiling == LightColor.YELLOW) {
            color = LightColor.YELLOW;
        }
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void addReason(String reason) {
        reasons.add(reason);
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void putDetail(String key, Object value) {
        detail.put(key, value);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("color", color.name().toLowerCase());
        map.put("colorCn", color.getCn());
        map.put("reasons", List.copyOf(reasons));
        map.put("detail", new LinkedHashMap<>(detail));
        return map;
    }
}
