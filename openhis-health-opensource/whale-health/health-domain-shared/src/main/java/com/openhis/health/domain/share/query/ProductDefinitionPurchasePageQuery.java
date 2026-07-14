/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.query;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 采购/进货选品分页入参（读主表；由应用层 {@code ProductDefinitionPageQuery} 转入）。
 */
@Getter
@Setter
public class ProductDefinitionPurchasePageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 产品来源表名（单表；药库场景通常为药品或耗材表）。 */
    private String productTableName;

    /** 分类编码。 */
    private String categoryCode;

    /** 排除药品目录 categoryCode。 */
    private List<String> excludeMedicationCategoryCodes;

    /** 启用标志。 */
    private Boolean activeFlag;

    /** 供应商标识（药品/耗材 {@code supply_id}）。 */
    private Long supplierId;

    /** 厂家名称。 */
    private String manufacturer;

    /** 预选产品键，与关键字 OR 组合。 */
    private List<ProductDefinitionBatchLookup> prepickedProductKeys;

    /** 排序字段（可选；默认按创建时间降序）。 */
    private String sortField;

    /** 排序方向。 */
    private String sortOrder;
}
