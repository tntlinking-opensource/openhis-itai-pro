/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.util;

import com.openhis.whale.core.application.annotation.Excel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@link Excel} 注解将行 DTO 列表写出为 xlsx 字节流。
 */
public final class AnnotatedExcelExporter {

    private static final Map<Class<?>, List<ExcelColumnBinding<?>>> COLUMN_CACHE = new ConcurrentHashMap<>();

    private AnnotatedExcelExporter() {
    }

    /**
     * 将注解行写出为单工作表 xlsx。
     *
     * @param sheetName 工作表名
     * @param rows      数据行
     * @param rowType   行 DTO 类型（须含 {@link Excel} 字段）
     * @param <T>       行类型
     * @return 工作簿字节流
     */
    public static <T> byte[] exportToXlsxBytes(String sheetName, List<T> rows, Class<T> rowType) {
        List<ExcelColumnBinding<T>> columns = resolveColumns(rowType);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                headerRow.createCell(columnIndex).setCellValue(columns.get(columnIndex).headerName());
            }
            int rowIndex = 1;
            if (rows != null) {
                for (T row : rows) {
                    Row excelRow = sheet.createRow(rowIndex);
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                        writeCell(excelRow.createCell(columnIndex), columns.get(columnIndex).readValue(row));
                    }
                    rowIndex = rowIndex + 1;
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Excel export failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<ExcelColumnBinding<T>> resolveColumns(Class<T> rowType) {
        return (List<ExcelColumnBinding<T>>) (List<?>) COLUMN_CACHE.computeIfAbsent(rowType, AnnotatedExcelExporter::scanColumns);
    }

    private static List<ExcelColumnBinding<?>> scanColumns(Class<?> rowType) {
        List<ExcelColumnBinding<?>> bindings = new ArrayList<>();
        for (Field field : rowType.getDeclaredFields()) {
            Excel excel = field.getAnnotation(Excel.class);
            if (excel == null || !excel.export()) {
                continue;
            }
            field.setAccessible(true);
            bindings.add(new ExcelColumnBinding<>(field, excel));
        }
        bindings.sort(Comparator.comparingInt(binding -> binding.sort()));
        return List.copyOf(bindings);
    }

    private static void writeCell(Cell cell, String text) {
        if (text == null) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(text);
    }

    private static final class ExcelColumnBinding<T> {

        private final Field field;
        private final Excel excel;

        private ExcelColumnBinding(Field field, Excel excel) {
            this.field = field;
            this.excel = excel;
        }

        private int sort() {
            return this.excel.sort();
        }

        private String headerName() {
            return this.excel.name();
        }

        private String readValue(T row) {
            if (row == null) {
                return this.excel.defaultValue();
            }
            try {
                return this.formatValue(this.field.get(row));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Excel column read failed: " + this.field.getName(), ex);
            }
        }

        private String formatValue(Object value) {
            if (value == null) {
                return this.excel.defaultValue();
            }
            if (value instanceof BigDecimal decimal) {
                if (this.excel.scale() >= 0) {
                    return decimal.setScale(this.excel.scale(), this.excel.roundingMode()).toPlainString();
                }
                return decimal.toPlainString();
            }
            return String.valueOf(value);
        }
    }
}
