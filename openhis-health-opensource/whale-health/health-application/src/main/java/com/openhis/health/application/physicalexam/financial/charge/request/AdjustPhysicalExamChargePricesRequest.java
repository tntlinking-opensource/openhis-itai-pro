/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 体检项目改价请求（仅 {@code wkf_service_request} 锚点的待收费明细）。
 */
@Data
@Schema(description = "体检项目改价请求")
public class AdjustPhysicalExamChargePricesRequest {

    @Schema(description = "就诊主键")
    private Long encounterId;

    @Schema(description = "改价明细")
    private List<Item> items;

    @Data
    @Schema(description = "单条改价")
    public static class Item {
        @Schema(description = "收费明细主键")
        private Long chargeItemId;

        @Schema(description = "新单价")
        private BigDecimal unitPrice;

        @Schema(description = "改价原因码")
        private String overrideReasonCode;

        @Schema(description = "改价原因说明")
        private String overrideReasonText;
    }
}
