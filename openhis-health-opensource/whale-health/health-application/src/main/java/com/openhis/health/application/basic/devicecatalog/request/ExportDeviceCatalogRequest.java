/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.request;

import lombok.Data;

/**
 * 耗材目录导出请求（筛选口径与维护列表一致；不分页，受 {@code EXPORT_MAX_ROWS} 截断）。
 */
@Data
public class ExportDeviceCatalogRequest {

    /**
     * 关键字（名称/拼音/五笔/业务号/医保编码模糊）；空表示不限。
     */
    private String keyword;

    /**
     * 耗材分类编码筛选；空表示不限。
     */
    private String categoryCode;

    /**
     * 耗材种类编码筛选；空表示不限。
     */
    private String typeCode;
}
