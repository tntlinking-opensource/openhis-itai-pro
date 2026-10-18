/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.position.dto;

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

    /** 平台角色主键 zxplat_roles.id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

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
