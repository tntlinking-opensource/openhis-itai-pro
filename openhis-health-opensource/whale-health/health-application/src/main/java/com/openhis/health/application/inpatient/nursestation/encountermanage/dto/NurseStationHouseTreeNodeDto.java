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

package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 护士站左侧树 — 病房子节点。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病房树节点")
public class NurseStationHouseTreeNodeDto {

    /** 病房位置主键。 */
    @Schema(description = "病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 病房名称。 */
    @Schema(description = "病房名称")
    private String name;

    /** 位置编码 bus_no。 */
    @Schema(description = "位置编码")
    private String busNo;

    /** 展示排序。 */
    @Schema(description = "展示排序")
    private Integer displayOrder;
}
