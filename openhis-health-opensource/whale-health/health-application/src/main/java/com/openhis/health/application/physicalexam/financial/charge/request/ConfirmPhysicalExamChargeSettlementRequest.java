/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.physicalexam.financial.charge.dto.PhysicalExamPaymentDetailSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 体检正式结算请求（IF-PE-CHG-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检正式结算请求")
public class ConfirmPhysicalExamChargeSettlementRequest {

    /** 预结算返回的支付草稿主键。 */
    @Schema(description = "支付草稿主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;

    /** 体检就诊主键。 */
    @Schema(description = "体检就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 本次结算收费项主键列表。 */
    @Schema(description = "收费项主键列表")
    private List<Long> chargeItemIds;

    /** 支付渠道明细。 */
    @Schema(description = "支付渠道明细")
    private List<PhysicalExamPaymentDetailSubmitDto> paymentDetails;

    /** 账单日期。 */
    @Schema(description = "账单日期")
    private LocalDateTime billDate;

    /** 结算结果枚举 code。 */
    @Schema(description = "结算结果枚举 code")
    private String outcomeEnum;
}
