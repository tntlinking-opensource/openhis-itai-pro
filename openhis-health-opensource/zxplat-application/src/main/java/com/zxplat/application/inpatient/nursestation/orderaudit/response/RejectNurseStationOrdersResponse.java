/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderaudit.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量退回响应（IF-IP-AUD-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量退回响应")
public class RejectNurseStationOrdersResponse {

    @Schema(description = "整批成功为 true")
    /** 整批成功为 true。 */
    private Boolean success;

    @Schema(description = "成功处理条数")
    /** 成功处理条数。 */
    private Integer processedCount;
}
