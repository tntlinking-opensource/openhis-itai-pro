/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室退库选品分页请求（源科室药房库存批号级，IF-DEP-RTW-11）。
 *
 * <p>按源科室药房库存批号行展开；{@code productTableName} 须落在药品/耗材定义表白名单内。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class PageProductsForReturnToWarehouseRequest extends AbstractHealthPageRequest {

    /**
     * 源科室药房位置主键；须在会话岗位管辖的 {@code DEPARTMENT_PHARMACY} 内。
     */
    @Schema(description = "源科室药房位置", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /**
     * 物品定义物理表名白名单（药品或耗材定义表）；非法表名拒绝选品。
     */
    @Schema(description = "物品表名白名单", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /**
     * 是否仅查选品库位账面数量大于 0 的批号行；可选，非必传。
     *
     * <p>{@code true}：仅返回 {@code quantity > 0}；{@code false} 或未传：不按数量过滤，零库存行一并返回。</p>
     */
    @Schema(description = "true=仅返回选品库位账面量>0的批号行；false/未传=不过滤数量")
    private Boolean positiveQuantityOnly;
}
