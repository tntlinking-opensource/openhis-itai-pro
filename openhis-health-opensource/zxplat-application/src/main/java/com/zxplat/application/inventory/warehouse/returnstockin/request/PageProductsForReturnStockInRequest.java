/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.request;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 退库入库选品分页请求（IF-WHS-RSI-11，禁止自由选品：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "退库入库选品分页 IF-WHS-RSI-11（已拒绝）")
public class PageProductsForReturnStockInRequest extends AbstractHealthPageRequest {

    /** 物品表 productTableName。 */
    @Schema(description = "物品表 productTableName")
    private String productTableName;
    /** 分类编码 categoryCode。 */
    @Schema(description = "分类编码 categoryCode")
    private String categoryCode;
    /** 启用标志 activeFlag。 */
    @Schema(description = "启用标志 activeFlag")
    private Boolean activeFlag;
    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;
    /** 生产厂家 manufacturer。 */
    @Schema(description = "生产厂家 manufacturer")
    private String manufacturer;
    /** 附带库存 includeInventory。 */
    @Schema(description = "附带库存 includeInventory")
    private Boolean includeInventory;
    /** 排序字段 sortField。 */
    @Schema(description = "排序字段 sortField")
    private String sortField;
    /** 排序方向 sortOrder。 */
    @Schema(description = "排序方向 sortOrder")
    private String sortOrder;
}
