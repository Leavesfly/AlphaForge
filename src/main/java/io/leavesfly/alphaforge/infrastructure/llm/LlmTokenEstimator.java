package io.leavesfly.alphaforge.infrastructure.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM Token 估算器 — 粗略估算文本/消息的 Token 数量
 *
 * 估算规则：约 4 字符 ≈ 1 token（中英文混合场景的经验值）
 * 当 LLM 供应商未返回 usage 信息时，使用此估算器作为后备。
 */
public class LlmTokenEstimator {

    /** 估算消息列表（Object 类型，含 tool role）的 Token 数 */
    public int estimateMessagesTokens(List<Map<String, Object>> messages) {
        int total = 0;
        for (Map<String, Object> msg : messages) {
            Object content = msg.get("content");
            if (content instanceof String s) {
                total += s.length();
            } else if (content != null) {
                total += content.toString().length();
            }
        }
        return Math.max(1, total / 4);
    }

    /** 估算消息列表（String 类型）的 Token 数 */
    public int estimateMessagesTokensStr(List<Map<String, String>> messages) {
        int total = 0;
        for (Map<String, String> msg : messages) {
            String content = msg.get("content");
            if (content != null) total += content.length();
        }
        return Math.max(1, total / 4);
    }

    /** 估算单个文本的 Token 数 */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, text.length() / 4);
    }
}
