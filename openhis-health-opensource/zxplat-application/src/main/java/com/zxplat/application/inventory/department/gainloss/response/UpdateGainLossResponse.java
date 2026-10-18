/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.gainloss.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 编辑科室药房损益单出参（IF-DEPT-GL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "编辑科室药房损益单出参")
public class UpdateGainLossResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
