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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.pharmacy.gainloss.dto.PharmacyGainLossLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 新建药房盘点单入参（IF-PHA-ST-04）。
 */
@Data
@Accessors(chain = true)
/** 新建盘点单 IF-PHA-ST-04。 */
@Schema(description = "新建盘点单 IF-PHA-ST-04")
public class CreatePharmacyGainLossRequest {

    /** 盘点药房位置主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "盘点药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 经手人医务人员主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /** 供应类别；默认 STOCK_SUPPLY。 */
    @Schema(description = "供应类别 categoryEnum")
    private String categoryEnum;

    /** 明细行。 */
    @Schema(description = "明细行 lines", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PharmacyGainLossLineDto> lines;
}
