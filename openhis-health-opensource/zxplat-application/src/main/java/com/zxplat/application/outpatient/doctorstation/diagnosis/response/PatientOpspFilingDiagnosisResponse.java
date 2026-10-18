/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.response;

import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.OpspFilingDiagnosisRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-DOR-06B：慢特病备案可导入诊断响应。
 */
@Data
@Schema(description = "慢特病备案可导入诊断响应")
public class PatientOpspFilingDiagnosisResponse {

    /**
     * CHS 门控：false 时前端隐藏「慢病备案」入口。
     */
    @Schema(description = "是否启用慢病备案导入（CHS 门控）")
    private Boolean opspFilingImportEnabled;

    /** 备案候选行。 */
    @Schema(description = "备案候选行")
    private List<OpspFilingDiagnosisRowDto> filingList = new ArrayList<>();
}
