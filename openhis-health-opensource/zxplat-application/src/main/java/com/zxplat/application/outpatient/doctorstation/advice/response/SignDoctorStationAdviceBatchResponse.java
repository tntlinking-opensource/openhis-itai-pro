/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * IF-DOR-15：签发医嘱批次响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-签发医嘱批次响应")
public class SignDoctorStationAdviceBatchResponse {

    @Schema(description = "是否成功")
    /** 药品请求主键到处方号映射。 */
    private Boolean success;

    @Schema(description = "药品请求主键到处方号映射")
    private Map<Long, String> prescriptionNoByMedicationRequestId;

    /** 提示信息。 */
    @Schema(description = "提示信息")
    private String message;
}
