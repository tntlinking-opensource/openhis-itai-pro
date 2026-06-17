package com.openhis.whale.report.application.pdf;

import org.springframework.util.StringUtils;

/**
 * 打印文本行格式化（标签 + 取值）。
 */
public final class PrintTextFormat {

    private PrintTextFormat() {
    }

    public static String formatLabelValue(String label, String valueText, boolean valueOnly) {
        if (!StringUtils.hasText(valueText)) {
            if (StringUtils.hasText(label)) {
                return label;
            }
            return "";
        }
        if (valueOnly || !StringUtils.hasText(label)) {
            return valueText;
        }
        if (label.endsWith("：") || label.endsWith(":")) {
            return label + valueText;
        }
        if (label.contains("：") || label.contains(":")) {
            return label + valueText;
        }
        return label + "：" + valueText;
    }

    /** 同一 kv 块内按最长标签补齐全角空格，便于热敏小票纵向对齐。 */
    public static String padKvLabel(String label, int maxLabelChars) {
        if (!StringUtils.hasText(label) || maxLabelChars <= label.length()) {
            return label;
        }
        return label + "　".repeat(maxLabelChars - label.length());
    }
}
