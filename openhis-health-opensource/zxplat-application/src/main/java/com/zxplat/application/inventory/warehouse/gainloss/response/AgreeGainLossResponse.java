/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.gainloss.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批同意药库损益单响应（IF-WHS-GL-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批同意药库损益 IF-WHS-GL-08")
public class AgreeGainLossResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 同意后状态 SupplyStatus#getCode。 */
    @Schema(description = "同意后状态 SupplyStatus#getCode")
    private String statusEnum;
}
