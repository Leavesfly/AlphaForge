package io.leavesfly.stock.application.agent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 技能加载器 - 多层级加载SKILL.md技能文件
 *
 * 参考TinyClaw的SkillsLoader设计，保持轻量级：
 * - 多层级加载：workspace（已安装）> builtin（classpath内置）
 * - 解析YAML frontmatter获取name/description
 * - 生成技能摘要注入system prompt
 *
 * 技能文件格式（SKILL.md）：
 * ---
 * name: skill_name
 * description: 技能描述
 * ---
 * # 技能标题
 * 技能指令内容...
 */
@Component
public class SkillsLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillsLoader.class);

    /** classpath中技能文件的基础路径 */
    private static final String BUILTIN_SKILLS_PATH = "skills/";

    /** 内置技能名称列表（硬编码，解决JAR环境下classpath目录列举问题） */
    private static final List<String> BUILTIN_SKILL_NAMES = List.of(
            "stock_analysis", "market_overview", "backtest",
            "portfolio", "intelligence", "alert"
    );

    /** 技能文件名 */
    private static final String SKILL_FILE = "SKILL.md";

    /** YAML frontmatter正则 */
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("(?s)^---\\n(.*)\\n---");

    /** workspace技能安装目录（{app.home}/data/skills/） */
    private final String workspaceSkillsDir;

    public SkillsLoader() {
        String appHome = System.getProperty("app.home",
                java.nio.file.Paths.get(System.getProperty("user.home"), ".j_daily-stock-analysis").toString());
        this.workspaceSkillsDir = java.nio.file.Paths.get(appHome, "data", "skills").toString();
    }

    /**
     * 列出所有可用技能（workspace > builtin，同名workspace覆盖builtin）
     */
    public List<SkillInfo> listSkills() {
        List<SkillInfo> skills = new ArrayList<>();

        // 1. 先扫描workspace目录（已安装的技能，最高优先级）
        addSkillsFromDir(skills, workspaceSkillsDir, "workspace");

        // 2. 再加载builtin技能（跳过workspace已有的同名技能）
        for (String name : BUILTIN_SKILL_NAMES) {
            boolean exists = skills.stream().anyMatch(s -> s.name().equals(name));
            if (!exists) {
                String content = loadBuiltinSkillContent(name);
                if (content != null) {
                    String frontmatter = extractFrontmatter(content);
                    Map<String, String> yaml = parseSimpleYAML(frontmatter);
                    String description = yaml.getOrDefault("description", "");
                    skills.add(new SkillInfo(name, description, "builtin"));
                }
            }
        }
        log.debug("加载 {} 个技能 (workspace={}, builtin={})",
                skills.size(),
                skills.stream().filter(s -> "workspace".equals(s.source())).count(),
                skills.stream().filter(s -> "builtin".equals(s.source())).count());
        return skills;
    }

    /**
     * 按名称加载技能内容（去除YAML frontmatter后的Markdown指令）
     *
     * @param name 技能名称
     * @return 技能内容，未找到返回null
     */
    public String loadSkill(String name) {
        // 1. 先从workspace加载
        String content = loadSkillFromDir(workspaceSkillsDir, name);
        if (content != null) {
            return content;
        }
        // 2. 再从builtin加载
        content = loadBuiltinSkillContent(name);
        if (content != null) {
            return stripFrontmatter(content);
        }
        return null;
    }

    /**
     * 生成技能摘要（XML格式，供system prompt注入）
     *
     * 格式：
     * <skills>
     *   <skill>
     *     <name>stock_analysis</name>
     *     <description>股票综合分析...</description>
     *   </skill>
     * </skills>
     */
    public String buildSkillsSummary() {
        List<SkillInfo> allSkills = listSkills();
        if (allSkills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<skills>\n");
        for (SkillInfo s : allSkills) {
            sb.append("  <skill>\n");
            sb.append("    <name>").append(escapeXML(s.name())).append("</name>\n");
            sb.append("    <description>").append(escapeXML(s.description())).append("</description>\n");
            sb.append("  </skill>\n");
        }
        sb.append("</skills>");
        return sb.toString();
    }

    // ========== 内部方法 ==========

    /** 从文件系统目录扫描技能 */
    private void addSkillsFromDir(List<SkillInfo> skills, String dirPath, String source) {
        if (dirPath == null) return;
        java.nio.file.Path dir = java.nio.file.Paths.get(dirPath);
        if (!java.nio.file.Files.exists(dir) || !java.nio.file.Files.isDirectory(dir)) return;

        try (var stream = java.nio.file.Files.list(dir)) {
            stream.filter(java.nio.file.Files::isDirectory).forEach(skillDir -> {
                String name = skillDir.getFileName().toString();
                java.nio.file.Path skillFile = skillDir.resolve(SKILL_FILE);
                if (java.nio.file.Files.exists(skillFile)) {
                    String description = parseSkillDescription(skillFile);
                    skills.add(new SkillInfo(name, description, source));
                }
            });
        } catch (IOException e) {
            log.warn("扫描技能目录失败: {} - {}", dirPath, e.getMessage());
        }
    }

    /** 从文件系统目录加载技能内容（去除frontmatter） */
    private String loadSkillFromDir(String dir, String name) {
        if (dir == null) return null;
        java.nio.file.Path skillFile = java.nio.file.Paths.get(dir, name, SKILL_FILE);
        if (!java.nio.file.Files.exists(skillFile)) return null;
        try {
            String content = java.nio.file.Files.readString(skillFile);
            return stripFrontmatter(content);
        } catch (IOException e) {
            log.error("加载技能文件失败: {} - {}", skillFile, e.getMessage());
            return null;
        }
    }

    /** 从SKILL.md解析description */
    private String parseSkillDescription(java.nio.file.Path skillPath) {
        try {
            String content = java.nio.file.Files.readString(skillPath);
            String frontmatter = extractFrontmatter(content);
            Map<String, String> yaml = parseSimpleYAML(frontmatter);
            return yaml.getOrDefault("description", "");
        } catch (IOException e) {
            return "";
        }
    }

    /** 从classpath加载技能文件原始内容 */
    private String loadBuiltinSkillContent(String skillName) {
        String resourcePath = BUILTIN_SKILLS_PATH + skillName + "/" + SKILL_FILE;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("技能文件未找到: {}", resourcePath);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            log.error("加载技能文件失败: {}", resourcePath, e);
            return null;
        }
    }

    /** 提取YAML frontmatter内容 */
    private String extractFrontmatter(String content) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /** 去除YAML frontmatter */
    private String stripFrontmatter(String content) {
        return content.replaceFirst("^---\\n.*?\\n---\\n?", "");
    }

    /** 解析简单YAML（key: value格式） */
    private Map<String, String> parseSimpleYAML(String content) {
        Map<String, String> result = new HashMap<>();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                value = value.replaceAll("^['\"]|['\"]$", "");
                result.put(key, value);
            }
        }
        return result;
    }

    /** 转义XML特殊字符 */
    private String escapeXML(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 技能信息
     *
     * @param name 技能名称
     * @param description 技能描述（用于LLM匹配）
     * @param source 来源（"builtin"）
     */
    public record SkillInfo(String name, String description, String source) {}
}
