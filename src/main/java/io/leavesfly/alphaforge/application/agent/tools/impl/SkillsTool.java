package io.leavesfly.alphaforge.application.agent.tools.impl;

import io.leavesfly.alphaforge.application.agent.skills.SkillsInstaller;
import io.leavesfly.alphaforge.application.agent.skills.SkillsLoader;
import io.leavesfly.alphaforge.application.agent.skills.SkillsLoader.SkillInfo;
import io.leavesfly.alphaforge.application.agent.tools.Tool;
import io.leavesfly.alphaforge.application.agent.tools.ToolException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能管理工具 - 让LLM自主发现、调用、安装和移除技能
 *
 * 参考TinyClaw的SkillsTool设计，保持轻量级：
 * - list: 列出所有可用技能（内置+已安装）
 * - invoke: 调用技能，返回完整指令内容
 * - install: 从GitHub仓库安装技能
 * - remove: 删除已安装的技能
 *
 * 技能内容是给LLM的Markdown指令，LLM读取后按指令步骤调用其他工具。
 */
@Component
public class SkillsTool implements Tool {

    private final SkillsLoader skillsLoader;
    private final SkillsInstaller skillsInstaller;

    public SkillsTool(SkillsLoader skillsLoader, SkillsInstaller skillsInstaller) {
        this.skillsLoader = skillsLoader;
        this.skillsInstaller = skillsInstaller;
    }

    @Override
    public String name() {
        return "skills";
    }

    @Override
    public String description() {
        return "管理和调用技能。"
                + "使用 'list' 列出所有可用技能（内置+已安装）；"
                + "使用 'invoke' 调用指定技能，返回该技能的完整执行指令（包含步骤和输出格式）；"
                + "使用 'install' 从GitHub仓库安装技能（参数 repo，格式 owner/repo 或 owner/repo/skill-name）；"
                + "使用 'remove' 删除已安装的技能（参数 name，只能删除通过 install 安装的技能）。"
                + "当用户任务与某个技能描述匹配时，先 invoke 获取指令，再按指令调用其他工具执行。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> action = new HashMap<>();
        action.put("type", "string");
        action.put("description", "操作类型：'list' 列出技能，'invoke' 调用技能");
        action.put("enum", new String[]{"list", "invoke", "install", "remove"});
        properties.put("action", action);

        Map<String, Object> name = new HashMap<>();
        name.put("type", "string");
        name.put("description", "技能名称（invoke/remove操作必需）");
        properties.put("name", name);

        Map<String, Object> repo = new HashMap<>();
        repo.put("type", "string");
        repo.put("description", "GitHub仓库说明符（install操作必需），格式：owner/repo 或 owner/repo/skill-name 或完整URL");
        properties.put("repo", repo);

        params.put("properties", properties);
        params.put("required", new String[]{"action"});
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) throws ToolException {
        String action = (String) args.get("action");
        if (action == null || action.isBlank()) {
            throw new ToolException("参数 action 不能为空", "PARAM_MISSING");
        }

        return switch (action) {
            case "list" -> executeList();
            case "invoke" -> executeInvoke(args);
            case "install" -> executeInstall(args);
            case "remove" -> executeRemove(args);
            default -> throw new ToolException("未知操作: " + action + "，有效操作：list、invoke、install、remove", "INVALID_ACTION");
        };
    }

    /** 列出所有可用技能 */
    private String executeList() {
        List<SkillInfo> skills = skillsLoader.listSkills();
        if (skills.isEmpty()) {
            return "当前没有可用的技能。";
        }

        String skillLines = skills.stream()
                .map(s -> "- **" + s.name() + "** — " + s.description())
                .collect(Collectors.joining("\n"));

        return "可用技能 (" + skills.size() + "):\n\n" + skillLines
                + "\n\n使用 skills(action='invoke', name='技能名') 调用技能获取执行指令。";
    }

    /** 调用技能，返回完整指令内容 */
    private String executeInvoke(Map<String, Object> args) throws ToolException {
        String skillName = (String) args.get("name");
        if (skillName == null || skillName.isBlank()) {
            throw new ToolException("invoke 操作需要 name 参数", "PARAM_MISSING");
        }

        String content = skillsLoader.loadSkill(skillName);
        if (content == null) {
            throw new ToolException("技能 '" + skillName + "' 未找到。使用 'list' 操作查看可用技能。", "SKILL_NOT_FOUND");
        }

        return "<skill-invocation>\n"
                + "<name>" + skillName + "</name>\n"
                + "</skill-invocation>\n\n"
                + content;
    }

    /** 从GitHub仓库安装技能 */
    private String executeInstall(Map<String, Object> args) throws ToolException {
        String repo = (String) args.get("repo");
        if (repo == null || repo.isBlank()) {
            throw new ToolException("install 操作需要 repo 参数（GitHub仓库说明符）", "PARAM_MISSING");
        }
        try {
            String result = skillsInstaller.install(repo);
            return result + "\n技能现已可用，将在下次对话中自动加载。";
        } catch (Exception e) {
            throw new ToolException("安装技能失败: " + e.getMessage(), e);
        }
    }

    /** 删除已安装的技能 */
    private String executeRemove(Map<String, Object> args) throws ToolException {
        String skillName = (String) args.get("name");
        if (skillName == null || skillName.isBlank()) {
            throw new ToolException("remove 操作需要 name 参数", "PARAM_MISSING");
        }
        try {
            return skillsInstaller.remove(skillName);
        } catch (Exception e) {
            throw new ToolException("删除技能失败: " + e.getMessage(), e);
        }
    }
}
