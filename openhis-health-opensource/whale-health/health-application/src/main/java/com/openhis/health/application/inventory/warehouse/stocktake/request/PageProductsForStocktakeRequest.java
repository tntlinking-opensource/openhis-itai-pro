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

package com.openhis.health.application.inventory.warehouse.stocktake.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class PageProductsForStocktakeRequest extends AbstractHealthPageRequest {

    /** 盘点药库（非 sourceLocationId）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 盘点药库 purposeLocationId。 */
    @Schema(description = "盘点药库 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 物品定义物理表名。 */
    @Schema(description = "产品表 productTableName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
