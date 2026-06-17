/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class PageProductsForReturnFromDownstreamRequest extends AbstractHealthPageRequest {

    /** 物品表名。 */
    @Schema(description = "物品表 productTableName")
    private String productTableName;
    /** 物资分类编码。 */
    @Schema(description = "分类编码 categoryCode")
    private String categoryCode;
    /** 启用标志。 */
    @Schema(description = "启用标志 activeFlag")
    private Boolean activeFlag;
    /** 供应商主键。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;
    /** 生产厂家。 */
    @Schema(description = "生产厂家 manufacturer")
    private String manufacturer;
    /** 是否附带库存量。 */
    @Schema(description = "附带库存 includeInventory")
    private Boolean includeInventory;
    /** 排序字段。 */
    @Schema(description = "排序字段 sortField")
    private String sortField;
    /** 排序方向。 */
    @Schema(description = "排序方向 sortOrder")
    private String sortOrder;
}
