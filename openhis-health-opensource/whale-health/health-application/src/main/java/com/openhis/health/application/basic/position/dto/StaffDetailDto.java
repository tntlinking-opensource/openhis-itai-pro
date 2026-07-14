/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.position.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.whale.identity.application.dto.RoleDto;
import lombok.Data;

import java.util.List;

/**
 * 岗位人员维护详情：用户 + 医务人员 + 六类岗位分配。
 */
@Data
public class StaffDetailDto {

    /** 平台用户主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 登录账号。 */
    private String userName;
    /** 用户显示姓名。 */
    private String name;
    /** 联系邮箱。 */
    private String email;
    /** 联系电话。 */
    private String phoneNumber;
    /** 平台账号是否启用。 */
    private Boolean isActive;

    /** 已分配的平台角色列表。 */
    private List<RoleDto> systemRoles;

    /** 绑定的医务人员主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /** 医务人员姓名。 */
    private String practitionerName;
    /** 医务人员工号。 */
    private String busNo;
    /** 医保人员编号。 */
    private String ybNo;
    /** 职称编码。 */
    private Integer drProfttlCode;
    /** 执业资格证编号。 */
    private String pharPracCertNo;
    /** 性别枚举 code。 */
    private String genderEnum;
    /** 医务人员状态枚举 code。 */
    private String statusEnum;

    /** 默认登录科室 {@code adm_organization.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long primaryOrgId;

    /** 出诊科室岗位列表。 */
    private List<StaffOrgLocationItemDto> doctorVisitOrgList;
    /** 可操作药库岗位列表。 */
    private List<StaffOrgLocationItemDto> warehouseLocationList;
    /** 可操作药房岗位列表。 */
    private List<StaffOrgLocationItemDto> pharmacyLocationList;
    /** 护士管理科室岗位列表。 */
    private List<StaffOrgLocationItemDto> nurseOrgList;
    /** 护士管理病区岗位列表。 */
    private List<StaffOrgLocationItemDto> nurseWardLocationList;

    /** 医生岗位关联的号源类型（各出诊科室岗位并集去重）。 */
    private List<Long> doctorServiceIds;
}
