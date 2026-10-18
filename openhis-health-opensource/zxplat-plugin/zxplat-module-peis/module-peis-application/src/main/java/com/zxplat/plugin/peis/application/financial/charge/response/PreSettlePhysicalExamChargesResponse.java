/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamPreSettleLinePreviewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 体检预结算响应（IF-PE-CHG-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检预结算响应")
public class PreSettlePhysicalExamChargesResponse {

    /** 支付草稿主键。 */
    @Schema(description = "支付草稿主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;

    /** 应付展示金额（2 位刻度）。 */
    @Schema(description = "应付展示金额")
    private BigDecimal displayAmount;

    /** 预结算行预览。 */
    @Schema(description = "预结算行预览")
    private List<PhysicalExamPreSettleLinePreviewDto> previews;
}
