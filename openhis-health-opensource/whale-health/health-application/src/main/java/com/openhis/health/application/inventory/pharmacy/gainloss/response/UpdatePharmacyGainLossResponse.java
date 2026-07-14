/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.gainloss.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 编辑药房损益单出参（IF-PHA-GL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "编辑药房损益单出参")
public class UpdatePharmacyGainLossResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
