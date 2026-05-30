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

package com.openhis.health.application.inpatient.nursestation.ATDManager.response;

import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.BedBoardItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NUR-ATD-01：床位列表查询响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "床位列表查询响应")
public class ListBedBoardResponse {

    /** 范围内全部启用床位行（位置块 + 可选占床患者块）。 */
    @Schema(description = "床位列表项")
    private List<BedBoardItemDto> items;
}
