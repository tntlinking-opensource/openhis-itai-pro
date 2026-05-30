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

package com.openhis.health.application.inventory.warehouse.stockout.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class AgreeStockOutResponse {

    /** 出库业务单号。 */
    @Schema(description = "出库单号")
    private String busNo;

    /** 同意后的出库单状态持久化身份。 */
    @Schema(description = "出库单状态")
    private String statusEnum;

    /** 系统生成的药房入库壳单业务单号；未生成对侧单时可为 null。 */
    @Schema(description = "系统生成的药房入库单号")
    private String generatedPharmacyStockInBusNo;
}
