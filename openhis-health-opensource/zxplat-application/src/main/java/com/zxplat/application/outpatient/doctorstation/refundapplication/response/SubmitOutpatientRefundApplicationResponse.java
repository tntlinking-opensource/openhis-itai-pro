/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.refundapplication.response;

import com.zxplat.application.outpatient.doctorstation.refundapplication.dto.OutpatientRefundApplicationPaymentSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DRA-03：提交门诊退费申请响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-提交门诊退费申请响应")
public class SubmitOutpatientRefundApplicationResponse {

    @Schema(description = "受理成功")
    /** 进入 REFUNDING 的拟退行数。 */
    private boolean success;

    @Schema(description = "进入 REFUNDING 的拟退行数")
    private int refundingChargeItemCount;

    /** 按支付分组的执行衔接摘要。 */
    @Schema(description = "按支付分组的执行衔接摘要")
    private List<OutpatientRefundApplicationPaymentSummaryDto> paymentSummaries;
}
