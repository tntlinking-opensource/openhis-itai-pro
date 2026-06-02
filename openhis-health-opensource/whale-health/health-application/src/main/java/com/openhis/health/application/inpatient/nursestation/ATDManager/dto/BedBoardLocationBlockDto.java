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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 床位列表 — 位置块（BR-IP-ATD-001）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "床位位置块")
public class BedBoardLocationBlockDto {

    @Schema(description = "床位位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    @Schema(description = "床号/名称")
    private String bedName;

    @Schema(description = "所属病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;

    @Schema(description = "病房名称")
    private String houseName;

    @Schema(description = "床运营态 code：IN_USE/NOT_IN_USE")
    private String operationalEnum;

    @Schema(description = "排序序号")
    private Integer displayOrder;
}
