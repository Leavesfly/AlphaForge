package io.leavesfly.alphaforge.application.agent.skills;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * alpha-forge-skill CLI 桥接启用条件。
 *
 * <p>仅当 {@code SKILL_BRIDGE_DIR}（系统环境变量优先，其次 .env）指向的目录含
 * {@code scripts/} 时才装配 {@link SkillCliBridge} 与依赖它的 Agent 工具；未配置时
 * 整个桥接体系不注册任何 Bean，对现有系统零影响。</p>
 *
 * <p>桥接类与工具各自标注 {@code @Conditional(SkillBridgeEnabledCondition.class)}
 * 而非 {@code @ConditionalOnBean}：后者用于组件扫描的 Bean 时依赖注册顺序、结果不稳定，
 * 而本条件只探测环境变量与目录，求值与 Bean 顺序无关。</p>
 */
public class SkillBridgeEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String dir = System.getenv("SKILL_BRIDGE_DIR");
        if (dir == null || dir.isBlank()) {
            try {
                dir = Dotenv.configure().ignoreIfMissing().load().get("SKILL_BRIDGE_DIR");
            } catch (Exception ignored) {
                // .env 不可用时仅依赖系统环境变量
            }
        }
        if (dir == null || dir.isBlank()) {
            return false;
        }
        return Files.isDirectory(Path.of(dir.trim()).resolve("scripts"));
    }
}
