package io.leavesfly.alphaforge.application.agent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能安装器 - 从GitHub仓库安装和移除技能
 *
 * 参考TinyClaw的SkillsInstaller设计，保持轻量级：
 * - 支持GitHub仓库克隆（owner/repo、owner/repo/subdir、完整URL）
 * - 使用git clone --depth 1浅克隆
 * - 支持remove删除已安装技能
 * - 防并发重复安装
 *
 * 不引入压缩包URL安装、SSH回退等复杂功能。
 *
 * 技能安装目录：{app.home}/data/skills/{skillName}/
 */
@Component
public class SkillsInstaller {

    private static final Logger log = LoggerFactory.getLogger(SkillsInstaller.class);

    private static final String GITHUB_BASE_URL = "https://github.com/";

    /** 技能安装目录 */
    private final String skillsDir;

    /** 正在安装中的技能，防止并发重复安装 */
    private final Map<String, Boolean> installing = new ConcurrentHashMap<>();

    public SkillsInstaller() {
        String appHome = System.getProperty("app.home",
                Paths.get(System.getProperty("user.home"), ".alphaforge").toString());
        this.skillsDir = Paths.get(appHome, "data", "skills").toString();
    }

    /**
     * 从GitHub仓库安装技能
     *
     * 支持的格式：
     * - owner/repo - 安装整个仓库作为技能
     * - owner/repo/skill-name - 安装仓库中的特定子目录
     * - https://github.com/owner/repo - 完整URL
     *
     * @param specifier 仓库说明符
     * @return 安装结果消息
     * @throws Exception 安装失败
     */
    public String install(String specifier) throws Exception {
        RepoInfo repoInfo = parseRepoSpecifier(specifier);

        if (!isGitAvailable()) {
            throw new Exception("git 命令不可用。请确保已安装 git 并添加到 PATH 环境变量中。");
        }

        String skillName = repoInfo.skillName;

        // 防并发重复安装
        if (installing.putIfAbsent(skillName, true) != null) {
            throw new Exception("技能 '" + skillName + "' 正在安装中，请稍候...");
        }

        try {
            Path targetPath = Paths.get(skillsDir, skillName);
            if (Files.exists(targetPath)) {
                throw new Exception("技能 '" + skillName + "' 已存在。请先使用 remove 删除后再安装。");
            }

            // 创建安装目录
            Files.createDirectories(Paths.get(skillsDir));

            // 浅克隆到临时目录
            Path tempDir = Files.createTempDirectory("j-daily-skill-");
            try {
                log.info("从GitHub安装技能: {} → {}", repoInfo.repoUrl, skillName);
                cloneRepository(repoInfo.repoUrl, tempDir.toString());

                // 确定技能源目录
                Path sourceDir = (repoInfo.subdir != null && !repoInfo.subdir.isEmpty())
                        ? tempDir.resolve(repoInfo.subdir)
                        : tempDir;

                if (!Files.exists(sourceDir.resolve("SKILL.md"))) {
                    throw new Exception("仓库中未找到 SKILL.md 文件。请确保这是一个有效的技能仓库。");
                }

                // 复制到目标目录（跳过.git）
                copyDirectory(sourceDir, targetPath);

                log.info("技能安装成功: {} → {}", skillName, targetPath);
                return "✓ 技能 '" + skillName + "' 安装成功！来源: " + repoInfo.repoUrl;
            } finally {
                deleteDirectory(tempDir);
            }
        } finally {
            installing.remove(skillName);
        }
    }

    /**
     * 删除已安装的技能
     *
     * 只能删除workspace中安装的技能，不能删除内置技能。
     *
     * @param skillName 技能名称
     * @return 删除结果消息
     * @throws Exception 删除失败
     */
    public String remove(String skillName) throws Exception {
        Path skillDir = Paths.get(skillsDir, skillName);
        if (!Files.exists(skillDir)) {
            throw new Exception("技能 '" + skillName + "' 未在已安装技能中找到。只能删除通过 install 安装的技能。");
        }

        deleteDirectory(skillDir);
        log.info("技能已删除: {}", skillName);
        return "✓ 技能 '" + skillName + "' 已成功删除。";
    }

    /** 获取技能安装目录路径 */
    public String getSkillsDir() {
        return skillsDir;
    }

    // ========== 内部方法 ==========

    /** 解析仓库说明符 */
    private RepoInfo parseRepoSpecifier(String specifier) throws Exception {
        if (specifier == null || specifier.trim().isEmpty()) {
            throw new Exception("仓库说明符不能为空");
        }
        specifier = specifier.trim();

        RepoInfo info = new RepoInfo();

        if (specifier.startsWith("https://") || specifier.startsWith("http://")) {
            // 完整URL: https://github.com/owner/repo
            info.repoUrl = specifier;
            String path = specifier.replaceFirst("^https?://github\\.com/", "");
            String[] parts = path.split("/");
            if (parts.length < 2) {
                throw new Exception("无效的GitHub URL格式: " + specifier);
            }
            info.skillName = parts[1].replace(".git", "");
        } else {
            // 简短格式: owner/repo 或 owner/repo/subdir
            String[] parts = specifier.split("/");
            if (parts.length < 2) {
                throw new Exception("无效的仓库格式。使用格式: owner/repo 或 owner/repo/skill-name");
            }
            info.repoUrl = GITHUB_BASE_URL + parts[0] + "/" + parts[1];
            info.skillName = parts.length >= 3 ? parts[2] : parts[1];
            info.subdir = parts.length >= 3 ? parts[2] : null;
        }

        return info;
    }

    /** 检查git是否可用 */
    private boolean isGitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 浅克隆仓库 */
    private void cloneRepository(String repoUrl, String targetDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "git", "clone", "--depth", "1", repoUrl, targetDir
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String error = output.toString();
            if (error.contains("not found") || error.contains("404")) {
                throw new Exception("仓库不存在或无访问权限: " + repoUrl);
            } else if (error.contains("Authentication failed")) {
                throw new Exception("认证失败。请检查仓库访问权限。");
            }
            throw new Exception("克隆仓库失败: " + error.trim());
        }
    }

    /** 递归复制目录（跳过.git） */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
                .filter(path -> {
                    Path relative = source.relativize(path);
                    for (Path component : relative) {
                        if (".git".equals(component.toString())) {
                            return false;
                        }
                    }
                    return true;
                })
                .forEach(sourcePath -> {
                    try {
                        Path relativePath = source.relativize(sourcePath);
                        Path targetPath = target.resolve(relativePath);
                        if (Files.isDirectory(sourcePath)) {
                            Files.createDirectories(targetPath);
                        } else {
                            Files.copy(sourcePath, targetPath);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("复制文件失败: " + e.getMessage(), e);
                    }
                });
    }

    /** 递归删除目录 */
    private void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) return;
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("删除文件失败: {} - {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("删除目录失败: {} - {}", dir, e.getMessage());
        }
    }

    /** 仓库信息 */
    private static class RepoInfo {
        String repoUrl;
        String skillName;
        String subdir;
    }
}

