package io.leavesfly.alphaforge.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 数据源体检配置 — DataSourceDoctor 的探针参数。
 *
 * <p>体检自身会对每个数据源发起真实拉取，遵循"限流场景缓存优先"实践：
 * 结果缓存 TTL 默认 300 秒，避免体检加剧上游 429。</p>
 */
@Component
public class DataSourceDoctorConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceDoctorConfig.class);

    private final EnvVarProvider env;

    /** 体检结果缓存 TTL（秒） */
    private int ttlSeconds = 300;
    /** A 股探针标的 */
    private String probeAShare = "600519";
    /** 美股探针标的 */
    private String probeUs = "AAPL";
    /** 港股探针标的 */
    private String probeHk = "00700";
    /** 探针回看天数 */
    private int probeDays = 14;

    public DataSourceDoctorConfig(EnvVarProvider env) {
        this.env = env;
    }

    @PostConstruct
    void init() {
        this.ttlSeconds = env.getInt("DATASOURCE_DOCTOR_TTL_SECONDS", 300);
        this.probeAShare = env.get("DATASOURCE_DOCTOR_PROBE_A", "600519");
        this.probeUs = env.get("DATASOURCE_DOCTOR_PROBE_US", "AAPL");
        this.probeHk = env.get("DATASOURCE_DOCTOR_PROBE_HK", "00700");
        this.probeDays = env.getInt("DATASOURCE_DOCTOR_PROBE_DAYS", 14);
        log.info("数据源体检配置: TTL={}s, 探针 A/{}/ US/{}/ HK/{} 回看 {} 天",
                ttlSeconds, probeAShare, probeUs, probeHk, probeDays);
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public String getProbeAShare() {
        return probeAShare;
    }

    public String getProbeUs() {
        return probeUs;
    }

    public String getProbeHk() {
        return probeHk;
    }

    public int getProbeDays() {
        return probeDays;
    }
}
