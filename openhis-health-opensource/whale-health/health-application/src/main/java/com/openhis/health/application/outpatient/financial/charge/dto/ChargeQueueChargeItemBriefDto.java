/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊收费队列行携带的收费明细摘要（与 IF-OPC-03 列表口径一致：待收费/已结算且非挂号锚点）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊收费队列行 — 收费明细摘要")
public class ChargeQueueChargeItemBriefDto {

    /** 收费明细 ID（业务字段）。 */
    @Schema(description = "收费明细 ID")
    private Long chargeItemId;

    /** 收费状态持久化码。 */
    @Schema(description = "收费状态持久化码（ChargeItemStatus.code）")
    private String statusCode;

    /** 收费状态展示文案。 */
    @Schema(description = "收费状态展示文案")
    private String statusText;

    /** 行总金额。 */
    @Schema(description = "行总金额")
    private BigDecimal totalPrice;

    /** 项目名称。 */
    @Schema(description = "项目名称（目录/定价回落口径与 IF-OPC-03 一致）")
    private String itemLabel;
}
