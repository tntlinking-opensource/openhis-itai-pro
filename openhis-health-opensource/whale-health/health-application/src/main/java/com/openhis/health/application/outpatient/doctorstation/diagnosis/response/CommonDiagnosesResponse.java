/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.whale.core.application.dto.OptionDto;
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

    /**
     * 门诊诊断类型下拉（医保 {@code med_type} / {@code YbMedType}），供诊断表「诊断类型」列行编辑。
     *
     * <p>仅含门诊向常用码（普通门诊、急诊、慢特病/慢病/特病等），不含住院向码。</p>
     */
    @Schema(description = "门诊诊断类型选项（诊断类型列下拉）")
    private List<OptionDto> medicalTypeOptions;
}
