/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.response;

import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.PatientDetailDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 患者详情查询响应（IF-IP-NUR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者详情查询响应")
public class GetPatientDetailResponse {

    @Schema(description = "患者全景详情")
    /** 患者全景详情。 */
    private PatientDetailDto detail;
}
