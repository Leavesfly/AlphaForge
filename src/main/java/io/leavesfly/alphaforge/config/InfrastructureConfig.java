package io.leavesfly.alphaforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * 基础设施共享 Bean 配置
 *
 * 统一创建 ObjectMapper、OkHttpClient 等线程安全的重量级对象，
 * 避免各组件重复 new 导致的内存浪费和配置不一致。
 */
@Configuration
public class InfrastructureConfig {

    /** 默认 User-Agent，供 HTTP 请求统一使用 */
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    /**
     * 全局共享 ObjectMapper（线程安全）
     * 注册 JavaTimeModule 支持 LocalDateTime 等时间类型
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * YAML 格式的 ObjectMapper（用于解析 YAML 配置文件）
     */
    @Bean("yamlObjectMapper")
    public ObjectMapper yamlObjectMapper() {
        return new ObjectMapper(new YAMLFactory());
    }

    /**
     * 全局共享 OkHttpClient（线程安全，内含连接池和线程池）
     * 各组件如需不同超时，可用 client.newBuilder().readTimeout() 派生
     */
    @Bean
    @Primary
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
}
