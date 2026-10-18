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
 * 新建药库损益单响应（IF-WHS-GL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建药库损益单 IF-WHS-GL-04")
public class CreateGainLossResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
