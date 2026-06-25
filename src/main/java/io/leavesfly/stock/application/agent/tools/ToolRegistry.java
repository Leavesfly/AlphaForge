package io.leavesfly.stock.application.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表 - 统一管理和执行所有Agent工具
 *
 * 参考TinyClaw的ToolRegistry设计，保持轻量级：
 * - 线程安全：ConcurrentHashMap确保并发安全
 * - 统一执行入口：execute()带日志和异常处理
 * - OpenAI兼容：getDefinitions()生成Function Calling格式
 * - 工具白名单：filter()支持按Agent角色配置差异化工具集
 *
 * 不引入TinyClaw的Reflection 2.0（ToolCallRecorder/RepairApplier）等重型功能。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    /** 注册工具 */
    public void register(Tool tool) {
        tools.put(tool.name(), tool);
        log.debug("注册工具: {}", tool.name());
    }

    /** 注销工具 */
    public void unregister(String name) {
        tools.remove(name);
        log.debug("注销工具: {}", name);
    }

    /** 按名称获取工具 */
    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** 检查工具是否存在 */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 执行工具（统一入口）
     *
     * @param name 工具名称
     * @param args 工具参数
     * @return 执行结果字符串
     * @throws ToolException 工具未找到或执行失败
     */
    public String execute(String name, Map<String, Object> args) throws ToolException {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolException("工具未找到: " + name, "TOOL_NOT_FOUND");
        }

        long start = System.currentTimeMillis();
        try {
            String result = tool.execute(args);
            long duration = System.currentTimeMillis() - start;
            log.info("工具执行完成: {} 耗时:{}ms 结果长度:{}", name, duration,
                    result != null ? result.length() : 0);
            return result;
        } catch (ToolException e) {
            long duration = System.currentTimeMillis() - start;
            log.error("工具执行失败: {} 耗时:{}ms 错误:{}", name, duration, e.getMessage());
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("工具执行异常: {} 耗时:{}ms 错误:{}", name, duration, e.getMessage());
            throw new ToolException("工具执行异常: " + name, e);
        }
    }

    /** 获取所有已注册工具名称 */
    public List<String> list() {
        return new ArrayList<>(tools.keySet());
    }

    /** 已注册工具数量 */
    public int count() {
        return tools.size();
    }

    /**
     * 生成OpenAI Function Calling格式的工具定义列表
     *
     * @return 工具定义列表（兼容OpenAI tools参数格式）
     */
    public List<Map<String, Object>> getDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            fn.put("parameters", tool.parameters());
            def.put("function", fn);
            definitions.add(def);
        }
        return definitions;
    }

    /**
     * 生成人类可读的工具摘要（用于注入system prompt）
     *
     * @return 工具摘要列表，格式: "- `tool_name` - 工具描述"
     */
    public List<String> getSummaries() {
        List<String> summaries = new ArrayList<>();
        for (Tool tool : tools.values()) {
            summaries.add("- " + tool.name() + ": " + tool.description());
        }
        return summaries;
    }

    /**
     * 生成工具说明文本（用于system prompt中的工具列表）
     *
     * 格式: "- tool_name: 描述 (参数: param1, param2)"
     */
    public String getToolSummaryText() {
        StringBuilder sb = new StringBuilder();
        for (Tool tool : tools.values()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description());
            Map<String, Object> params = tool.parameters();
            Object properties = params.get("properties");
            Object required = params.get("required");
            if (properties instanceof Map && !((Map<?, ?>) properties).isEmpty()) {
                sb.append(" (参数: ");
                StringJoiner joiner = new StringJoiner(", ");
                for (Object key : ((Map<?, ?>) properties).keySet()) {
                    if (required instanceof Object[] reqArr) {
                        boolean isRequired = Arrays.asList(reqArr).contains(key);
                        joiner.add(key + (isRequired ? "(必填)" : "(可选)"));
                    } else {
                        joiner.add(key.toString());
                    }
                }
                sb.append(joiner).append(")");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 创建只包含白名单工具的受限注册表
     *
     * 用于为不同Agent角色配置差异化工具集。
     * 若allowedToolNames为空，返回完整副本。
     */
    public ToolRegistry filter(List<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            ToolRegistry copy = new ToolRegistry();
            tools.values().forEach(copy::register);
            return copy;
        }
        ToolRegistry restricted = new ToolRegistry();
        for (String name : allowedToolNames) {
            Tool tool = tools.get(name);
            if (tool != null) {
                restricted.register(tool);
            } else {
                log.warn("白名单中的工具未注册，已忽略: {}", name);
            }
        }
        return restricted;
    }

    /** 清除所有工具 */
    public void clear() {
        tools.clear();
    }
}
