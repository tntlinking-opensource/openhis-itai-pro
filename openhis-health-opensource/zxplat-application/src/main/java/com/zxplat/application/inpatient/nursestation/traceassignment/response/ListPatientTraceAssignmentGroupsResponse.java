/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.response;

import com.zxplat.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-TRC-02：加载患者药批汇总待分配行出参。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载患者药批汇总待分配行出参")
public class ListPatientTraceAssignmentGroupsResponse {

    /** 汇总组列表。 */
    @Schema(description = "汇总组列表")
    private List<PatientTraceAssignmentGroupDto> groups;
}
