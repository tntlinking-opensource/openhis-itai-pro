/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.transferin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药库调入选品分页请求（IF-WHS-TI-11）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "药库调入选品分页 IF-WHS-TI-11",
    requiredProperties = {"sourceLocationId", "productTableName", "pageNo", "pageSize"}
)
public class PageProductsForTransferInRequest extends AbstractHealthPageRequest {

    @NotNull(message = "sourceLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源药库主键。 */
    @Schema(description = "来源药库主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    @NotBlank(message = "productTableName 不能为空")
    /** 物品物理表 med_medication_definition / adm_device_definition。 */
    @Schema(description = "物品物理表 med_medication_definition / adm_device_definition", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
    /**
     * 是否仅查选品库位账面数量大于 0 的批号行；可选，非必传。
     * <p>{@code true}：仅返回 {@code quantity > 0}；{@code false} 或未传：不按数量过滤，零库存行一并返回。</p>
     * <p>下推 {@code InventoryItemPageQuery#positiveQuantityOnly}，与库存明细「有库存」列表口径一致。</p>
     */
    @Schema(description = "true=仅返回选品库位账面量>0的批号行；false/未传=不过滤数量")
    private Boolean positiveQuantityOnly;
}
