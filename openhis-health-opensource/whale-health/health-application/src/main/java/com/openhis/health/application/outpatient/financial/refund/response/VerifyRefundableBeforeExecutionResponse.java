/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 执行前可退门禁响应（IF-REF-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "执行前可退门禁响应")
public class VerifyRefundableBeforeExecutionResponse {

    /** 是否允许继续执行退费（业务字段）。 */
    @Schema(description = "是否允许继续执行退费")
    private boolean allowed;

    /** 拒绝原因语义码（如 EX-REF-201）；允许时为 null。 */
    @Schema(description = "拒绝原因语义码，如 EX-REF-201")
    private String rejectReasonCode;
}
