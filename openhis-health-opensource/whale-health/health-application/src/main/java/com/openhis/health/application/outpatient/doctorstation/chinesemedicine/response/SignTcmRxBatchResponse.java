/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/** IF-DOR-37：签发中药处方批次响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "签发中药处方批次响应")
public class SignTcmRxBatchResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 方标识到处方号映射。 */
    @Schema(description = "方标识到处方号映射")
    private Map<String, String> prescriptionNoByGroupKey = new HashMap<>();
}
