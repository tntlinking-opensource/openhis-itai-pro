/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stocktake.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审批驳回盘点出参（IF-DEPT-ST-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "审批驳回盘点 IF-DEPT-ST-09")
public class RejectStocktakeResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}
