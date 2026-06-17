package com.openhis.whale.report.contracts.prescription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 门诊处方药品明细行：输液成组展示（组内括线标记、组末行保留用法/途径/频次）。
 */
public final class PrescriptionDrugLineGroupSupport {

    private PrescriptionDrugLineGroupSupport() {
    }

    /**
     * 为已构建的明细行补充成组元数据并调整组内用法列展示。
     *
     * @param lines  按处方顺序排列的明细行（每行须含 {@code groupId}，未成组为 null）
     * @return 新列表（不修改入参 Map）
     */
    public static List<Map<String, Object>> applyGrouping(List<Map<String, Object>> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> groupCounts = countByGroupId(lines);
        List<Map<String, Object>> result = new ArrayList<>(lines.size());
        Map<Long, Integer> groupIndex = new LinkedHashMap<>();
        for (Map<String, Object> source : lines) {
            Long groupId = asLong(source.get("groupId"));
            int groupLineCount = resolveGroupLineCount(groupId, groupCounts);
            int groupLineIndex = nextGroupLineIndex(groupIndex, groupId);
            result.add(buildGroupedLine(source, groupId, groupLineIndex, groupLineCount));
        }
        return result;
    }

    private static Map<Long, Integer> countByGroupId(List<Map<String, Object>> lines) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> line : lines) {
            Long groupId = asLong(line.get("groupId"));
            if (groupId == null) {
                continue;
            }
            counts.merge(groupId, 1, Integer::sum);
        }
        return counts;
    }

    private static int resolveGroupLineCount(Long groupId, Map<Long, Integer> groupCounts) {
        if (groupId == null) {
            return 1;
        }
        return Math.max(1, groupCounts.getOrDefault(groupId, 1));
    }

    private static int nextGroupLineIndex(Map<Long, Integer> groupIndex, Long groupId) {
        if (groupId == null) {
            return 1;
        }
        int next = groupIndex.getOrDefault(groupId, 0) + 1;
        groupIndex.put(groupId, next);
        return next;
    }

    private static Map<String, Object> buildGroupedLine(
            Map<String, Object> source,
            Long groupId,
            int groupLineIndex,
            int groupLineCount) {
        Map<String, Object> line = new LinkedHashMap<>(source);
        boolean infusionGroup = groupId != null && groupLineCount > 1;
        line.put("groupId", groupId);
        line.put("groupLineIndex", groupLineIndex);
        line.put("groupLineCount", groupLineCount);
        line.put("infusionGroup", infusionGroup);
        line.put("groupMarker", resolveGroupMarker(infusionGroup, groupLineIndex, groupLineCount));
        if (infusionGroup && groupLineIndex < groupLineCount) {
            line.put("usageText", "");
            line.put("methodText", "");
            line.put("rateText", "");
        }
        return line;
    }

    private static String resolveGroupMarker(boolean infusionGroup, int groupLineIndex, int groupLineCount) {
        if (!infusionGroup) {
            return "";
        }
        if (groupLineCount == 1) {
            return "";
        }
        if (groupLineIndex == 1) {
            return "┌";
        }
        if (groupLineIndex >= groupLineCount) {
            return "└";
        }
        return "├";
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 构建单行明细（未成组字段由 {@link #applyGrouping} 补充）。
     */
    public static Map<String, Object> baseLine(
            int lineNo,
            Long groupId,
            String drugName,
            String spec,
            String quantity,
            String unitText,
            String usageText,
            String methodText,
            String rateText,
            String skinTestText) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lineNo", lineNo);
        row.put("groupId", groupId);
        row.put("drugName", Objects.toString(drugName, ""));
        row.put("spec", Objects.toString(spec, ""));
        row.put("quantity", Objects.toString(quantity, ""));
        row.put("unitText", Objects.toString(unitText, ""));
        row.put("usageText", Objects.toString(usageText, ""));
        row.put("methodText", Objects.toString(methodText, ""));
        row.put("rateText", Objects.toString(rateText, ""));
        row.put("skinTestText", Objects.toString(skinTestText, ""));
        return row;
    }
}
