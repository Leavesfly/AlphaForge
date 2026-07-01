package io.leavesfly.alphaforge.application.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * Prompt 模板管理器 — 外部化 + 版本化 Prompt 管理
 *
 * 核心能力：
 * 1. 从 classpath:prompts/*.yaml 加载 Prompt 模板
 * 2. 支持版本管理（同一名多版本，按 active 标记选择）
 * 3. 支持变量替换（{variable_name} 语法）
 * 4. 运行时热加载（通过 reload() 方法）
 *
 * Prompt YAML 格式：
 *   name: stock_analysis_system
 *   version: "1.1"
 *   active: true
 *   description: 股票分析系统 Prompt
 *   template: |
 *     你是一位专业的AI股票分析助手...
 *     可用工具：{tools}
 */
@Service
public class PromptManager {

    private static final Logger log = LoggerFactory.getLogger(PromptManager.class);
    private static final String PROMPTS_DIR = "prompts/";

    private final ObjectMapper yamlMapper;
    private final Map<String, PromptTemplate> templates = new HashMap<>();

    public PromptManager(@Qualifier("yamlObjectMapper") ObjectMapper yamlMapper) {
        this.yamlMapper = yamlMapper;
    }

    @PostConstruct
    public void init() {
        loadTemplates();
    }

    /** 加载所有 Prompt 模板 */
    public synchronized void loadTemplates() {
        templates.clear();
        try {
            // 尝试加载 prompts 目录下的所有 YAML 文件
            // 由于 classpath 资源列举受限，这里使用预定义的模板名列表
            String[] knownPrompts = {
                    "stock_analysis_system",
                    "react_agent_system",
                    "technical_agent_system",
                    "fundamental_agent_system",
                    "risk_agent_system",
                    "intelligence_system",
                    "synthesis_system",
                    "synthesis_user"
            };

            for (String name : knownPrompts) {
                loadTemplate(name);
            }

            log.info("已加载 {} 个 Prompt 模板", templates.size());
        } catch (Exception e) {
            log.warn("加载 Prompt 模板失败，将使用代码内嵌 Prompt: {}", e.getMessage());
        }
    }

    private void loadTemplate(String name) {
        String path = PROMPTS_DIR + name + ".yaml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) return; // 文件不存在，跳过

            @SuppressWarnings("unchecked")
            Map<String, Object> raw = yamlMapper.readValue(in, Map.class);

            String version = String.valueOf(raw.getOrDefault("version", "1.0"));
            boolean active = Boolean.TRUE.equals(raw.getOrDefault("active", true));
            String description = String.valueOf(raw.getOrDefault("description", ""));
            String template = String.valueOf(raw.getOrDefault("template", ""));

            if (active && !template.isEmpty()) {
                templates.put(name, new PromptTemplate(name, version, description, template));
                log.debug("加载 Prompt 模板: {} v{}", name, version);
            }
        } catch (Exception e) {
            log.debug("加载 Prompt 模板 {} 失败: {}", name, e.getMessage());
        }
    }

    /**
     * 获取 Prompt 模板（未渲染）
     *
     * @return 模板文本，未找到返回 null
     */
    public String getTemplate(String name) {
        PromptTemplate t = templates.get(name);
        return t != null ? t.template : null;
    }

    /**
     * 获取 Prompt 模板并渲染变量
     *
     * @param name      模板名
     * @param variables 变量映射（替换 {key} 占位符）
     * @return 渲染后的文本，未找到返回 null
     */
    public String render(String name, Map<String, Object> variables) {
        String template = getTemplate(name);
        if (template == null) return null;

        String result = template;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}",
                        String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    /** 检查模板是否存在 */
    public boolean hasTemplate(String name) {
        return templates.containsKey(name);
    }

    /**
     * 获取模板，未找到时返回默认值
     * 消除调用方重复的 hasTemplate + getTemplate + fallback 模式
     */
    public String getTemplateOrDefault(String name, String defaultValue) {
        String template = getTemplate(name);
        return (template != null && !template.isEmpty()) ? template : defaultValue;
    }

    /** 列出所有已加载的模板名 */
    public Set<String> listTemplateNames() {
        return Collections.unmodifiableSet(templates.keySet());
    }

    /** 热加载 */
    public synchronized void reload() {
        loadTemplates();
        log.info("Prompt 模板已热更新");
    }

    // ===== 数据类 =====

    private record PromptTemplate(String name, String version, String description, String template) {}
}
