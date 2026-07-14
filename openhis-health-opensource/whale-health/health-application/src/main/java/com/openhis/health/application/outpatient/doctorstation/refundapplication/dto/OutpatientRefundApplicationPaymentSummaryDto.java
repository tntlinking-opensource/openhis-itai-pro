/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.refundapplication.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DRA-03：按支付主单分组的执行侧衔接摘要（供收费窗退费执行消费）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-按支付分组的执行衔接摘要")
public class OutpatientRefundApplicationPaymentSummaryDto {

    @Schema(description = "原支付主单主键")
    /** 拟退收费明细主键列表。 */
    private Long paymentId;

    @Schema(description = "拟退收费明细主键列表")
    private List<Long> refundingChargeItemIds;

    /** 保留再生 PLANNED 收费明细主键列表。 */
    @Schema(description = "保留再生 PLANNED 收费明细主键列表")
    private List<Long> preservedNewChargeItemIds;

    /** 是否产品半退。 */
    @Schema(description = "是否产品半退")
    private boolean halfRefundOnPayment;
}
