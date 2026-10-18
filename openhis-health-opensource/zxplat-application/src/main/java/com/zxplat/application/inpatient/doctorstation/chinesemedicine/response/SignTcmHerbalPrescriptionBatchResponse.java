/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IF-IP-TCM-37：签发中药处方响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "签发中药处方响应")
public class SignTcmHerbalPrescriptionBatchResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 方标识到处方号映射。 */
    @Schema(description = "方标识到处方号映射")
    private Map<String, String> prescriptionNoByGroupKey = new LinkedHashMap<>();
}
