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

package com.openhis.health.application.inventory.warehouse.stockout.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * <p>继承 {@link AbstractHealthPageRequest}（B.10.11）；{@code productTableName} 须落在药品定义表与耗材定义表白名单内，由应用层门禁。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class PageProductsForStockOutRequest extends AbstractHealthPageRequest {

    @Schema(description = "源药库位置", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    @Schema(description = "物品表名白名单", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
