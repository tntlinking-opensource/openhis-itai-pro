/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量校对通过响应（IF-IP-AUD-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量校对通过响应")
public class VerifyNurseStationOrdersResponse {

    @Schema(description = "整批成功为 true")
    /** 整批成功为 true。 */
    private Boolean success;

    @Schema(description = "成功处理条数")
    /** 成功处理条数。 */
    private Integer processedCount;
}
