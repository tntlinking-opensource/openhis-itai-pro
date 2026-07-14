/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.response;

import com.openhis.health.application.finance.priceadjustment.dto.InTransitCheckDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交前在途预检响应（IF-PRC-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交前在途预检响应")
public class CheckInTransitForSubmitResponse {

    /** 在途预检结果。 */
    @Schema(description = "在途预检结果")
    private InTransitCheckDto check;
}
