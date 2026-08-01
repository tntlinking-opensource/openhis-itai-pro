/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.dto;

import lombok.Data;

/**
 * 诊疗目录导入单行/批级错误项。
 */
@Data
public class ImportServiceCatalogErrorItemDto {

    /** Excel 1-based 行号；批级 Gate 错误可为 {@code null}。 */
    private Integer excelRowNum;

    /** 出错列中文名；批级错误可为 {@code null}。 */
    private String columnName;

    /** 业务错误码（如 {@code SVC_CAT_IMP_301}）。 */
    private String code;

    /** 人可读错误说明。 */
    private String message;
}
