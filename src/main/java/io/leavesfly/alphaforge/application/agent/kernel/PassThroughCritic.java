package io.leavesfly.alphaforge.application.agent.kernel;

import org.springframework.stereotype.Component;

/**
 * 直通评审器（阶段 1 默认）— 不做额外校验，原样返回草稿结果。
 *
 * <p>现有质量校验（幻觉/逻辑）已内置于 AgentAnalysisService，阶段 3+ 再收敛到此处。
 */
@Component
public class PassThroughCritic implements Critic {

    @Override
    public AgentResult review(AgentContext context, AgentResult draft) {
        return draft;
    }
}
