/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * IF-DOR-06B 扩展：点击未匹配慢病备案时，幂等补录门诊慢特病种目录并返回可导入定义。
 */
@Data
@Schema(description = "确保慢病备案病种目录可导入请求")
public class EnsureOpspFilingDiagnosisRequest {

    /** 就诊标识。 */
    @Schema(description = "就诊标识")
    private Long encounterId;

    /** 患者标识。 */
    @Schema(description = "患者标识")
    private Long patientId;

    /** 门慢门特病种目录代码（5301 opsp_dise_code）。 */
    @Schema(description = "门慢门特病种目录代码")
    private String opspDiseCode;

    /** 门慢门特病种名称（5301 opsp_dise_name；可与平台行核对）。 */
    @Schema(description = "门慢门特病种名称")
    private String opspDiseName;
}
