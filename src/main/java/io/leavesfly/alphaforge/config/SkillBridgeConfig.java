package io.leavesfly.alphaforge.config;

import io.leavesfly.alphaforge.application.agent.skills.SkillCliBridge;
import io.leavesfly.alphaforge.application.agent.tools.impl.DcaPlanTool;
import io.leavesfly.alphaforge.application.agent.tools.impl.ScreenerPresetTool;
import io.leavesfly.alphaforge.application.agent.tools.impl.StageAnalysisTool;
import io.leavesfly.alphaforge.application.agent.tools.impl.StrategyValidateTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * alpha-forge-skill CLI 桥接装配 — 仅当 SKILL_BRIDGE_DIR 配置有效时生效。
 *
 * <p>桥接 alpha-forge-skill 尚缺的四项能力：个股阶段定位（stage_analysis）、
 * DCA/XIRR 定投（dca_plan）、走步+PBO 稳健性验证（strategy_validate）、
 * 方法论预设筛选（screener_preset）。工具 Bean 由 {@link
 * io.leavesfly.alphaforge.application.agent.tools.ToolConfig} 自动注册进
 * ToolRegistry；未配置时不注册任何 Bean，对现有系统零影响。</p>
 */
@Configuration
@Conditional(SkillBridgeEnabledCondition.class)
public class SkillBridgeConfig {

    private static final Logger log = LoggerFactory.getLogger(SkillBridgeConfig.class);

    @Bean
    public SkillCliBridge skillCliBridge(EnvVarProvider envVarProvider) {
        SkillCliBridge bridge = new SkillCliBridge(envVarProvider);
        log.info("alpha-forge-skill CLI 桥接已启用: {}", bridge.getScriptsDir());
        return bridge;
    }

    @Bean
    public StageAnalysisTool stageAnalysisTool(SkillCliBridge skillCliBridge) {
        return new StageAnalysisTool(skillCliBridge);
    }

    @Bean
    public DcaPlanTool dcaPlanTool(SkillCliBridge skillCliBridge) {
        return new DcaPlanTool(skillCliBridge);
    }

    @Bean
    public StrategyValidateTool strategyValidateTool(SkillCliBridge skillCliBridge) {
        return new StrategyValidateTool(skillCliBridge);
    }

    @Bean
    public ScreenerPresetTool screenerPresetTool(SkillCliBridge skillCliBridge) {
        return new ScreenerPresetTool(skillCliBridge);
    }
}
