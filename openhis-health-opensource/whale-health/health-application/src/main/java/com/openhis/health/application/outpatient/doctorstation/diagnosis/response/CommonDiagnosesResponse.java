/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-06A：四类常用诊断响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-常用诊断四类列表响应")
public class CommonDiagnosesResponse {

    @Schema(description = "病人历史诊断")
    /** 医生常用诊断。 */
    private List<CommonDiagnosisRowDto> patientHistoryList;

    @Schema(description = "医生常用诊断")
    private List<CommonDiagnosisRowDto> doctorCommonUseList;

    /** 用户个人诊断。 */
    @Schema(description = "用户个人诊断")
    private List<CommonDiagnosisRowDto> userPersonalList;

    /** 科室诊断。 */
    @Schema(description = "科室诊断")
    private List<CommonDiagnosisRowDto> organizationList;
}
