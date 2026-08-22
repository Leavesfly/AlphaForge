package io.leavesfly.alphaforge.application.autonomy;

import io.leavesfly.alphaforge.application.evaluation.StrategyQualityScore;
import io.leavesfly.alphaforge.config.AutonomyConfig;
import io.leavesfly.alphaforge.config.EnvVarProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L4 自主门面测试 — 固化「总开关一票否决」「仓位上限钳制」「晋升等级门槛」三项风控契约。
 *
 * <p>AutonomyPolicy 被 15+ 处依赖，且直接决定是否自动下单/晋升/改参，
 * 其默认关闭与钳制行为属资金安全底线，需显式测试保护。</p>
 */
@DisplayName("AutonomyPolicy — 自主开关、仓位钳制与晋升门槛")
class AutonomyPolicyTest {

    /** 只从固定 Map 取值的 EnvVarProvider，隔离 .env 与系统环境变量对测试的干扰。 */
    private static final class FixedEnv extends EnvVarProvider {
        private final Map<String, String> values;

        FixedEnv(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String get(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }
    }

    private static AutonomyPolicy policyWith(Map<String, String> env) {
        AutonomyConfig config = new AutonomyConfig(new FixedEnv(env));
        config.init();
        return new AutonomyPolicy(config, new AutonomyAuditLog());
    }

    private static Map<String, String> env(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Nested
    @DisplayName("总开关一票否决")
    class MasterSwitch {

        @Test
        @DisplayName("默认配置下全部自主动作关闭")
        void defaultsAreAllOff() {
            AutonomyPolicy policy = policyWith(env());

            assertFalse(policy.isEnabled());
            assertFalse(policy.canAutoExecuteSignals());
            assertFalse(policy.canAutoPromote());
            assertFalse(policy.canAutoDemote());
            assertFalse(policy.canAutoApplyParams());
        }

        @Test
        @DisplayName("总开关关闭时，即使子开关为 true 也不得放行")
        void subSwitchesRequireMasterEnabled() {
            AutonomyPolicy policy = policyWith(env(
                    "AUTONOMY_ENABLED", "false",
                    "AUTONOMY_AUTO_EXECUTE_SIGNALS", "true",
                    "AUTONOMY_AUTO_PROMOTE", "true",
                    "AUTONOMY_AUTO_DEMOTE", "true",
                    "AUTONOMY_AUTO_APPLY_PARAMS", "true"));

            assertFalse(policy.canAutoExecuteSignals(), "总开关关闭必须一票否决自动下单");
            assertFalse(policy.canAutoPromote());
            assertFalse(policy.canAutoDemote());
            assertFalse(policy.canAutoApplyParams());
        }

        @Test
        @DisplayName("总开关与子开关同时开启才放行")
        void bothEnabledAllows() {
            AutonomyPolicy policy = policyWith(env(
                    "AUTONOMY_ENABLED", "true",
                    "AUTONOMY_AUTO_EXECUTE_SIGNALS", "true"));

            assertTrue(policy.isEnabled());
            assertTrue(policy.canAutoExecuteSignals());
            assertFalse(policy.canAutoPromote(), "未显式开启的子开关仍保持关闭");
        }
    }

    @Nested
    @DisplayName("仓位钳制")
    class PositionSizing {

        @Test
        @DisplayName("默认仓位超过上限时按上限钳制")
        void clampsToMax() {
            AutonomyPolicy policy = policyWith(env(
                    "AUTONOMY_DEFAULT_POSITION_PCT", "0.50",
                    "AUTONOMY_MAX_POSITION_PCT", "0.20"));

            assertEquals(0.20, policy.positionPct(), 1e-9);
        }

        @Test
        @DisplayName("默认仓位低于上限时按默认值")
        void keepsDefaultBelowMax() {
            AutonomyPolicy policy = policyWith(env(
                    "AUTONOMY_DEFAULT_POSITION_PCT", "0.05",
                    "AUTONOMY_MAX_POSITION_PCT", "0.20"));

            assertEquals(0.05, policy.positionPct(), 1e-9);
        }
    }

    @Nested
    @DisplayName("晋升等级门槛")
    class PromoteGrade {

        @Test
        @DisplayName("非法等级配置回退为 B，不放宽门槛")
        void invalidGradeFallsBackToB() {
            AutonomyPolicy policy = policyWith(env("AUTONOMY_MIN_PROMOTE_GRADE", "NOT_A_GRADE"));

            assertEquals(StrategyQualityScore.QualityGrade.B, policy.minPromoteGrade());
        }

        @Test
        @DisplayName("门槛为 B 时：A/B 达标，C/D 不达标")
        void gradeOrderingAgainstB() {
            AutonomyPolicy policy = policyWith(env("AUTONOMY_MIN_PROMOTE_GRADE", "B"));

            assertTrue(policy.meetsPromoteGrade(StrategyQualityScore.QualityGrade.A));
            assertTrue(policy.meetsPromoteGrade(StrategyQualityScore.QualityGrade.B));
            assertFalse(policy.meetsPromoteGrade(StrategyQualityScore.QualityGrade.C));
            assertFalse(policy.meetsPromoteGrade(StrategyQualityScore.QualityGrade.D));
        }

        @Test
        @DisplayName("门槛为 A 时仅 A 达标")
        void strictestGradeOnlyAllowsA() {
            AutonomyPolicy policy = policyWith(env("AUTONOMY_MIN_PROMOTE_GRADE", "A"));

            assertTrue(policy.meetsPromoteGrade(StrategyQualityScore.QualityGrade.A));
            assertFalse(policy.meetsPromoteGrade(StrategyQualityScore.QualityGrade.B));
        }

        @Test
        @DisplayName("等级缺失（null）视为不达标")
        void nullGradeIsNotPromotable() {
            AutonomyPolicy policy = policyWith(env("AUTONOMY_MIN_PROMOTE_GRADE", "D"));

            assertFalse(policy.meetsPromoteGrade(null));
        }
    }

    @Nested
    @DisplayName("审计留痕")
    class Auditing {

        @Test
        @DisplayName("audit 调用写入审计日志")
        void auditIsRecorded() {
            AutonomyConfig config = new AutonomyConfig(new FixedEnv(env()));
            config.init();
            AutonomyAuditLog auditLog = new AutonomyAuditLog();
            AutonomyPolicy policy = new AutonomyPolicy(config, auditLog);

            policy.audit("auto_demote", "strategy", "s-1", "PUBLISHED", "TESTING", "matchRate 低");

            assertEquals(1, auditLog.recent(10).size());
            assertEquals("auto_demote", auditLog.recent(10).get(0).get("action"));
        }
    }
}
