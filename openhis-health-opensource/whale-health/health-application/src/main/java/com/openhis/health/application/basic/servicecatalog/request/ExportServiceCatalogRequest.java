/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.request;

import lombok.Data;

/**
 * 诊疗目录导出请求（筛选口径与维护列表一致；不分页，受 {@code EXPORT_MAX_ROWS} 截断）。
 */
@Data
public class ExportServiceCatalogRequest {

    /**
     * 关键字（名称/助记码/业务号模糊）；空表示不限。
     */
    private String keyword;

    /**
     * 目录分类编码筛选；空表示不限。
     */
    private String categoryCode;
}
