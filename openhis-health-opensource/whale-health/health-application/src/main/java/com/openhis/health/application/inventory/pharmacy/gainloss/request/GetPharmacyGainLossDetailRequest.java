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

package com.openhis.health.application.inventory.pharmacy.gainloss.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 盘点单详情入参（IF-PHA-ST-03）。
 */
@Data
@Accessors(chain = true)
/** 盘点单详情 IF-PHA-ST-03。 */
@Schema(description = "盘点单详情 IF-PHA-ST-03")
public class GetPharmacyGainLossDetailRequest {

    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
