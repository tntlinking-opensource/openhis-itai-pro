/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.gainloss.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 编辑药库损益单响应（IF-WHS-GL-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "编辑药库损益单 IF-WHS-GL-05")
public class UpdateGainLossResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
