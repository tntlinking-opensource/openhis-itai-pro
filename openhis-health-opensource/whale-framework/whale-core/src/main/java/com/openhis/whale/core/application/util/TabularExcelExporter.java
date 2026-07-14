/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * 基于动态列定义与 Map 行数据写出 xlsx（C 类报表等无固定 DTO 场景）。
 */
public final class TabularExcelExporter {

    private TabularExcelExporter() {
    }

    /**
     * 将动态列与 Map 行写出为单工作表 xlsx。
     *
     * @param sheetName 工作表名（空则使用 Sheet1）
     * @param columns   列定义（顺序即导出列顺序）
     * @param rows      数据行
     * @return xlsx 字节流
     */
    public static byte[] exportToXlsxBytes(
            String sheetName, List<TabularColumnDef> columns, List<Map<String, Object>> rows) {
        String resolvedSheetName = sheetName;
        if (resolvedSheetName == null || resolvedSheetName.isBlank()) {
            resolvedSheetName = "Sheet1";
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns 不能为空");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(resolvedSheetName);
            Row headerRow = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex += 1) {
                TabularColumnDef column = columns.get(columnIndex);
                headerRow.createCell(columnIndex).setCellValue(column.headerName());
            }
            int rowIndex = 1;
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Row excelRow = sheet.createRow(rowIndex);
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex += 1) {
                        TabularColumnDef column = columns.get(columnIndex);
                        Object value = null;
                        if (row != null) {
                            value = row.get(column.field());
                        }
                        writeCell(excelRow.createCell(columnIndex), value);
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

    private static void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }
}
