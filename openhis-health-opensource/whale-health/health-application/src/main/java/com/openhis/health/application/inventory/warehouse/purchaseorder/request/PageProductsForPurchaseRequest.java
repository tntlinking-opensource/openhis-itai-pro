/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 采购选品分页请求（对齐 ProductDefinitionPageQuery）。
 */
@Getter
@Setter
public class PageProductsForPurchaseRequest extends AbstractHealthPageRequest {

    /** 产品表名（药品/耗材目录表）。 */
    private String productTableName;
    /** 供应类别 code。 */
    private String categoryCode;
    /** 是否仅查启用目录。 */
    private Boolean activeFlag;
    /** 供应商主键筛选。 */
    private Long supplierId;
    /** 生产厂家关键字。 */
    private String manufacturer;
    /** 是否附带现库可用量扩展。 */
    private Boolean includeInventory;
    /** 排序字段。 */
    private String sortField;
    /** 排序方向（asc/desc）。 */
    private String sortOrder;
}
