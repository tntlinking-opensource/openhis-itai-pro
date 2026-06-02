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

package com.openhis.health.application.inventory.pharmacy.stockin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 撤回药房入库审批响应（IF-PHA-IN-07）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤回药房入库审批 IF-PHA-IN-07")
public class WithdrawPharmacyStockInApprovalResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "撤回后状态 SupplyStatus#getCode")
    private String statusEnum;
}
