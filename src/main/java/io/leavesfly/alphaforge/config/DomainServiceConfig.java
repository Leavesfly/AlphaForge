package io.leavesfly.alphaforge.config;

import io.leavesfly.alphaforge.domain.service.NameToCodeResolver;
import io.leavesfly.alphaforge.domain.service.TechnicalAnalysisService;
import io.leavesfly.alphaforge.domain.service.TechnicalIndicatorCalculator;
import io.leavesfly.alphaforge.domain.service.TradingCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域服务 Bean 装配配置
 *
 * domain 层不依赖 Spring，由配置层统一注册为 Bean。
 * 从 AppConfig 中提取，遵循单一职责原则。
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public TechnicalAnalysisService technicalAnalysisService() {
        return new TechnicalAnalysisService();
    }

    @Bean
    public TradingCalendar tradingCalendar() {
        return new TradingCalendar();
    }

    @Bean
    public NameToCodeResolver nameToCodeResolver() {
        return new NameToCodeResolver();
    }

    @Bean
    public TechnicalIndicatorCalculator technicalIndicatorCalculator() {
        return new TechnicalIndicatorCalculator();
    }
}
