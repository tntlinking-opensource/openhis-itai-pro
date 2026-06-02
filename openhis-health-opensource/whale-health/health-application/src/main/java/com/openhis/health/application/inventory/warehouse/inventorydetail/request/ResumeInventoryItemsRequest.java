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

package com.openhis.health.application.inventory.warehouse.inventorydetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量恢复供应入参（IF-INV-DTL-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量恢复供应 IF-INV-DTL-09", requiredProperties = {"inventoryItemIds"})
public class ResumeInventoryItemsRequest {

    @NotEmpty(message = "库存项主键列表不能为空")
    @Schema(description = "库存项主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> inventoryItemIds;
}
