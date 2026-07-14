/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 退号并退费响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退号并退费响应")
public class CancelWithRefundResponse {

    /** 就诊状态（业务字段）。 */
    @Schema(description = "就诊状态")
    private String encounterStatus;

    /** 退款状态。 */
    @Schema(description = "退款状态")
    private String refundStatus;

    /** 红票 ID。 */
    @Schema(description = "红票ID")
    private Long redInvoiceId;
}
