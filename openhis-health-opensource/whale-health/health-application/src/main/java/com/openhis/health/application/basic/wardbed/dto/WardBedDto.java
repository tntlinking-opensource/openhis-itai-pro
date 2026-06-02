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

package com.openhis.health.application.basic.wardbed.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 病区/病房/床位位置展示 DTO，可嵌套子床位与父级快照。
 */
@Data
public class WardBedDto {
    /** 位置主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 业务编号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 状态枚举 code。 */
    private String statusEnum;
    /** 状态展示文案。 */
    private String statusEnumText;
    /** 物理形态枚举 code。 */
    private String formEnum;
    /** 物理形态展示文案。 */
    private String formEnumText;
    /** 所属机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
    /** 显示顺序。 */
    private Integer displayOrder;
    /** 子位置（如病房下的床位）。 */
    private List<WardBedDto> children;
    /** 父位置快照（如床位所属病房）。 */
    private WardBedDto parent;
}
