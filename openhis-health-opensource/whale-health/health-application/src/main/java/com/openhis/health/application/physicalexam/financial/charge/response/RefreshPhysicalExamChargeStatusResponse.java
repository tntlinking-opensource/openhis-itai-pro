/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 刷新体检收费态响应（IF-PE-CHG-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "刷新体检收费态响应")
public class RefreshPhysicalExamChargeStatusResponse {

    /** 是否已结清。 */
    @Schema(description = "是否已结清")
    private Boolean paid;

    /** 是否未结清。 */
    @Schema(description = "是否未结清")
    private Boolean unpaid;

    /** 是否未知（读失败时不阻断登记）。 */
    @Schema(description = "是否未知")
    private Boolean unknown;
}
