package io.leavesfly.alphaforge.application.service.system;

import io.leavesfly.alphaforge.application.service.loop.LoopStateManager;
import io.leavesfly.alphaforge.infrastructure.dataprovider.DataSourceDoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源健康服务 — 体检编排 + Loop 健康上下文合并。
 *
 * <p>体检委托 {@link DataSourceDoctor}（TTL 缓存 + 串行约束在基础设施层实现），
 * 应用层负责把 Loop 循环自身的健康报告附在同一响应里，形成"数据可用性 + 循环状态"
 * 的完整自感知视图。</p>
 */
@Service
public class DataSourceHealthService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceHealthService.class);

    private final DataSourceDoctor doctor;
    private final LoopStateManager loopStateManager;

    public DataSourceHealthService(DataSourceDoctor doctor, LoopStateManager loopStateManager) {
        this.doctor = doctor;
        this.loopStateManager = loopStateManager;
    }

    /**
     * 获取数据源体检结果（TTL 内命中缓存；force=true 绕过 TTL 重新探测，入口层已限流）。
     */
    public Map<String, Object> getCachedOrProbe(boolean force) {
        Map<String, Object> report = new LinkedHashMap<>(doctor.getCachedOrProbe(force));
        try {
            report.put("loopHealth", loopStateManager.getHealthReport());
        } catch (Exception e) {
            log.warn("Loop 健康报告获取失败（不影响体检结果）: {}", e.getMessage());
        }
        return report;
    }
}
