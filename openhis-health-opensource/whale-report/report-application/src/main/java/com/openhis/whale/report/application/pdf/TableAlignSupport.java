package com.openhis.whale.report.application.pdf;

import com.lowagie.text.Element;
import com.openhis.whale.report.application.layout.model.ColumnDef;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 表格列水平对齐与列宽分配（flow / overlay 共用）。
 */
public final class TableAlignSupport {

    private TableAlignSupport() {
    }

    public static int toPdfAlign(String align) {
        if (!StringUtils.hasText(align)) {
            return Element.ALIGN_LEFT;
        }
        return switch (align.trim().toLowerCase()) {
            case "center", "centre" -> Element.ALIGN_CENTER;
            case "right" -> Element.ALIGN_RIGHT;
            default -> Element.ALIGN_LEFT;
        };
    }

    public static String resolveTableDefaultAlign(LayoutItem item) {
        if (item != null && item.getStyle() != null && StringUtils.hasText(item.getStyle().getAlign())) {
            return item.getStyle().getAlign().trim();
        }
        return "left";
    }

    public static int resolveColumnPdfAlign(LayoutItem item, ColumnDef column, String tableDefaultAlign) {
        return toPdfAlign(resolveColumnAlignText(item, column, tableDefaultAlign));
    }

    public static int resolveColumnPdfAlign(LayoutItem item, ColumnDef column) {
        return resolveColumnPdfAlign(item, column, resolveTableDefaultAlign(item));
    }

    public static String resolveColumnAlignText(LayoutItem item, ColumnDef column, String tableDefaultAlign) {
        if (item != null && item.getColumnAligns() != null && column != null && column.getField() != null) {
            String override = item.getColumnAligns().get(column.getField());
            if (StringUtils.hasText(override)) {
                return override.trim();
            }
        }
        if (column != null && StringUtils.hasText(column.getAlign())) {
            return column.getAlign().trim();
        }
        if (column != null && StringUtils.hasText(column.getField())) {
            String heuristic = heuristicAlign(column.getField());
            if (heuristic != null) {
                return heuristic;
            }
        }
        return StringUtils.hasText(tableDefaultAlign) ? tableDefaultAlign : "left";
    }

    private static String heuristicAlign(String field) {
        String f = field.trim();
        if ("lineNo".equalsIgnoreCase(f) || "groupMarker".equalsIgnoreCase(f)) {
            return "center";
        }
        if (f.contains("quantity") || f.contains("Amount") || f.contains("Price")) {
            return "right";
        }
        return null;
    }

    /**
     * 将行宽（pt）按列定义拆分为各列宽度；无 widthMm 时等分。
     */
    public static float[] resolveColumnWidthPoints(List<ColumnDef> columns, float totalWidthPt) {
        if (columns == null || columns.isEmpty() || totalWidthPt <= 0f) {
            return new float[0];
        }
        int count = columns.size();
        float[] widths = new float[count];
        float[] weights = new float[count];
        float weightSum = 0f;
        int missing = 0;
        for (int i = 0; i < count; i++) {
            Float widthMm = columns.get(i).getWidthMm();
            if (widthMm != null && widthMm > 0f) {
                weights[i] = widthMm;
                weightSum += widthMm;
            } else {
                missing++;
            }
        }
        if (weightSum <= 0f) {
            float each = totalWidthPt / count;
            for (int i = 0; i < count; i++) {
                widths[i] = each;
            }
            return widths;
        }
        float fallbackWeight = weightSum / Math.max(1, count - missing);
        for (int i = 0; i < count; i++) {
            if (weights[i] <= 0f) {
                weights[i] = fallbackWeight;
                weightSum += fallbackWeight;
            }
        }
        for (int i = 0; i < count; i++) {
            widths[i] = totalWidthPt * (weights[i] / weightSum);
        }
        return widths;
    }

    /**
     * flow 表格相对列宽（供 PdfPTable.setWidths）。
     */
    public static float[] resolveRelativeColumnWidths(List<ColumnDef> columns) {
        if (columns == null || columns.isEmpty()) {
            return new float[0];
        }
        int count = columns.size();
        float[] weights = new float[count];
        float sum = 0f;
        int missing = 0;
        for (int i = 0; i < count; i++) {
            Float widthMm = columns.get(i).getWidthMm();
            if (widthMm != null && widthMm > 0f) {
                weights[i] = widthMm;
                sum += widthMm;
            } else {
                missing++;
            }
        }
        if (sum <= 0f) {
            for (int i = 0; i < count; i++) {
                weights[i] = 1f;
            }
            return weights;
        }
        float fallback = sum / Math.max(1, count - missing);
        for (int i = 0; i < count; i++) {
            if (weights[i] <= 0f) {
                weights[i] = fallback;
            }
        }
        return weights;
    }
}
