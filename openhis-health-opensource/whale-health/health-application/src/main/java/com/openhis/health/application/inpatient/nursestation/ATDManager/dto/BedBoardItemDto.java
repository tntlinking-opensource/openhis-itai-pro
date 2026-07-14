/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 床位列表单行：位置块 + 占床患者块（空床时 occupant 为 null）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "床位列表项")
public class BedBoardItemDto {

    @Schema(description = "床位位置块")
    /** 床位位置块。 */
    private BedBoardLocationBlockDto location;

    @Schema(description = "占床患者块；空床为 null")
    /** 占床患者块；空床为 null。 */
    private BedBoardOccupantDto occupant;
}
