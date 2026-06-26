/*
 * Copyright © 2026 OpenHIS Contributors
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
/** 门诊正式结算响应。 */
@Schema(description = "门诊正式结算响应")
public class ConfirmOutpatientChargeSettlementResponse {
    @Schema(description = "正式支付主键")
    /** 正式支付主键（业务字段）。 */
    private Long paymentId;
    @Schema(description = "票据草稿主键")
    /** 票据草稿主键（业务字段）。 */
    private Long invoiceId;
    @Schema(description = "票据状态（DRAFT/ISSUED/CANCELLED）")
    /** 票据状态（业务字段）。 */
    private String invoiceStatus;
    /** 成功语义码。 */
    @Schema(description = "成功语义码")
    /** 成功语义码（业务字段）。 */
    private String successMessageCode;
    /** 医保结算 ID（2207 setl_id，多组逗号分隔） */
    @Schema(description = "医保结算 ID")
    /** 医保结算 ID（业务字段）。 */
    private String ybSetlId;
}
