/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药库入库选品分页请求（IF-WHS-IN-11）。
 *
 * <p>继承 {@link AbstractHealthPageRequest}（B.10.11）。
 * 应用层在分页返回前对 {@link com.zxplat.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto}
 * 二次写入实例维采购进价（{@link com.zxplat.domain.share.enums.PriceType#PURCHASE}）及目的库可用库存。
 * 须携带 {@link #purposeLocationId}（目的药库，用于与进货头一致的药库门禁）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库入库选品分页 IF-WHS-IN-11")
public class PageProductsForInboundReceiptRequest extends AbstractHealthPageRequest {

    @JsonSerialize(using = ToStringSerializer.class)
    /** 目的药库 purposeLocationId。 */
    @Schema(description = "目的药库 purposeLocationId")
    private Long purposeLocationId;

    /** 物品来源表名 productTableName。 */
    @Schema(description = "物品来源表名 productTableName")
    private String productTableName;

    /** 物资分类编码 categoryCode。 */
    @Schema(description = "物资分类编码 categoryCode")
    private String categoryCode;

    /** 是否仅启用 activeFlag。 */
    @Schema(description = "是否仅启用 activeFlag")
    private Boolean activeFlag;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 生产厂家 manufacturer。 */
    @Schema(description = "生产厂家 manufacturer")
    private String manufacturer;

    /** 是否附带库存 includeInventory。 */
    @Schema(description = "是否附带库存 includeInventory")
    private Boolean includeInventory;

    /** 排序字段 sortField。 */
    @Schema(description = "排序字段 sortField")
    private String sortField;

    /** 排序方向 sortOrder。 */
    @Schema(description = "排序方向 sortOrder")
    private String sortOrder;
}
