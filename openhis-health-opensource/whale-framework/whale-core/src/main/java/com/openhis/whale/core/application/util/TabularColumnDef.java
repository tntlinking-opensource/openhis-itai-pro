/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.util;

/**
 * 动态表格列定义，供 {@link TabularExcelExporter} 写出 xlsx。
 *
 * @param field      行 Map 中的字段键
 * @param headerName 表头显示名
 * @param scale      数值小数位；{@link ExcelCellWriter#SCALE_UNSET} 表示不强制（仍写数字类型）
 */
public record TabularColumnDef(String field, String headerName, int scale) {

    /** 不强制小数位（仍写数字单元格）。 */
    public static final int SCALE_UNSET = ExcelCellWriter.SCALE_UNSET;

    /**
     * 文本或未指定小数位的列（数值仍按数字类型写出）。
     *
     * @param field      字段键
     * @param headerName 表头
     */
    public TabularColumnDef(String field, String headerName) {
        this(field, headerName, SCALE_UNSET);
    }

    public TabularColumnDef {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field 不能为空");
        }
        if (headerName == null || headerName.isBlank()) {
            headerName = field;
        }
    }
}
