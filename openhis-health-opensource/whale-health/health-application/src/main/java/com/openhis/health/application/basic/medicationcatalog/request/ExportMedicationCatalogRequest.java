/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.request;

import lombok.Data;

/**
 * 药品目录导出请求（筛选口径与维护列表一致；不分页，受 {@code EXPORT_MAX_ROWS} 截断）。
 *
 * <p>对应 IF-BAS-CAT-EXP-01；应用层按导入模板 v1.6 列序写出 xlsx。</p>
 */
@Data
public class ExportMedicationCatalogRequest {

    /**
     * 关键字（药品名称/商品名/拼音/五笔/业务号模糊）；空表示不限。
     */
    private String keyword;

    /**
     * 药品分类编码筛选；空表示不限。
     */
    private String categoryCode;

    /**
     * 基药标识筛选（对应药典 {@code basic_flag}）；{@code null} 表示不限。
     */
    private Boolean basicFlag;
}
