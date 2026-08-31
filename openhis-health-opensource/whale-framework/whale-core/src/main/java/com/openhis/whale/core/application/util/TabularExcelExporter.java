/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.util;

import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于动态列定义与 Map 行数据写出 xlsx（单据详情、期初期末等无固定注解 DTO 场景）。
 *
 * <p>{@link Number}/{@link java.math.BigDecimal} 经 {@link ExcelCellWriter} 写为数字单元格；
 * 列 {@link TabularColumnDef#scale()} 控制显示小数位（数量 0、金额展示 2）。</p>
 *
 * <p>调用方须把数量/金额以 Number 放入 Map，禁止先 {@code toPlainString()} 再放入——否则仍会落成文本。</p>
 *
 * <p>推荐前端下发 {@link ExcelExportColumnDto}：按 {@link TabularColumnDef#field()} 匹配；
 * 未传或空清单导出全量列；有 title 时覆盖表头。</p>
 */
public final class TabularExcelExporter {

    private TabularExcelExporter() {
    }

    /**
     * 将动态列与 Map 行写出为单工作表 xlsx（全部列）。
     *
     * @param sheetName 工作表名（空或空白则使用 Sheet1）
     * @param columns   列定义（顺序即导出列顺序）；不可空
     * @param rows      数据行；null 视为无数据（仅表头）
     * @return xlsx 字节流
     */
    public static byte[] exportToXlsxBytes(
            String sheetName, List<TabularColumnDef> columns, List<Map<String, Object>> rows) {
        return exportToXlsxBytes(sheetName, columns, rows, null);
    }

    /**
     * 将动态列与 Map 行写出为单工作表 xlsx；可按前端列清单裁剪并覆盖表头。
     *
     * @param sheetName         工作表名（空或空白则使用 Sheet1）
     * @param columns           全量列定义；不可空
     * @param rows              数据行；null 视为无数据（仅表头）
     * @param requestedColumns  前端自选列；null/空则导出全部 columns
     * @return xlsx 字节流
     */
    public static byte[] exportToXlsxBytes(
            String sheetName,
            List<TabularColumnDef> columns,
            List<Map<String, Object>> rows,
            List<ExcelExportColumnDto> requestedColumns) {
        String resolvedSheetName = sheetName;
        // 空表名回落 Sheet1，避免 POI 拒收
        if (resolvedSheetName == null || resolvedSheetName.isBlank()) {
            resolvedSheetName = "Sheet1";
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns 不能为空");
        }
        List<TabularColumnDef> exportColumns = resolveExportColumns(columns, requestedColumns);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(resolvedSheetName);
            Row headerRow = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < exportColumns.size(); columnIndex += 1) {
                TabularColumnDef column = exportColumns.get(columnIndex);
                headerRow.createCell(columnIndex).setCellValue(column.headerName());
            }
            Map<Integer, CellStyle> numericStyles = new HashMap<>();
            int rowIndex = 1;
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Row excelRow = sheet.createRow(rowIndex);
                    for (int columnIndex = 0; columnIndex < exportColumns.size(); columnIndex += 1) {
                        TabularColumnDef column = exportColumns.get(columnIndex);
                        Object value = null;
                        if (row != null) {
                            value = row.get(column.field());
                        }
                        // 动态列统一 HALF_UP；刻度由列定义 scale 决定
                        ExcelCellWriter.write(
                            excelRow.createCell(columnIndex),
                            value,
                            column.scale(),
                            RoundingMode.HALF_UP,
                            workbook,
                            numericStyles);
                    }
                    rowIndex = rowIndex + 1;
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Tabular Excel export failed", ex);
        }
    }

    /**
     * 按前端列清单裁剪动态列；未传清单则全量；有 title 覆盖表头。
     *
     * @param allColumns        全量列定义
     * @param requestedColumns  前端自选列
     * @return 实际写出列（非空）
     */
    private static List<TabularColumnDef> resolveExportColumns(
            List<TabularColumnDef> allColumns,
            List<ExcelExportColumnDto> requestedColumns) {
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            return allColumns;
        }
        Map<String, TabularColumnDef> byField = new LinkedHashMap<>();
        for (TabularColumnDef column : allColumns) {
            byField.put(column.field(), column);
        }
        List<TabularColumnDef> selected = new ArrayList<>();
        for (ExcelExportColumnDto request : requestedColumns) {
            if (request == null) {
                continue;
            }
            String fieldName = trimToNull(request.getField());
            // 空 field 跳过，避免 NPE
            if (fieldName == null) {
                continue;
            }
            TabularColumnDef column = byField.get(fieldName);
            // 非白名单字段跳过（防前端误传）
            if (column == null) {
                continue;
            }
            String titleOverride = trimToNull(request.getTitle());
            if (titleOverride == null) {
                selected.add(column);
            } else {
                selected.add(new TabularColumnDef(column.field(), titleOverride, column.scale()));
            }
        }
        // 清单全部非法：回落全量列，避免空表头
        if (selected.isEmpty()) {
            return allColumns;
        }
        return selected;
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
