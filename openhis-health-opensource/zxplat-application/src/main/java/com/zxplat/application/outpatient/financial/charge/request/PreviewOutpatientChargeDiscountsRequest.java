/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊收费列表折后价试算请求（IF-OPC-08）。
 *
 * <p>只读：不落支付草稿、不写收费折扣快照。款员折扣率口径与预结算相同。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊收费列表折后价试算请求")
public class PreviewOutpatientChargeDiscountsRequest {

    /**
     * 就诊主键；收费项须归属该就诊。
     */
    @Schema(description = "就诊主键")
    private Long encounterId;

    /**
     * 拟试算的收费明细主键列表（通常为当前列表待收行）。
     */
    @Schema(description = "收费明细主键列表")
    private List<Long> chargeItemIds;

    /**
     * 款员所选折扣率 (0,1]；仅已配置有效折扣时可传。
     *
     * <p>空表示未选手动折扣：固定率配置行仍按配置折，款员定率行按原价返回。</p>
     */
    @Schema(description = "款员所选折扣率")
    private BigDecimal cashierDiscountRate;
}
