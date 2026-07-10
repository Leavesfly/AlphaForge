package io.leavesfly.alphaforge.application.agent.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Agent 内核骨架测试")
class AgentKernelSkeletonTest {

    @Nested
    @DisplayName("AgentTask 构造与执行策略")
    class TaskTest {

        @Test
        @DisplayName("默认执行策略：不允许状态变更")
        void defaultPolicy() {
            AgentTask task = AgentTask.of(AgentTaskType.STOCK_ANALYSIS)
                    .goal("分析 600519")
                    .input("stockCode", "600519")
                    .build();

            assertEquals(AgentTaskType.STOCK_ANALYSIS, task.getType());
            assertEquals("600519", task.inputString("stockCode"));
            assertEquals(5, task.getPolicy().getMaxToolCalls());
            assertEquals(120, task.getPolicy().getTimeoutSeconds());
            assertFalse(task.getPolicy().isAllowStateMutation());
        }

        @Test
        @DisplayName("Builder 可覆盖策略且 inputs 不可变")
        void builderOverrides() {
            AgentTask task = AgentTask.of(AgentTaskType.CHAT)
                    .maxToolCalls(8)
                    .allowStateMutation(true)
                    .input("k", "v")
                    .build();

            assertEquals(8, task.getPolicy().getMaxToolCalls());
            assertTrue(task.getPolicy().isAllowStateMutation());
            assertThrows(UnsupportedOperationException.class,
                    () -> task.getInputs().put("x", "y"));
        }

        @Test
        @DisplayName("type 为空时抛异常")
        void nullTypeRejected() {
            assertThrows(IllegalArgumentException.class, () -> AgentTask.of(null));
        }
    }

    @Nested
    @DisplayName("AgentContext 黑板")
    class ContextTest {

        @Test
        @DisplayName("初始化时拷贝任务 inputs，支持读写与快照")
        void blackboard() {
            AgentTask task = AgentTask.of(AgentTaskType.STOCK_ANALYSIS)
                    .input("stockCode", "000001")
                    .build();
            AgentContext ctx = new AgentContext(task);

            assertEquals("000001", ctx.getString("stockCode"));
            ctx.put("score", 88);
            assertEquals(88, ctx.get("score", Integer.class));

            ctx.trace("step-1");
            assertEquals(1, ctx.getTrace().size());
            assertNotNull(ctx.getTaskId());
            assertTrue(ctx.snapshot().containsKey("stockCode"));
        }
    }

    @Nested
    @DisplayName("规划器：SOP 骨架 + Hybrid")
    class PlannerTest {

        private final SopPlanner sop = new SopPlanner();
        private final HybridPlanner hybrid = new HybridPlanner(sop, new LlmPlanner());

        @Test
        @DisplayName("STOCK_ANALYSIS 产出多步只读骨架")
        void stockAnalysisPlan() {
            AgentTask task = AgentTask.of(AgentTaskType.STOCK_ANALYSIS).build();
            AgentPlan plan = sop.plan(task, new AgentContext(task));

            assertFalse(plan.isEmpty());
            assertFalse(plan.hasMutatingStep());
            assertTrue(plan.getSteps().stream().anyMatch(s -> "analyze".equals(s.getName())));
        }

        @Test
        @DisplayName("AUTONOMY_DECISION 含状态变更步骤")
        void autonomyPlanHasMutating() {
            AgentTask task = AgentTask.of(AgentTaskType.AUTONOMY_DECISION).build();
            AgentPlan plan = sop.plan(task, new AgentContext(task));

            assertTrue(plan.hasMutatingStep());
        }

        @Test
        @DisplayName("HybridPlanner 优先采用非空 SOP 骨架")
        void hybridUsesSop() {
            AgentTask task = AgentTask.of(AgentTaskType.CHAT).build();
            AgentPlan plan = hybrid.plan(task, new AgentContext(task));

            assertEquals(1, plan.getSteps().size());
            assertEquals("react_loop", plan.getSteps().get(0).getName());
        }

        @Test
        @DisplayName("SopPlanner 从 resources/plans/*.yaml 加载骨架，含状态变更标记")
        void yamlPlansLoaded() {
            SopPlanner planner = new SopPlanner();
            planner.loadYamlPlans();

            AgentTask analysis = AgentTask.of(AgentTaskType.STOCK_ANALYSIS).build();
            AgentPlan plan = planner.plan(analysis, new AgentContext(analysis));
            assertFalse(plan.isEmpty());
            assertTrue(plan.getSteps().stream().anyMatch(s -> "analyze".equals(s.getName())));

            AgentTask autonomy = AgentTask.of(AgentTaskType.AUTONOMY_DECISION).build();
            assertTrue(planner.plan(autonomy, new AgentContext(autonomy)).hasMutatingStep());
        }
    }

    @Nested
    @DisplayName("Guardrail 治理闸")
    class GuardrailTest {

        @Test
        @DisplayName("只读步骤总是放行")
        void readOnlyAllowed() {
            AgentGuardrail guardrail = new AgentGuardrail();
            AgentTask task = AgentTask.of(AgentTaskType.STOCK_ANALYSIS).build();
            PlanStep readOnly = PlanStep.of("s", "gather", PlanStep.Kind.TOOL, "读数据");

            assertDoesNotThrow(() -> guardrail.assertStepAllowed(task, readOnly));
        }

        @Test
        @DisplayName("状态变更步骤在任务未授权时被拦截")
        void mutatingBlockedWhenNotAuthorized() {
            AgentGuardrail guardrail = new AgentGuardrail();
            AgentTask task = AgentTask.of(AgentTaskType.AUTONOMY_DECISION).build(); // 默认不授权变更
            PlanStep mutating = PlanStep.mutating("e", "execute_signal", PlanStep.Kind.TOOL, "下单");

            assertThrows(AgentGuardrailException.class,
                    () -> guardrail.assertStepAllowed(task, mutating));
            assertFalse(guardrail.isMutationAllowed(task));
        }

        @Test
        @DisplayName("授权变更但无自治依赖时（autonomy 未注入）放行")
        void mutatingAllowedWhenAuthorizedNoAutonomy() {
            AgentGuardrail guardrail = new AgentGuardrail(); // autonomyPolicy/riskGuard 均为 null
            AgentTask task = AgentTask.of(AgentTaskType.AUTONOMY_DECISION)
                    .allowStateMutation(true)
                    .build();
            PlanStep mutating = PlanStep.mutating("e", "execute_signal", PlanStep.Kind.TOOL, "下单");

            assertDoesNotThrow(() -> guardrail.assertStepAllowed(task, mutating));
            assertTrue(guardrail.isMutationAllowed(task));
        }
    }

    @Nested
    @DisplayName("AgentResult")
    class ResultTest {

        @Test
        @DisplayName("ok 与 fail 构造")
        void okAndFail() {
            AgentResult ok = AgentResult.ok(AgentTaskType.CHAT)
                    .output("hi").toolCalls(2)
                    .toolCallLog(List.of("t1", "t2"))
                    .data("k", "v").build();
            assertTrue(ok.isSuccess());
            assertEquals("hi", ok.getOutput());
            assertEquals(2, ok.getToolCalls());
            assertEquals("v", ok.data("k"));

            AgentResult fail = AgentResult.fail(AgentTaskType.STOCK_ANALYSIS, "缺少 stockCode");
            assertFalse(fail.isSuccess());
            assertEquals("缺少 stockCode", fail.getError());
        }

        @Test
        @DisplayName("data 不可变")
        void immutableData() {
            AgentResult ok = AgentResult.ok(AgentTaskType.CHAT).build();
            assertThrows(UnsupportedOperationException.class,
                    () -> ((Map<String, Object>) ok.getData()).put("x", 1));
        }
    }

    @Nested
    @DisplayName("内核 AUTONOMY_DECISION 分派治理")
    class AutonomyDispatchTest {

        private AgentKernel newKernel() {
            SopPlanner sop = new SopPlanner();
            return new AgentKernel(sop, new AgentGuardrail(), new PassThroughCritic(),
                    null, null, null);
        }

        @Test
        @DisplayName("默认策略下状态变更任务被 Guardrail 拦截")
        void defaultPolicyBlocked() {
            AgentResult result = newKernel().run(
                    AgentTask.of(AgentTaskType.AUTONOMY_DECISION).build());

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("治理拦截"));
        }

        @Test
        @DisplayName("授权变更后通过 Guardrail，因执行器未启用而失败")
        void authorizedPassesGuardrail() {
            AgentResult result = newKernel().run(
                    AgentTask.of(AgentTaskType.AUTONOMY_DECISION)
                            .allowStateMutation(true)
                            .build());

            assertFalse(result.isSuccess());
            assertNotNull(result.getError());
            assertTrue(result.getError().contains("未启用"));
        }
    }
}
