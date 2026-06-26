package com.openhis.whale.report.domain.support;

/**
 * 报表模块纯文本归一（无 Spring 依赖）。
 */
public final class ReportTextSupport {

    private ReportTextSupport() {
    }

    /**
     * null 转为空串，供排序键与占位符使用。
     *
     * @param value 原始文本
     * @return 非 null 字符串
     */
    public static String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    /**
     * 将对象转为展示字符串；null 显示为破折号。
     *
     * @param value SQL 单元格值
     * @return 非 null 展示文本
     */
    public static String displayCell(Object value) {
        if (value == null) {
            return "—";
        }
        return String.valueOf(value);
    }
}
