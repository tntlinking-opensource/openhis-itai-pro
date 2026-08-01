/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.catalog.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 体检目录通用分页检索请求（CFG-01～05 列表 Tab 共用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchCatalogRequest extends AbstractHealthPageRequest {

    /** 分类编码精确过滤（小项 OD 等场景）。 */
    private String categoryCode;

    /** 项目类型编码精确过滤（体检项目 AD 等场景）。 */
    private String typeEnum;

    /** 发布状态精确过滤。 */
    private String statusEnum;
}
