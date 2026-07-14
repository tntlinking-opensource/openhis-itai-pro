/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import com.openhis.health.infrastructure.integration.payment.dto.ClientRefundResultDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 退号并退费请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退号并退费请求")
public class CancelWithRefundRequest {

    /** 就诊ID（业务字段）。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 取消原因。 */
    @Schema(description = "取消原因")
    private String cancelReason;

    /** 退费意图。 */
    @Schema(description = "退费意图")
    private String refundIntent;

    /** 退费请求键。 */
    @Schema(description = "退费请求键")
    private String refundRequestId;

    /** 银联退货 Win 回传（原扫码段原路退时必填）。 */
    @Schema(description = "银联退货 Win 回传")
    private ClientRefundResultDto clientRefundResult;
}
