/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊正式结算响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊正式结算响应")
public class ConfirmOutpatientChargeSettlementResponse {
    /** 正式支付主键（业务字段）。 */
    @Schema(description = "正式支付主键")
    private Long paymentId;
    /** 票据草稿主键（业务字段）。 */
    @Schema(description = "票据草稿主键")
    private Long invoiceId;
    /** 票据状态（业务字段）。 */
    @Schema(description = "票据状态（DRAFT/ISSUED/CANCELLED）")
    private String invoiceStatus;
    /** 成功语义码。 */
    @Schema(description = "成功语义码")
    private String successMessageCode;
    /** 医保结算 ID（2207 setl_id，多组逗号分隔） */
    @Schema(description = "医保结算 ID")
    private String ybSetlId;
}
