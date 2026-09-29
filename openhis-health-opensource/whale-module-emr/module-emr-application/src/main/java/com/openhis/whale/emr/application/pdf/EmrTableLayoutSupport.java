/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.util.StringUtils;

/**
 * 表格布局 attrs 回放（09 §5.1）。
 * 有 colWidths / minHeightMm 等则按 attrs 画；缺省时调用方走角色目录。
 * 本类不出现表名字符串，不按格子汉字猜行高。
 */
public final class EmrTableLayoutSupport {

    private static final String ATTRS = "attrs";

    private EmrTableLayoutSupport() {
    }

    /**
     * 表级布局。colWidths 与列数不等长时视为无效，交给调用方回退。
     *
     * @param colWidths     相对份额，与列数等长；无效为 null
     * @param border        true/false；模板没写则为 null
     * @param splitLate     true=行能放下就整行走
     * @param hasFillRemain 任一格带了 fillRemainShare
     */
    public record TableLayout(float[] colWidths, Boolean border, Boolean splitLate, boolean hasFillRemain) {
    }

    /**
     * 格级布局。未写的字段为 null，调用方不要用 0 当「没写」。
     *
     * @param minHeightMm     最小格高（mm）
     * @param paddingMm       上右下左（mm）；未写为 null
     * @param align           left/center/right
     * @param valign          top/middle/bottom
     * @param wrap            true=按列宽折行
     * @param nowrap          true=落款行不折
     * @param fillRemainShare 本页剩余高度份额
     */
    public record CellLayout(
            Float minHeightMm,
            float[] paddingMm,
            String align,
            String valign,
            Boolean wrap,
            Boolean nowrap,
            Float fillRemainShare) {
    }

    /**
     * 读 table.attrs。
     *
     * @param tableNode  table JSON
     * @param colCount   实际列数
     * @return 解析结果，不会为 null
     */
    public static TableLayout parseTable(JsonNode tableNode, int colCount) {
        float[] widths = readColWidths(tableNode, colCount);
        Boolean border = readOptionalBool(tableNode, "border");
        Boolean splitLate = readOptionalBool(tableNode, "splitLate");
        boolean hasFill = false;
        if (tableNode != null && tableNode.has("content") && tableNode.get("content").isArray()) {
            hasFill = anyFillRemainShare(tableNode.get("content"));
        }
        return new TableLayout(widths, border, splitLate, hasFill);
    }

    /**
     * 读 tableCell / tableHeader attrs。
     *
     * @param cell 单元格 JSON
     * @return 解析结果，不会为 null
     */
    public static CellLayout parseCell(JsonNode cell) {
        return new CellLayout(
                readPositiveFloat(cell, "minHeightMm"),
                readPaddingMm(cell),
                readAlignToken(cell, "align"),
                readValignToken(cell, "valign"),
                readOptionalBool(cell, "wrap"),
                readOptionalBool(cell, "nowrap"),
                readPositiveFloat(cell, "fillRemainShare"));
    }

    /**
     * 格上是否写了任意协议字段。有则调用方不再走表名毫米特判。
     *
     * @param layout 格布局
     * @return 写过则为 true
     */
    public static boolean hasCellLayout(CellLayout layout) {
        if (layout == null) {
            return false;
        }
        // 任一协议字段存在即视为新模板格
        return layout.minHeightMm() != null
                || layout.paddingMm() != null
                || StringUtils.hasText(layout.align())
                || StringUtils.hasText(layout.valign())
                || layout.wrap() != null
                || layout.nowrap() != null
                || layout.fillRemainShare() != null;
    }

    /**
     * 把 colWidths 写到 PdfPTable。失败返回 false，调用方走旧特判。
     *
     * @param table   目标表
     * @param layout  表布局
     * @return 已 setWidths 则为 true
     */
    public static boolean applyColumnWidths(PdfPTable table, TableLayout layout) {
        if (table == null || layout == null || layout.colWidths() == null) {
            return false;
        }
        try {
            table.setWidths(layout.colWidths());
            table.setWidthPercentage(100f);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 把格 attrs 写到 PdfPCell。fillRemainPt 优先于 minHeightMm（空单铺满本页）。
     *
     * @param pdfCell      目标格
     * @param layout       格布局
     * @param fillRemainPt 本页按份额算好的最小高度（pt）；无则为 null
     */
    public static void applyCell(PdfPCell pdfCell, CellLayout layout, Float fillRemainPt) {
        if (pdfCell == null || layout == null) {
            return;
        }
        if (layout.minHeightMm() != null) {
            pdfCell.setMinimumHeight(PdfUnitUtils.mmToPt(layout.minHeightMm()));
        }
        if (fillRemainPt != null && fillRemainPt > 0f) {
            pdfCell.setMinimumHeight(fillRemainPt);
        }
        if (layout.paddingMm() != null && layout.paddingMm().length == 4) {
            pdfCell.setPaddingTop(PdfUnitUtils.mmToPt(layout.paddingMm()[0]));
            pdfCell.setPaddingRight(PdfUnitUtils.mmToPt(layout.paddingMm()[1]));
            pdfCell.setPaddingBottom(PdfUnitUtils.mmToPt(layout.paddingMm()[2]));
            pdfCell.setPaddingLeft(PdfUnitUtils.mmToPt(layout.paddingMm()[3]));
        }
        int hAlign = toHorizontalAlign(layout.align());
        if (hAlign >= 0) {
            pdfCell.setHorizontalAlignment(hAlign);
        }
        int vAlign = toVerticalAlign(layout.valign());
        if (vAlign >= 0) {
            pdfCell.setVerticalAlignment(vAlign);
        }
        if (layout.nowrap() != null && layout.nowrap()) {
            // 居中不能 setNoWrap：OpenPDF 会把排版宽度左右各扩 10000pt 再居中，字画到格子外，打印是空格。
            if (hAlign != Element.ALIGN_CENTER) {
                pdfCell.setNoWrap(true);
            }
        } else if (layout.wrap() != null && layout.wrap()) {
            pdfCell.setNoWrap(false);
        }
    }

    /**
     * 无框：attrs.border=false。
     *
     * @param layout 表布局
     * @return 明确写了 false 则为 true
     */
    public static boolean isBorderless(TableLayout layout) {
        return layout != null && layout.border() != null && !layout.border();
    }

    /**
     * 整行切页：attrs.splitLate=true，或表内有 fillRemainShare。
     *
     * @param layout 表布局
     * @return 需要 setSplitLate(true)
     */
    public static boolean shouldSplitLate(TableLayout layout) {
        if (layout == null) {
            return false;
        }
        if (layout.splitLate() != null && layout.splitLate()) {
            return true;
        }
        return layout.hasFillRemain();
    }

    /**
     * 读 colWidths。% 与裸数字都当相对份额；带 px 的丢掉整组。
     *
     * @param tableNode table JSON
     * @param colCount  列数
     * @return 与列数等长的份额；无效为 null
     */
    static float[] readColWidths(JsonNode tableNode, int colCount) {
        if (colCount <= 0) {
            return null;
        }
        JsonNode raw = attrNode(tableNode, "colWidths");
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return null;
        }
        float[] values;
        if (raw.isArray()) {
            values = new float[raw.size()];
            for (int i = 0; i < raw.size(); i++) {
                Float token = parseWidthToken(raw.get(i));
                if (token == null || token <= 0f) {
                    return null;
                }
                values[i] = token;
            }
        } else if (raw.isTextual()) {
            String text = raw.asText("");
            if (!StringUtils.hasText(text)) {
                return null;
            }
            String[] parts = text.split("[,\\s]+");
            values = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                Float token = parseWidthTokenText(parts[i]);
                if (token == null || token <= 0f) {
                    return null;
                }
                values[i] = token;
            }
        } else {
            return null;
        }
        if (values.length != colCount) {
            return null;
        }
        return values;
    }

    /**
     * 行数组里是否有 fillRemainShare 格。
     *
     * @param rows table.content
     * @return 有则为 true
     */
    static boolean anyFillRemainShare(JsonNode rows) {
        if (rows == null || !rows.isArray()) {
            return false;
        }
        for (JsonNode row : rows) {
            JsonNode cells = row.get("content");
            if (cells == null || !cells.isArray()) {
                continue;
            }
            for (JsonNode cell : cells) {
                if (readPositiveFloat(cell, "fillRemainShare") != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 行内 fillRemainShare（取该行最大正数）。无则为 null。
     *
     * @param row tableRow
     * @return 份额
     */
    public static Float rowFillRemainShare(JsonNode row) {
        if (row == null || !row.has("content") || !row.get("content").isArray()) {
            return null;
        }
        Float max = null;
        for (JsonNode cell : row.get("content")) {
            Float share = readPositiveFloat(cell, "fillRemainShare");
            if (share == null) {
                continue;
            }
            if (max == null || share > max) {
                max = share;
            }
        }
        return max;
    }

    /**
     * 行内最小高度（mm）：取该行最大 minHeightMm，供扣非书写行。
     *
     * @param row tableRow
     * @return mm；无则为 null
     */
    public static Float rowMinHeightMm(JsonNode row) {
        if (row == null || !row.has("content") || !row.get("content").isArray()) {
            return null;
        }
        Float max = null;
        for (JsonNode cell : row.get("content")) {
            Float mm = readPositiveFloat(cell, "minHeightMm");
            if (mm == null) {
                continue;
            }
            if (max == null || mm > max) {
                max = mm;
            }
        }
        return max;
    }

    /**
     * 取节点 attrs 下的字段；没有则 null。
     *
     * @param node JSON 节点
     * @param key  attrs 键
     * @return 字段节点
     */
    private static JsonNode attrNode(JsonNode node, String key) {
        if (node == null || !node.has(ATTRS)) {
            return null;
        }
        JsonNode attrs = node.get(ATTRS);
        if (attrs == null || !attrs.has(key)) {
            return null;
        }
        return attrs.get(key);
    }

    /**
     * 读正数 float attrs；未写或非正数为 null。
     *
     * @param node JSON 节点
     * @param key  attrs 键
     * @return 正数
     */
    private static Float readPositiveFloat(JsonNode node, String key) {
        JsonNode raw = attrNode(node, key);
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return null;
        }
        if (raw.isNumber()) {
            float value = raw.floatValue();
            if (value > 0f) {
                return value;
            }
            return null;
        }
        if (raw.isTextual()) {
            try {
                float value = Float.parseFloat(raw.asText("").trim());
                if (value > 0f) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 读可选布尔。模板没写该字段时返回 null，不要当成 false。
     *
     * @param node JSON 节点
     * @param key  attrs 键
     * @return true/false/null
     */
    private static Boolean readOptionalBool(JsonNode node, String key) {
        JsonNode raw = attrNode(node, key);
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return null;
        }
        if (raw.isBoolean()) {
            return raw.booleanValue();
        }
        if (raw.isTextual()) {
            String text = raw.asText("").trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        if (raw.isNumber()) {
            return raw.intValue() != 0;
        }
        return null;
    }

    /**
     * 读水平对齐 token。
     *
     * @param node JSON 节点
     * @param key  attrs 键
     * @return left/center/right
     */
    private static String readAlignToken(JsonNode node, String key) {
        JsonNode raw = attrNode(node, key);
        if (raw == null || !raw.isTextual()) {
            return null;
        }
        String text = raw.asText("").trim().toLowerCase();
        if ("left".equals(text) || "center".equals(text) || "right".equals(text)) {
            return text;
        }
        return null;
    }

    /**
     * 读垂直对齐 token。
     *
     * @param node JSON 节点
     * @param key  attrs 键
     * @return top/middle/bottom
     */
    private static String readValignToken(JsonNode node, String key) {
        JsonNode raw = attrNode(node, key);
        if (raw == null || !raw.isTextual()) {
            return null;
        }
        String text = raw.asText("").trim().toLowerCase();
        if ("top".equals(text) || "middle".equals(text) || "bottom".equals(text)) {
            return text;
        }
        return null;
    }

    /**
     * 读 paddingMm：单值、1～4 元数组或 {top,right,bottom,left}。
     *
     * @param node 单元格 JSON
     * @return 上右下左（mm）
     */
    private static float[] readPaddingMm(JsonNode node) {
        JsonNode raw = attrNode(node, "paddingMm");
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return null;
        }
        if (raw.isNumber()) {
            float v = raw.floatValue();
            if (v < 0f) {
                return null;
            }
            return new float[]{v, v, v, v};
        }
        if (raw.isArray()) {
            int n = raw.size();
            if (n == 1) {
                float v = raw.get(0).floatValue();
                return new float[]{v, v, v, v};
            }
            if (n == 2) {
                float a = raw.get(0).floatValue();
                float b = raw.get(1).floatValue();
                return new float[]{a, b, a, b};
            }
            if (n == 3) {
                return new float[]{
                        raw.get(0).floatValue(),
                        raw.get(1).floatValue(),
                        raw.get(2).floatValue(),
                        raw.get(1).floatValue()
                };
            }
            if (n >= 4) {
                return new float[]{
                        raw.get(0).floatValue(),
                        raw.get(1).floatValue(),
                        raw.get(2).floatValue(),
                        raw.get(3).floatValue()
                };
            }
            return null;
        }
        if (raw.isObject()) {
            float top = raw.path("top").floatValue();
            float right = raw.path("right").floatValue();
            float bottom = raw.path("bottom").floatValue();
            float left = raw.path("left").floatValue();
            return new float[]{top, right, bottom, left};
        }
        return null;
    }

    /**
     * 列宽 JSON token → 份额。px 无效。
     *
     * @param token 数字或文本
     * @return 正数份额
     */
    private static Float parseWidthToken(JsonNode token) {
        if (token == null || token.isNull()) {
            return null;
        }
        if (token.isNumber()) {
            return token.floatValue();
        }
        if (token.isTextual()) {
            return parseWidthTokenText(token.asText(""));
        }
        return null;
    }

    /**
     * 列宽文本 → 份额。允许 13% / 1.3；禁止 px。
     *
     * @param raw 文本
     * @return 正数份额
     */
    private static Float parseWidthTokenText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim().toLowerCase();
        if (text.endsWith("px")) {
            return null;
        }
        boolean percent = text.endsWith("%");
        String num = text;
        if (percent) {
            num = text.substring(0, text.length() - 1).trim();
        }
        try {
            float value = Float.parseFloat(num);
            if (value > 0f) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    /**
     * 水平对齐 → OpenPDF 常量；未识别返回 -1。
     *
     * @param align left/center/right
     * @return ALIGN_* 或 -1
     */
    private static int toHorizontalAlign(String align) {
        if (!StringUtils.hasText(align)) {
            return -1;
        }
        if ("center".equals(align)) {
            return Element.ALIGN_CENTER;
        }
        if ("right".equals(align)) {
            return Element.ALIGN_RIGHT;
        }
        if ("left".equals(align)) {
            return Element.ALIGN_LEFT;
        }
        return -1;
    }

    /**
     * 垂直对齐 → OpenPDF 常量；未识别返回 -1。
     *
     * @param valign top/middle/bottom
     * @return ALIGN_* 或 -1
     */
    private static int toVerticalAlign(String valign) {
        if (!StringUtils.hasText(valign)) {
            return -1;
        }
        if ("top".equals(valign)) {
            return Element.ALIGN_TOP;
        }
        if ("bottom".equals(valign)) {
            return Element.ALIGN_BOTTOM;
        }
        if ("middle".equals(valign)) {
            return Element.ALIGN_MIDDLE;
        }
        return -1;
    }
}
