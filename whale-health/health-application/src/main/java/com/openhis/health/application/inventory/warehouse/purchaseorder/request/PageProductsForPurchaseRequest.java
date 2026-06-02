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

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 采购选品分页请求（对齐 ProductDefinitionPageQuery）。
 */
@Getter
@Setter
public class PageProductsForPurchaseRequest extends AbstractHealthPageRequest {

    /**
     * productTableName。
     */
    private String productTableName;
    /**
     * categoryCode。
     */
    private String categoryCode;
    /**
     * activeFlag。
     */
    private Boolean activeFlag;
    /**
     * supplierId。
     */
    private Long supplierId;
    /**
     * manufacturer。
     */
    private String manufacturer;
    /**
     * includeInventory。
     */
    private Boolean includeInventory;
    /**
     * sortField。
     */
    private String sortField;
    /**
     * sortOrder。
     */
    private String sortOrder;
}
