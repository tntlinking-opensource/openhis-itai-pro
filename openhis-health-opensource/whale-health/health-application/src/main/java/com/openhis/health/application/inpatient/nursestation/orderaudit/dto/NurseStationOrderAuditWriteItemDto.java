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

package com.openhis.health.application.inpatient.nursestation.orderaudit.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医嘱校对写操作锚点（IF-IP-AUD-02/03）。
 */
@Data
@Accessors(chain = true)
/** 医嘱校对写操作锚点。 */
@Schema(description = "医嘱校对写操作锚点", requiredProperties = {"orderType", "requestId"})
public class NurseStationOrderAuditWriteItemDto {

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @NotBlank(message = "医嘱类型不能为空")
    @Schema(description = "医嘱类型：MED_REQUEST / SERVICE_REQUEST", required = true)
    private String orderType;

    /** 药品/诊疗申请主键。 */
    @NotNull(message = "申请主键不能为空")
    /** 药品/诊疗申请主键。 */
    @Schema(description = "药品/诊疗申请主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;
}
