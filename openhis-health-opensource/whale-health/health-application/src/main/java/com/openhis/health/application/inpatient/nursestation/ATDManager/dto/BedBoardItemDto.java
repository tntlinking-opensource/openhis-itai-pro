/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
/** 床位列表项。 */
@Schema(description = "床位列表项")
public class BedBoardItemDto {

    @Schema(description = "床位位置块")
    private BedBoardLocationBlockDto location;

    @Schema(description = "占床患者块；空床为 null")
    private BedBoardOccupantDto occupant;
}
