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

package com.openhis.health.application.inventory.warehouse.transferin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 审批同意药房入库响应（IF-PHA-IN-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 审批同意药房入库 IF-PHA-IN-08。 */
@Schema(description = "审批同意药房入库 IF-PHA-IN-08")
public class AgreeTransferInResponse {

    @Schema(description = "业务单号")
    private String busNo;

    /** 同意后状态 SupplyStatus#getCode。 */
    @Schema(description = "同意后状态 SupplyStatus#getCode")
    private String statusEnum;
}
