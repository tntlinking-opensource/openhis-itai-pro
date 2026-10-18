/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.response;

import com.zxplat.application.outpatient.financial.charge.dto.PreviewOutpatientChargeDiscountLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊收费列表折后价试算响应（IF-OPC-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊收费列表折后价试算响应")
public class PreviewOutpatientChargeDiscountsResponse {

    /**
     * 就诊主键。
     */
    @Schema(description = "就诊主键")
    private Long encounterId;

    /**
     * 本批优惠合计（原价−折后）；无打折行为 0。
     */
    @Schema(description = "本批优惠合计")
    private BigDecimal discountAmountTotal;

    /**
     * 行级折后价；与请求收费项顺序一致。
     */
    @Schema(description = "折后价行")
    private List<PreviewOutpatientChargeDiscountLineDto> lines;
}
