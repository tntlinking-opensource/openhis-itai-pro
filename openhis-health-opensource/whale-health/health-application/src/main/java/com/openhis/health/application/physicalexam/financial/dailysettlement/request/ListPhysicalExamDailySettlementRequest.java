/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.dailysettlement.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 查询体检日结清单请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询体检日结清单请求")
public class ListPhysicalExamDailySettlementRequest {

    /**
     * 收费业务日起（yyyy-MM-dd，闭区间）。
     */
    @Schema(description = "收费业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /**
     * 收费业务日止（yyyy-MM-dd，闭区间）。
     */
    @Schema(description = "收费业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;
}
