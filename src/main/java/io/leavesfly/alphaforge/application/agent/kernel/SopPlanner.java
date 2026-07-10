package io.leavesfly.alphaforge.application.agent.kernel;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * SOP 骨架规划器 — 按任务类型返回确定性步骤骨架。
 *
 * <p>关键风控/合规步骤在此固定（如状态变更步骤必经 Guardrail），保证可控性。
 * 阶段 2 将把骨架外置到 {@code resources/plans/*.yaml}，此处先以内置表提供默认骨架。
 */
@Component
public class SopPlanner implements Planner {

    private static final Logger log = LoggerFactory.getLogger(SopPlanner.class);

    /** 从 resources/plans/*.yaml 加载的计划骨架（优先于内置骨架）。 */
    private final Map<AgentTaskType, AgentPlan> yamlPlans = new EnumMap<>(AgentTaskType.class);

    @PostConstruct
    void loadYamlPlans() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:plans/*.yaml");
            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    AgentPlan plan = parsePlan(yaml.load(in));
                    if (plan != null && !plan.isEmpty()) {
                        yamlPlans.put(plan.getTaskType(), plan);
                    }
                } catch (Exception e) {
                    log.warn("解析 SOP 计划失败 {}: {}", resource.getFilename(), e.getMessage());
                }
            }
            log.info("SopPlanner 从 YAML 加载 {} 个计划骨架: {}", yamlPlans.size(), yamlPlans.keySet());
        } catch (Exception e) {
            log.warn("扫描 SOP YAML 失败，使用内置骨架: {}", e.getMessage());
        }
    }

    @Override
    public AgentPlan plan(AgentTask task, AgentContext context) {
        AgentPlan fromYaml = yamlPlans.get(task.getType());
        if (fromYaml != null && !fromYaml.isEmpty()) {
            return fromYaml;
        }
        return builtinPlan(task.getType());
    }

    /** 内置骨架（YAML 缺失时的兜底）。 */
    private AgentPlan builtinPlan(AgentTaskType type) {
        List<PlanStep> steps = new ArrayList<>();

        switch (type) {
            case CHAT -> steps.add(PlanStep.of("react", "react_loop",
                    PlanStep.Kind.LLM_REASONING, "ReAct 工具循环，自主调用工具后回复"));

            case STOCK_ANALYSIS -> {
                steps.add(PlanStep.of("prepare", "prepare_context",
                        PlanStep.Kind.DETERMINISTIC, "装配上下文与学习提示"));
                steps.add(PlanStep.of("gather", "gather_data",
                        PlanStep.Kind.TOOL, "获取行情/技术/新闻数据"));
                steps.add(PlanStep.of("analyze", "analyze",
                        PlanStep.Kind.LLM_REASONING, "多维研判并产出结构化结论"));
                steps.add(PlanStep.of("extract", "extract_signal",
                        PlanStep.Kind.DETERMINISTIC, "抽取决策信号（受质量门约束）"));
            }

            case STRATEGY_GENERATE -> {
                steps.add(PlanStep.of("gen", "generate_strategy",
                        PlanStep.Kind.LLM_REASONING, "根据目标生成策略定义"));
                steps.add(PlanStep.of("backtest", "run_backtest",
                        PlanStep.Kind.TOOL, "对生成策略跑基准回测"));
            }

            case STRATEGY_OPTIMIZE -> {
                steps.add(PlanStep.of("optimize", "optimize_strategy",
                        PlanStep.Kind.LLM_REASONING, "参数寻优与迭代改写"));
                steps.add(PlanStep.mutating("apply", "apply_params",
                        PlanStep.Kind.DETERMINISTIC, "写回策略参数（状态变更，必经 Guardrail）"));
            }

            case NL_SCREENING -> {
                steps.add(PlanStep.of("parse", "parse_criteria",
                        PlanStep.Kind.LLM_REASONING, "解析自然语言选股条件"));
                steps.add(PlanStep.of("screen", "run_screening",
                        PlanStep.Kind.TOOL, "执行全市场扫描与排名"));
            }

            case PORTFOLIO_REVIEW -> steps.add(PlanStep.of("review", "review_portfolio",
                    PlanStep.Kind.LLM_REASONING, "组合持仓审查与建议"));

            case AUTONOMY_DECISION -> {
                steps.add(PlanStep.of("judge", "judge_signal",
                        PlanStep.Kind.LLM_REASONING, "研判信号是否执行"));
                steps.add(PlanStep.mutating("execute", "execute_signal",
                        PlanStep.Kind.TOOL, "纸面执行信号（状态变更，必经 Guardrail）"));
            }

            case FACTOR_EVOLUTION -> steps.add(PlanStep.of("evolve", "run_evolution_cycle",
                    PlanStep.Kind.TOOL, "触发一轮因子进化"));

            default -> {
                // 未知类型返回空骨架，交由 HybridPlanner 兜底
            }
        }

        return new AgentPlan(type, steps);
    }

    /** 将 YAML 文档解析为 AgentPlan（结构非法时返回 null）。 */
    @SuppressWarnings("unchecked")
    private AgentPlan parsePlan(Object doc) {
        if (!(doc instanceof Map)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) doc;
        Object typeObj = map.get("taskType");
        if (typeObj == null) {
            return null;
        }
        AgentTaskType type;
        try {
            type = AgentTaskType.valueOf(String.valueOf(typeObj).trim());
        } catch (IllegalArgumentException e) {
            log.warn("未知的 taskType: {}", typeObj);
            return null;
        }

        List<PlanStep> steps = new ArrayList<>();
        Object stepsObj = map.get("steps");
        if (stepsObj instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> s = (Map<String, Object>) item;
                String id = String.valueOf(s.getOrDefault("id", ""));
                String name = String.valueOf(s.getOrDefault("name", ""));
                String description = String.valueOf(s.getOrDefault("description", ""));
                boolean mutating = Boolean.parseBoolean(String.valueOf(s.getOrDefault("mutating", "false")));
                PlanStep.Kind kind;
                try {
                    kind = PlanStep.Kind.valueOf(String.valueOf(s.getOrDefault("kind", "DETERMINISTIC")).trim());
                } catch (IllegalArgumentException e) {
                    kind = PlanStep.Kind.DETERMINISTIC;
                }
                steps.add(new PlanStep(id, name, kind, description, mutating));
            }
        }
        return new AgentPlan(type, steps);
    }
}
