/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Excel 导出自选列（前端按页面列清单下发；后端仅写出 {@code @Excel} 白名单字段）。
 *
 * <p>契约见 monetary-amount-contract §1.1 R-EXCEL-4：列真源在前端，
 * {@link com.zxplat.framework.core.application.util.AnnotatedExcelExporter} 按 {@link #field} 匹配。</p>
 */
@Data
@Accessors(chain = true)
public class ExcelExportColumnDto {

    /** 导出行 DTO 字段名（须与 {@code @Excel} 标注字段一致）。 */
    private String field;

    /** 表头文案；空则回落 {@code @Excel#name()}。 */
    private String title;
}
