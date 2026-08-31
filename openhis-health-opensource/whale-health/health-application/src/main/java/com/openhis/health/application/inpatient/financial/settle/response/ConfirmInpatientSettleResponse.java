/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientPrepayRefundPlanItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 确认住院结算响应（IF-IP-STL-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认住院结算响应")
public class ConfirmInpatientSettleResponse {

    /** 成功支付主键（业务字段）。 */
    @Schema(description = "成功支付主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 发票草稿 ID。 */
    @Schema(description = "发票草稿 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceDraftId;

    /** 票据状态（DRAFT/ISSUED/CANCELLED）。 */
    @Schema(description = "票据状态")
    private String invoiceStatus;

    /** 是否本次为出院全结。 */
    @Schema(description = "是否本次为出院全结")
    private Boolean dischargeFullSettle;

    /** 出院多退预交金支付单 ID；无多退时为 null。 */
    @Schema(description = "出院多退预交金支付单 ID；无多退时为 null")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long advanceRefundPaymentId;

    /** 成功语义码。 */
    @Schema(description = "成功语义码")
    private String successMessageCode;

    /** 医保结算 ID。 */
    @Schema(description = "医保结算 ID（2304 setl_id）")
    private String ybSetlId;

    /** 是否已完成医保正式结算。 */
    @Schema(description = "是否已完成医保正式结算")
    private Boolean insuranceSettled;

    /**
     * 医保出院：结算已落账但预交金扫码原路退待第二阶段完结。
     *
     * <p>为 true 时就诊尚未财务完结，须再调 {@code completeInpatientDischargePrepayRefund}。</p>
     */
    @Schema(description = "预交金原路退是否待第二阶段完结")
    private Boolean prepayRefundPending;

    /** 待原路退的预交金计划（仅 pending 时有值）。 */
    @Schema(description = "待原路退预交金计划")
    private List<InpatientPrepayRefundPlanItemDto> prepayRefundPlan;
}
