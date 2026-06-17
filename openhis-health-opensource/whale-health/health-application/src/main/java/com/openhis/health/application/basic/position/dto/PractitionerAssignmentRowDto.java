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

package com.openhis.health.application.basic.position.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 岗位分配行（adm_pr_assign），用于工作台列表展示与行编辑。
 */
@Data
public class PractitionerAssignmentRowDto {

    /** 岗位分配主键（adm_pr_assign）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assignmentId;
    /** 医务人员主档主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /** 平台角色主键 whale_roles.id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    /** 兼容旧前端 API：1=医生 2=护士 3=药师 4=库管 6=行政(财务)。 */
    private Integer roleCode;
    /** 角色中文说明 */
    private String roleName;

    /** 所属组织主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
    /** 所属组织名称。 */
    private String orgName;

    /** 工作位置/科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;
    /** 工作位置/科室名称。 */
    private String locationName;

    /** 医务人员姓名。 */
    private String name;
    /** 岗位是否有效。 */
    private Boolean activeFlag;
    /** 专业编码。 */
    private String specialtyCode;
    /** 人员类型编码。 */
    private Integer typeCode;

    /** 岗位可接诊的号源类型（HealthcareService）主键列表。 */
    private List<Long> serviceIds;
    /** 号源类型名称，顿号分隔，供列表展示。 */
    private String serviceNames;
}
