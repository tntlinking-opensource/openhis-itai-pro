/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 产品选品分页请求（已废弃，本场景禁止自由选品）。
 */
@Getter
@Setter
public class PageProductsForReturnToWarehouseRequest extends AbstractHealthPageRequest {

    /** 产品定义来源表名。 */
    private String productTableName;
    /** 品类编码。 */
    private String categoryCode;
    /** 启用标志。 */
    private Boolean activeFlag;
    /** 供应商主键。 */
    private Long supplierId;
    /** 生产厂家。 */
    private String manufacturer;
    /** 是否附带库存信息。 */
    private Boolean includeInventory;
    /** 排序字段。 */
    private String sortField;
    /** 排序方向。 */
    private String sortOrder;
}
