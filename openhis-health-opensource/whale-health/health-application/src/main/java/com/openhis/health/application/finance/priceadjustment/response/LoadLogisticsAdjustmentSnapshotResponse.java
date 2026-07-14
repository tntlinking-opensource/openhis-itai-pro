/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.response;

import com.openhis.health.application.finance.priceadjustment.dto.LogisticsSnapshotRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 物流批号快照加载响应（IF-PRC-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "物流批号快照加载响应")
public class LoadLogisticsAdjustmentSnapshotResponse {

    /** 批号快照行。 */
    @Schema(description = "批号快照行")
    private List<LogisticsSnapshotRowDto> rows;
}
