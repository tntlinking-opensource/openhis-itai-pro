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

package com.openhis.health.application.inventory.department.gainloss.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 删除盘点单入参（IF-DEP-ST-10）。
 */
@Data
@Accessors(chain = true)
/** 删除盘点单 IF-DEP-ST-10。 */
@Schema(description = "删除盘点单 IF-DEP-ST-10")
public class RemoveDeptGainLossRequest {

    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
