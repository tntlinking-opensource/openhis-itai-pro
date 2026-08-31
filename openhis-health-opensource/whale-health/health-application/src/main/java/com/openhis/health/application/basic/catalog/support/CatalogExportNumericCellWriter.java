/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.catalog.support;

import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import org.apache.poi.ss.usermodel.Cell;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目录（药品/耗材/诊疗）导出单元格写出：指定列写数字类型（可求和），其余仍写文本。
 *
 * <p>背景：目录导出与导入模板同列序，历史一律 {@code setCellValue(String)} 导致价格/倍率无法求和。
 * 导入解析已支持 NUMERIC（{@code DataFormatter}），故导出改数字不影响回导。</p>
 *
 * <p>编号等文本列<strong>禁止</strong>纳入 numericCols，避免长编码被 Excel 科学计数法显示。
 * 列下标须由 {@link #resolveExportNumericColumnIndexes} 按表头名解析，禁止业务类硬编码魔法下标。</p>
 *
 * <p>前端 {@link ExcelExportColumnDto} 按表头名匹配 {@code field}/{@code title}；空清单导出全量列。</p>
 *
 * @see com.openhis.whale.core.application.util.ExcelCellWriter 报表注解导出共用同类规则
 */
public final class CatalogExportNumericCellWriter {

    /** 导出首列「编号」相对导入表头的列偏移。 */
    private static final int EXPORT_BUS_NO_COLUMN_OFFSET = 1;

    private CatalogExportNumericCellWriter() {
    }

    /**
     * 按导入表头名解析导出 NUMERIC 列下标（含编号列偏移）。
     *
     * @param importColumnHeaders      导入 {@code COLUMN_HEADERS}（不含编号）
     * @param exportNumericHeaderNames 须写数字的表头名（如购入价）；来自各目录 ImportConstants
     * @return 不可变下标集合；找不到的表头名跳过
     */
    public static Set<Integer> resolveExportNumericColumnIndexes(
            String[] importColumnHeaders,
            String[] exportNumericHeaderNames) {
        if (importColumnHeaders == null || exportNumericHeaderNames == null) {
            return Set.of();
        }
        Set<Integer> indexes = new HashSet<>();
        for (String headerName : exportNumericHeaderNames) {
            // 空表头名忽略
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            int importIndex = indexOfHeader(importColumnHeaders, headerName);
            // 表头未命中：跳过，避免写错列
            if (importIndex < 0) {
                continue;
            }
            indexes.add(importIndex + EXPORT_BUS_NO_COLUMN_OFFSET);
        }
        return Collections.unmodifiableSet(indexes);
    }

    /**
     * 按前端列清单解析导出列下标；null/空清单返回 null 表示导出全量。
     *
     * <p>{@code field} 或 {@code title} 与导出表头精确匹配（trim）；清单全部非法时回落 null（全量）。</p>
     *
     * @param exportHeaders     全量导出表头（含编号）
     * @param requestedColumns  前端自选列
     * @return 选中的原列下标（有序）；null 表示不裁剪
     */
    public static List<Integer> resolveSelectedExportColumnIndexes(
            String[] exportHeaders,
            List<ExcelExportColumnDto> requestedColumns) {
        if (exportHeaders == null || exportHeaders.length == 0) {
            return null;
        }
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            return null;
        }
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i < exportHeaders.length; i++) {
            String header = trimToNull(exportHeaders[i]);
            if (header == null) {
                continue;
            }
            headerIndex.put(header, Integer.valueOf(i));
        }
        List<Integer> selected = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (ExcelExportColumnDto request : requestedColumns) {
            if (request == null) {
                continue;
            }
            Integer index = null;
            String field = trimToNull(request.getField());
            if (field != null) {
                index = headerIndex.get(field);
            }
            // field 未命中时再试 title（目录页可能只传中文表头）
            if (index == null) {
                String title = trimToNull(request.getTitle());
                if (title != null) {
                    index = headerIndex.get(title);
                }
            }
            if (index == null) {
                continue;
            }
            // 去重，保持前端顺序
            if (seen.add(index)) {
                selected.add(index);
            }
        }
        // 清单全部非法：回落全量，避免空表头
        if (selected.isEmpty()) {
            return null;
        }
        return selected;
    }

    /**
     * 将全量表头投影为自选列；有 title 时覆盖表头文案。
     *
     * @param exportHeaders     全量表头
     * @param selectedIndexes   选中原下标；null 表示全量
     * @param requestedColumns  前端列（用于 title 覆盖；可与 selected 顺序对齐）
     * @return 投影后表头
     */
    public static String[] projectExportHeaders(
            String[] exportHeaders,
            List<Integer> selectedIndexes,
            List<ExcelExportColumnDto> requestedColumns) {
        if (selectedIndexes == null) {
            return exportHeaders;
        }
        String[] projected = new String[selectedIndexes.size()];
        for (int i = 0; i < selectedIndexes.size(); i++) {
            int originalIndex = selectedIndexes.get(i).intValue();
            String header = exportHeaders[originalIndex];
            // 按同序请求项覆盖 title（若有）
            if (requestedColumns != null && i < requestedColumns.size()) {
                ExcelExportColumnDto request = requestedColumns.get(i);
                if (request != null) {
                    String titleOverride = trimToNull(request.getTitle());
                    if (titleOverride != null) {
                        header = titleOverride;
                    }
                }
            }
            projected[i] = header;
        }
        return projected;
    }

    /**
     * 将全量行值投影到自选列。
     *
     * @param fullValues      全量行
     * @param selectedIndexes 选中原下标；null 表示不投影
     * @return 投影行；selectedIndexes 为 null 时返回原数组
     */
    public static String[] projectExportRowValues(String[] fullValues, List<Integer> selectedIndexes) {
        if (selectedIndexes == null) {
            return fullValues;
        }
        String[] projected = new String[selectedIndexes.size()];
        if (fullValues == null) {
            return projected;
        }
        for (int i = 0; i < selectedIndexes.size(); i++) {
            int originalIndex = selectedIndexes.get(i).intValue();
            if (originalIndex >= 0 && originalIndex < fullValues.length) {
                projected[i] = fullValues[originalIndex];
            }
        }
        return projected;
    }

    /**
     * 将原 NUMERIC 下标映射到投影后的新下标。
     *
     * @param originalNumericIndexes 全量导出中的 NUMERIC 下标
     * @param selectedIndexes        选中原下标；null 表示不映射
     * @return 投影后 NUMERIC 下标集合
     */
    public static Set<Integer> remapNumericColumnIndexes(
            Set<Integer> originalNumericIndexes,
            List<Integer> selectedIndexes) {
        if (selectedIndexes == null) {
            if (originalNumericIndexes == null) {
                return Set.of();
            }
            return originalNumericIndexes;
        }
        if (originalNumericIndexes == null || originalNumericIndexes.isEmpty()) {
            return Set.of();
        }
        Set<Integer> remapped = new HashSet<>();
        for (int newIndex = 0; newIndex < selectedIndexes.size(); newIndex++) {
            Integer original = selectedIndexes.get(newIndex);
            if (original != null && originalNumericIndexes.contains(original)) {
                remapped.add(Integer.valueOf(newIndex));
            }
        }
        return Collections.unmodifiableSet(remapped);
    }

    /**
     * 在导入表头数组中定位规范名（精确匹配）。
     *
     * @param importColumnHeaders 导入表头
     * @param headerName          规范名
     * @return 0-based 导入下标；未找到 -1
     */
    private static int indexOfHeader(String[] importColumnHeaders, String headerName) {
        for (int i = 0; i < importColumnHeaders.length; i++) {
            if (headerName.equals(importColumnHeaders[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 按列是否数值列写出单元格（由各目录 TemplateBuilder 的导出行调用）。
     *
     * @param cell         目标单元格；null 则忽略
     * @param columnIndex  0-based 列下标（含导出首列「编号」偏移后的下标）
     * @param text         已格式化文案；null 则不写
     * @param numericCols  须写为数字的列下标集合；null 视为无数字列
     */
    public static void write(Cell cell, int columnIndex, String text, Set<Integer> numericCols) {
        if (cell == null) {
            return;
        }
        if (text == null) {
            return;
        }
        // 数字列：解析明文为 BigDecimal 后写 NUMERIC（可求和）
        if (numericCols != null && numericCols.contains(columnIndex) && !text.isBlank()) {
            try {
                cell.setCellValue(new BigDecimal(text.trim()).doubleValue());
                return;
            } catch (NumberFormatException ignored) {
                // 非纯数字（异常占位等）回落文本，保证文件仍可打开
            }
        }
        cell.setCellValue(text);
    }

    /**
     * 去空白；空串视为 null。
     *
     * @param text 原文
     * @return trim 后非空串或 null
     */
    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
