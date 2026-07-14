/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.refundapplication.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DRA-02：耗材执行门禁响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-耗材执行门禁响应")
public class VerifyConsumableExecutionBeforeRefundResponse {

    @Schema(description = "true 表示通过门禁")
    /** 若不通过，填充稳定语义码（如 EX-DRA-201）。 */
    private boolean allowed;

    @Schema(description = "若不通过，填充稳定语义码（如 EX-DRA-201）")
    private String rejectReasonCode;
}
