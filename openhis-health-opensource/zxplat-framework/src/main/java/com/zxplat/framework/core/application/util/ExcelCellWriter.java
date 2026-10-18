/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Excel 单元格写出：数值写为数字类型（可求和），文本仍写字符串。
 *
 * <p>业务背景：历史导出一律 {@code setCellValue(String)}，Excel 将数量/金额识别为文本，
 * 状态栏只能「计数」、需双击才转成数字。本类统一 NUMERIC 写出。</p>
 *
 * <p>刻度约定（与 health {@code ChargeItemAmountScaleConstants} / BR-MON-001 展示层对齐）：
 * 数量导出常用 {@code scale=0}；金额/单价常用展示 2 位；{@link #SCALE_UNSET} 表示不强制小数位。</p>
 */
final class ExcelCellWriter {

    /** 不强制小数位：仍写数字单元格，不套固定显示格式。 */
    static final int SCALE_UNSET = -1;

    private ExcelCellWriter() {
    }

    /**
     * 按值类型写出单元格（供 {@link AnnotatedExcelExporter}/{@link TabularExcelExporter} 共用）。
     *
     * @param cell           目标单元格
     * @param value          单元格值（null → 空白）
     * @param scale          {@link BigDecimal}/数值小数位；{@link #SCALE_UNSET} 表示不强制
     * @param roundingMode   scale 启用时的舍入；null 时回落 HALF_UP
     * @param workbook       工作簿（创建数字样式）
     * @param numericStyles  按 scale 缓存的数字样式（同一工作簿内复用）
     */
    static void write(
            Cell cell,
            Object value,
            int scale,
            RoundingMode roundingMode,
            Workbook workbook,
            Map<Integer, CellStyle> numericStyles) {
        // 空值：写空白，避免空串被当成文本「」
        if (value == null) {
            cell.setBlank();
            return;
        }
        // BigDecimal：业务金额/数量主路径，可按 scale 舍入并套显示格式
        if (value instanceof BigDecimal decimal) {
            writeBigDecimal(cell, decimal, scale, roundingMode, workbook, numericStyles);
            return;
        }
        // 其它 Number（Integer/Long/Double 等）：写 NUMERIC；有 scale 时仍套显示格式
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            if (scale >= 0) {
                cell.setCellStyle(styleForScale(workbook, numericStyles, scale));
            }
            return;
        }
        // 布尔：保留原生类型，便于筛选
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool.booleanValue());
            return;
        }
        // 其余一律文本（编码、姓名、带单位合并文案等）
        cell.setCellValue(String.valueOf(value));
    }

    /**
     * 写出 BigDecimal：先按 scale 舍入，再写为数字单元格并套显示格式。
     *
     * @param cell           目标单元格
     * @param decimal        数值
     * @param scale          小数位；{@link #SCALE_UNSET} 不强制
     * @param roundingMode   舍入模式
     * @param workbook       工作簿
     * @param numericStyles  样式缓存
     */
    private static void writeBigDecimal(
            Cell cell,
            BigDecimal decimal,
            int scale,
            RoundingMode roundingMode,
            Workbook workbook,
            Map<Integer, CellStyle> numericStyles) {
        // 未指定小数位：原值写入 NUMERIC（Excel General 显示）
        if (scale < 0) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }
        RoundingMode mode = roundingMode;
        // 注解未给舍入时与业务金额默认 HALF_UP 对齐
        if (mode == null) {
            mode = RoundingMode.HALF_UP;
        }
        BigDecimal scaled = decimal.setScale(scale, mode);
        cell.setCellValue(scaled.doubleValue());
        cell.setCellStyle(styleForScale(workbook, numericStyles, scale));
    }

    /**
     * 按小数位取/建数字单元格样式（如 {@code 0}、{@code 0.00}）。
     *
     * @param workbook 工作簿
     * @param cache    scale → 样式
     * @param scale    小数位（须 &gt;= 0）
     * @return 单元格样式
     */
    private static CellStyle styleForScale(Workbook workbook, Map<Integer, CellStyle> cache, int scale) {
        CellStyle cached = cache.get(scale);
        // 同工作簿同 scale 复用，避免每个单元格新建 Style
        if (cached != null) {
            return cached;
        }
        CellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat(buildDecimalPattern(scale)));
        cache.put(scale, style);
        return style;
    }

    /**
     * 构建 Excel 小数显示格式。
     *
     * @param scale 小数位（0 → {@code 0}；2 → {@code 0.00}）
     * @return 格式串
     */
    private static String buildDecimalPattern(int scale) {
        // 整数数量列：无小数点
        if (scale <= 0) {
            return "0";
        }
        StringBuilder pattern = new StringBuilder("0.");
        for (int i = 0; i < scale; i++) {
            pattern.append('0');
        }
        return pattern.toString();
    }
}
