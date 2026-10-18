/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.response;

import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationCancelExecuteReturnPatientGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 按患者查询取消执行退药清单响应（IF-IP-SUM-10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消执行退药清单响应")
public class ListCancelExecuteReturnMedicineLinesResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 按就诊分组的退药清单；无退药患者可不出现，或出现空 {@code returnLines}（实现以有行为准）。
     */
    @Schema(description = "按就诊分组的退药清单")
    private List<NurseStationCancelExecuteReturnPatientGroupDto> patientGroups;
}
