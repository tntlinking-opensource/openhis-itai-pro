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

/**
 * 岗位分配行（adm_pr_assign），用于工作台列表展示与行编辑。
 */
@Data
/**
 * PractitionerAssignmentRowDto。
 */
public class PractitionerAssignmentRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * assignmentId。
     */
    private Long assignmentId;
    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * practitionerId。
     */
    private Long practitionerId;

    /** 业务角色编码：1 医师 2 护士 3 药师 4 库管 5 技师 6 行政（与 DM-ADM-006 一致） */
    /**
     * roleCode。
     */
    private Integer roleCode;
    /** 角色中文说明 */
    /**
     * roleName。
     */
    private String roleName;

    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * orgId。
     */
    private Long orgId;
    /**
     * orgName。
     */
    private String orgName;

    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * locationId。
     */
    private Long locationId;
    /**
     * locationName。
     */
    private String locationName;

    /**
     * name。
     */
    private String name;
    /**
     * activeFlag。
     */
    private Boolean activeFlag;
    /**
     * specialtyCode。
     */
    private String specialtyCode;
    /**
     * typeCode。
     */
    private Integer typeCode;
}
