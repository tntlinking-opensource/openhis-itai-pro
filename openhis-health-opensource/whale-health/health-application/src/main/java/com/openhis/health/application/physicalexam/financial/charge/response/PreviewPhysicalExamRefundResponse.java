/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.physicalexam.financial.charge.dto.PhysicalExamPreSettleLinePreviewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 体检退费预览响应（IF-PE-CHG-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检退费预览响应")
public class PreviewPhysicalExamRefundResponse {

    /** 原支付主单主键。 */
    @Schema(description = "原支付主单主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalPaymentId;

    /** 应退金额（结算 2 位；原路返回合计）。 */
    @Schema(description = "应退金额")
    private BigDecimal refundableAmount;

    /** 原支付落库渠道码（去重，供银联原路退判定）。 */
    @Schema(description = "原支付落库渠道码")
    private List<String> originalPayChannelCodes;

    /** 拟退收费明细主键（原支付整单）。 */
    @Schema(description = "拟退收费明细主键")
    private List<Long> refundingChargeItemIds;

    /** 拟退行预览。 */
    @Schema(description = "拟退行预览")
    private List<PhysicalExamPreSettleLinePreviewDto> previews;
}
