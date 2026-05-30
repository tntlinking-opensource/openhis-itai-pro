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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医嘱补费父锚点（IF-IP-PRC-04，BR-IP-PRC-005）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱补费父锚点")
public class NurseStationPricingParentRequestAnchorDto {

    /** 父医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @Schema(description = "父医嘱类型：MED_REQUEST / SERVICE_REQUEST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderType;

    /** 父药品/诊疗申请主键。 */
    @Schema(description = "父药品/诊疗申请主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;
}
