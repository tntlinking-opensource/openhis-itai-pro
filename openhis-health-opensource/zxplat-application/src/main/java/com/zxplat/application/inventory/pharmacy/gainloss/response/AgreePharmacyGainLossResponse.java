/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.gainloss.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审批同意损益出参（IF-PHA-GL-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "审批同意损益 IF-PHA-GL-08")
public class AgreePharmacyGainLossResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 状态 statusEnum。 */
    @Schema(description = "状态 statusEnum")
    private String statusEnum;
}
