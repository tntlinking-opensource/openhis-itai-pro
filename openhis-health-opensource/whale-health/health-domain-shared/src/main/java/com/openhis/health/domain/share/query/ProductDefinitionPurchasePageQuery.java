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

    /**
     * 仅返回账面可用库存大于 0 的定义（分页前过滤）；采购选品未传则不过滤。
     */
    private Boolean positiveQuantityOnly;

    /**
     * 「只看库存&gt;0」时 EXISTS 限定的库位集合（医生站候选发药库位）。
     *
     * <p>{@code null}：不按库位收窄（采购/进货等全院账面口径）；非 null（含空列表）：仅这些库位有正库存才命中，
     * 空列表表示无可发药库位、药/耗无命中。</p>
     */
    private List<Long> positiveQuantityLocationIds;

    /** 排序字段（可选；默认按创建时间降序）。 */
    private String sortField;

    /** 排序方向。 */
    private String sortOrder;
}
