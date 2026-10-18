/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.stocktake.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 盘点选品分页入参（IF-DEPT-ST-11）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "盘点选品 IF-DEPT-ST-11")
public class PageProductsForStocktakeRequest extends AbstractHealthPageRequest {

    /** 盘点科室药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "盘点科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 产品表 productTableName。 */
    @Schema(description = "产品表 productTableName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
    /**
     * 是否仅查选品库位账面数量大于 0 的批号行；可选，非必传。
     * <p>{@code true}：仅返回 {@code quantity > 0}；{@code false} 或未传：不按数量过滤，零库存行一并返回。</p>
     * <p>下推 {@code InventoryItemPageQuery#positiveQuantityOnly}，与库存明细「有库存」列表口径一致。</p>
     */
    @Schema(description = "true=仅返回选品库位账面量>0的批号行；false/未传=不过滤数量")
    private Boolean positiveQuantityOnly;
}
