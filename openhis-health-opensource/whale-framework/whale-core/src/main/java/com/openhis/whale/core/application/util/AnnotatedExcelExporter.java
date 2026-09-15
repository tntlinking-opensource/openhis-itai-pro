/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.util;

import com.openhis.whale.core.application.annotation.Excel;
import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@link Excel} 注解将行 DTO 列表写出为 xlsx 字节流。
 *
 * <p>{@link BigDecimal}/{@link Number} 经 {@link ExcelCellWriter} 写为<strong>数字</strong>单元格（可框选求和）；
 * {@link Excel#scale()} &gt;= 0 时先舍入再套显示格式（数量常用 0、金额常用展示 2 位）。</p>
 *
 * <p><strong>推荐</strong>：前端下发 {@link ExcelExportColumnDto} 列清单（页面有什么导出什么），
 * 调用 {@link #exportToXlsxBytes(String, List, Class, List)}；未传列清单时导出全部 {@code @Excel} 列。</p>
 *
 * <p>禁止再把金额/数量 {@code toPlainString()} 后当文本写出——那是「数字存成文本」绿三角根因。</p>
 */
public final class AnnotatedExcelExporter {

    private static final Map<Class<?>, List<ExcelColumnBinding<?>>> COLUMN_CACHE = new ConcurrentHashMap<>();

    private AnnotatedExcelExporter() {
    }

    /**
     * 将注解行写出为单工作表 xlsx（全部 {@link Excel} 列）。
     *
     * @param sheetName 工作表名
     * @param rows      数据行；null 视为无数据（仅表头）
     * @param rowType   行 DTO 类型（须含 {@link Excel} 字段）
     * @param <T>       行类型
     * @return 工作簿字节流
     */
    public static <T> byte[] exportToXlsxBytes(String sheetName, List<T> rows, Class<T> rowType) {
        return exportToXlsxBytes(sheetName, rows, rowType, null);
    }

    /**
     * 将注解行写出为单工作表 xlsx；可按前端列清单裁剪并覆盖表头。
     *
     * @param sheetName         工作表名
     * @param rows              数据行；null 视为无数据（仅表头）
     * @param rowType           行 DTO 类型（须含 {@link Excel} 字段）
     * @param requestedColumns  前端自选列；null/空则导出全部 {@code @Excel} 列（按 sort）
     * @param <T>               行类型
     * @return 工作簿字节流
     */
    public static <T> byte[] exportToXlsxBytes(
        String sheetName,
        List<T> rows,
        Class<T> rowType,
        List<ExcelExportColumnDto> requestedColumns) {
        List<ExcelColumnBinding<T>> columns = resolveExportColumns(rowType, requestedColumns);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                headerRow.createCell(columnIndex).setCellValue(columns.get(columnIndex).headerName());
            }
            // 同簿按 scale 缓存数字样式，避免每格新建
            Map<Integer, CellStyle> numericStyles = new HashMap<>();
            int rowIndex = 1;
            if (rows != null) {
                for (T row : rows) {
                    Row excelRow = sheet.createRow(rowIndex);
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                        ExcelColumnBinding<T> binding = columns.get(columnIndex);
                        Cell cell = excelRow.createCell(columnIndex);
                        binding.writeCell(cell, row, workbook, numericStyles);
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

    /**
     * 兼容旧调用：{@code omitAllBlankColumns=true} 时在全量 {@code @Excel} 列上丢掉全空列。
     *
     * <p>新代码应传前端 {@code exportColumns}，勿再依赖全空省略。</p>
     *
     * @param sheetName             工作表名
     * @param rows                  数据行
     * @param rowType               行类型
     * @param omitAllBlankColumns   true：省略全空列
     * @param <T>                   行类型
     * @return 工作簿字节流
     * @deprecated 改用 {@link #exportToXlsxBytes(String, List, Class, List)}
     */
    @Deprecated
    public static <T> byte[] exportToXlsxBytes(
        String sheetName,
        List<T> rows,
        Class<T> rowType,
        boolean omitAllBlankColumns) {
        List<ExcelColumnBinding<T>> allColumns = resolveColumns(rowType);
        List<ExcelColumnBinding<T>> columns = allColumns;
        if (omitAllBlankColumns) {
            columns = filterColumnsWithAnyValue(allColumns, rows);
        }
        return writeWorkbook(sheetName, rows, columns);
    }

    /**
     * 按前端列清单解析绑定；未传清单则全量 sort 序。
     *
     * @param rowType           行类型
     * @param requestedColumns  自选列
     * @param <T>               行类型
     * @return 写出列（含表头覆盖）
     */
    private static <T> List<ExcelColumnBinding<T>> resolveExportColumns(
        Class<T> rowType,
        List<ExcelExportColumnDto> requestedColumns) {
        List<ExcelColumnBinding<T>> allColumns = resolveColumns(rowType);
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            return allColumns;
        }
        Map<String, ExcelColumnBinding<T>> byField = new LinkedHashMap<>();
        for (ExcelColumnBinding<T> binding : allColumns) {
            byField.put(binding.fieldName(), binding);
        }
        List<ExcelColumnBinding<T>> selected = new ArrayList<>();
        for (ExcelExportColumnDto request : requestedColumns) {
            if (request == null) {
                continue;
            }
            String fieldName = trimToNull(request.getField());
            // 空 field 跳过，避免 NPE
            if (fieldName == null) {
                continue;
            }
            ExcelColumnBinding<T> binding = byField.get(fieldName);
            // 非白名单字段跳过（防前端误传 / 越权字段）
            if (binding == null) {
                continue;
            }
            String titleOverride = trimToNull(request.getTitle());
            if (titleOverride == null) {
                selected.add(binding);
            } else {
                selected.add(binding.withHeaderName(titleOverride));
            }
        }
        // 清单全部非法：回落全量列，避免空表头
        if (selected.isEmpty()) {
            return allColumns;
        }
        return selected;
    }

    /**
     * 写出工作簿（内部复用）。
     *
     * @param sheetName 工作表名
     * @param rows      数据行
     * @param columns   列绑定
     * @param <T>       行类型
     * @return 字节流
     */
    private static <T> byte[] writeWorkbook(
        String sheetName,
        List<T> rows,
        List<ExcelColumnBinding<T>> columns) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                headerRow.createCell(columnIndex).setCellValue(columns.get(columnIndex).headerName());
            }
            Map<Integer, CellStyle> numericStyles = new HashMap<>();
            int rowIndex = 1;
            if (rows != null) {
                for (T row : rows) {
                    Row excelRow = sheet.createRow(rowIndex);
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                        ExcelColumnBinding<T> binding = columns.get(columnIndex);
                        Cell cell = excelRow.createCell(columnIndex);
                        binding.writeCell(cell, row, workbook, numericStyles);
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

    /**
     * 解析列绑定（按 rowType 缓存）。
     *
     * @param rowType 行类型
     * @param <T>     行类型
     * @return 已排序列绑定
     */
    @SuppressWarnings("unchecked")
    private static <T> List<ExcelColumnBinding<T>> resolveColumns(Class<T> rowType) {
        return (List<ExcelColumnBinding<T>>) (List<?>) COLUMN_CACHE.computeIfAbsent(
            rowType, AnnotatedExcelExporter::scanColumns);
    }

    /**
     * 扫描 DTO 上 {@link Excel} 字段并按 sort 升序。
     *
     * @param rowType 行类型
     * @return 列绑定（不可变副本）
     */
    private static List<ExcelColumnBinding<?>> scanColumns(Class<?> rowType) {
        List<ExcelColumnBinding<?>> bindings = new ArrayList<>();
        for (Field field : rowType.getDeclaredFields()) {
            Excel excel = field.getAnnotation(Excel.class);
            if (excel == null || !excel.export()) {
                continue;
            }
            field.setAccessible(true);
            bindings.add(new ExcelColumnBinding<>(field, excel, excel.name()));
        }
        bindings.sort(Comparator.comparingInt(ExcelColumnBinding::sort));
        return List.copyOf(bindings);
    }

    /**
     * 保留「至少一行有非空值」的列。
     *
     * @param columns 全量列
     * @param rows    数据行
     * @param <T>     行类型
     * @return 过滤后的列
     */
    private static <T> List<ExcelColumnBinding<T>> filterColumnsWithAnyValue(
        List<ExcelColumnBinding<T>> columns,
        List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            return columns;
        }
        List<ExcelColumnBinding<T>> kept = new ArrayList<>();
        for (ExcelColumnBinding<T> binding : columns) {
            if (binding.hasAnyNonBlankValue(rows)) {
                kept.add(binding);
            }
        }
        if (kept.isEmpty()) {
            return columns;
        }
        return kept;
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

    /**
     * 单列：字段反射 + {@link Excel} 元数据 + 可选表头覆盖。
     *
     * @param <T> 行类型
     */
    private static final class ExcelColumnBinding<T> {

        private final Field field;
        private final Excel excel;
        private final String headerName;

        /**
         * @param field      已 setAccessible 的字段
         * @param excel      列注解
         * @param headerName 表头（可覆盖注解名）
         */
        private ExcelColumnBinding(Field field, Excel excel, String headerName) {
            this.field = field;
            this.excel = excel;
            this.headerName = headerName;
        }

        /**
         * @return 列序
         */
        private int sort() {
            return this.excel.sort();
        }

        /**
         * @return Java 字段名（与前端 field 对齐）
         */
        private String fieldName() {
            return this.field.getName();
        }

        /**
         * @return 表头文案
         */
        private String headerName() {
            return this.headerName;
        }

        /**
         * 复制绑定并覆盖表头。
         *
         * @param overrideHeader 前端表头
         * @return 新绑定
         */
        private ExcelColumnBinding<T> withHeaderName(String overrideHeader) {
            return new ExcelColumnBinding<>(this.field, this.excel, overrideHeader);
        }

        /**
         * 本列在任一导出行上是否有非空值。
         *
         * @param rows 导出行
         * @return true 表示应保留
         */
        private boolean hasAnyNonBlankValue(List<T> rows) {
            for (T row : rows) {
                if (!isBlankExportValue(this.readRaw(row))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 读取字段并写出单元格。
         *
         * @param cell          目标单元格
         * @param row           行 DTO
         * @param workbook      工作簿
         * @param numericStyles 数字样式缓存
         */
        private void writeCell(Cell cell, T row, Workbook workbook, Map<Integer, CellStyle> numericStyles) {
            Object raw = this.readRaw(row);
            if (raw == null) {
                String defaultText = this.excel.defaultValue();
                if (defaultText == null || defaultText.isEmpty()) {
                    cell.setBlank();
                    return;
                }
                cell.setCellValue(defaultText);
                return;
            }
            ExcelCellWriter.write(
                cell,
                raw,
                this.excel.scale(),
                this.excel.roundingMode(),
                workbook,
                numericStyles);
        }

        /**
         * 反射读取字段原值。
         *
         * @param row 行 DTO
         * @return 字段值
         */
        private Object readRaw(T row) {
            if (row == null) {
                return null;
            }
            try {
                return this.field.get(row);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Excel column read failed: " + this.field.getName(), ex);
            }
        }
    }

    /**
     * 导出空值判定：null / 空白串为空；数字 0 仍有值。
     *
     * @param raw 字段原值
     * @return true 表示空
     */
    private static boolean isBlankExportValue(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof String text) {
            return text.isBlank();
        }
        return false;
    }
}
