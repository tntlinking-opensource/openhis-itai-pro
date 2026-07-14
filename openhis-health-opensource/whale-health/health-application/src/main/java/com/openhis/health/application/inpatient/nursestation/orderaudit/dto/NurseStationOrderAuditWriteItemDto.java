/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
