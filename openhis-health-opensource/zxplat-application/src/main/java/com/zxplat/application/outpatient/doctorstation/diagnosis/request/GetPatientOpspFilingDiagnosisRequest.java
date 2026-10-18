/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * IF-DOR-06B：查询患者慢特病备案可导入诊断请求。
 */
@Data
@Schema(description = "查询慢特病备案可导入诊断请求")
public class GetPatientOpspFilingDiagnosisRequest {

    /** 就诊标识。 */
    @Schema(description = "就诊标识")
    private Long encounterId;

    /** 患者标识。 */
    @Schema(description = "患者标识")
    private Long patientId;
}
