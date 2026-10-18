/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.refundapplication.response;

import com.zxplat.application.outpatient.doctorstation.refundapplication.dto.RefundableChargeLineItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DRA-01：可退账单行列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-可退账单行列表响应")
public class ListRefundableChargeLinesForEncounterResponse {

    @Schema(description = "可退候选行（按支付分组排序）")
    /** 可退候选行（按支付分组排序）。 */
    private List<RefundableChargeLineItemDto> lines;
}
