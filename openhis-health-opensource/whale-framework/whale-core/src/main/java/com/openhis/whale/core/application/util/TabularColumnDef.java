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
 */
public record TabularColumnDef(String field, String headerName) {

    public TabularColumnDef {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field 不能为空");
        }
        if (headerName == null || headerName.isBlank()) {
            headerName = field;
        }
    }
}
