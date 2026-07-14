/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    @Schema(description = "父医嘱类型：MED_REQUEST / SERVICE_REQUEST", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 父医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    private String orderType;

    @Schema(description = "父药品/诊疗申请主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 父药品/诊疗申请主键。 */
    private Long requestId;
}
