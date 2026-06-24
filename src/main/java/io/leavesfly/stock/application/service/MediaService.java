package io.leavesfly.stock.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * 图片股票提取器 + Markdown转图片 + 飞书文档
 * 对应Python: image_stock_extractor.py + md2img.py + feishu_doc.py
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    /** 从图片识别文本中提取股票代码 */
    public List<String> extractStockCodesFromText(String text) {
        List<String> codes = new ArrayList<>();
        if (text == null || text.isEmpty()) return codes;
        // A股6位代码
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(text);
        while (m.find()) {
            String code = m.group(1);
            if (code.startsWith("6") || code.startsWith("0") || code.startsWith("3")) codes.add(code);
        }
        // 美股代码
        Matcher us = Pattern.compile("\\b([A-Z]{1,5})\\b").matcher(text);
        while (us.find()) {
            String code = us.group(1);
            if (code.length() >= 2 && code.length() <= 5 && !isCommonWord(code)) codes.add(code);
        }
        return codes;
    }

    /** Markdown转HTML(简化版，用于图片渲染前的预处理) */
    public String markdownToHtml(String markdown) {
        if (markdown == null) return "";
        String html = markdown;
        html = html.replaceAll("### (.+)", "<h3>$1</h3>");
        html = html.replaceAll("## (.+)", "<h2>$1</h2>");
        html = html.replaceAll("# (.+)", "<h1>$1</h1>");
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        html = html.replaceAll("- (.+)", "<li>$1</li>");
        html = html.replaceAll("\n", "<br/>");
        return "<div style='font-family:sans-serif;padding:20px'>" + html + "</div>";
    }

    /** 构建飞书文档内容块 */
    public Map<String, Object> buildFeishuDocContent(String title, String markdownContent) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", title);
        List<Map<String, Object>> blocks = new ArrayList<>();
        // 标题块
        blocks.add(Map.of("block_type", "heading1", "content", title));
        // 内容块(按段落分割)
        String[] paragraphs = markdownContent.split("\n\n");
        for (String p : paragraphs) {
            if (p.startsWith("#")) {
                blocks.add(Map.of("block_type", "heading2", "content", p.replaceAll("^#+\\s*", "")));
            } else {
                blocks.add(Map.of("block_type", "text", "content", p));
            }
        }
        doc.put("blocks", blocks);
        return doc;
    }

    private boolean isCommonWord(String word) {
        Set<String> common = Set.of("THE", "AND", "FOR", "NOT", "ARE", "BUT", "ALL", "CAN", "HER", "WAS");
        return common.contains(word);
    }
}
