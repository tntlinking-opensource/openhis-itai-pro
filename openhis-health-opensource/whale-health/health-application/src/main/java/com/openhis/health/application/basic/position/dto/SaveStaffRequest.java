/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.position.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 保存系统用户 + 医务人员 + 岗位分配一体请求（对齐 openhis saveUserPractitioner）。
 */
@Data
public class SaveStaffRequest {

    /** 平台用户主键；新建时可为 null。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 登录账号。 */
    private String userName;
    /** 登录密码；新建必填，更新时为空表示不修改。 */
    private String password;
    /** 用户显示姓名。 */
    private String name;
    /** 联系邮箱。 */
    private String email;
    /** 联系电话。 */
    private String phoneNumber;
    /** 是否启用平台账号。 */
    private Boolean isActive;

    /** 平台角色主键列表。 */
    private List<Long> roleIds;

    /** 医务人员主键；与平台用户一对一绑定。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /** 性别枚举 code（{@code AdministrativeGender}）。 */
    private String genderEnum;
    /** 医务人员工号/业务编码。 */
    private String busNo;

    /** 出诊科室（{@code DOCTOR}）。 */
    private List<StaffOrgLocationItemDto> doctorVisitOrgList;

    /** 可操作药库（{@code WAREHOUSE_ADMIN} → {@code adm_pr_assign.location_id}，{@code adm_location.form_enum=WAREHOUSE}）。 */
    private List<StaffOrgLocationItemDto> warehouseLocationList;

    /** 可操作药房/科室药房（{@code PHARMACIST} → {@code adm_pr_assign.location_id}，{@code adm_location.form_enum} 为 PHARMACY 或 DEPARTMENT_PHARMACY）。 */
    private List<StaffOrgLocationItemDto> pharmacyLocationList;

    /**
     * 工作科室（界面称「工作科室」）。
     * 护士角色仅护士站，医技角色仅医技科室；与管理病区无关。
     */
    private List<StaffOrgLocationItemDto> nurseOrgList;

    /** 管理病区（{@code NURSE}，按 location）。 */
    private List<StaffOrgLocationItemDto> nurseWardLocationList;

    /** 医生可接诊号源类型（HealthcareService 主键）；写入各出诊科室岗位。 */
    private List<Long> doctorServiceIds;

    /**
     * 默认登录科室（{@code adm_practitioner.org_id}）；未填时登录回落当前医院。
     */
    private Long primaryOrgId;

    /** 为 true 时按 {@link #primaryOrgId} 更新默认登录科室（含置空）。 */
    private Boolean syncPrimaryOrgId;

    /** 医保人员编号（{@code adm_practitioner.yb_no}）。 */
    private String ybNo;

    /** 职称编码（{@code adm_practitioner.dr_profttl_code}）。 */
    private Integer drProfttlCode;

    /** 医师/药师执业资格证编号（{@code adm_practitioner.phar_prac_cert_no}）。 */
    private String pharPracCertNo;

    /**
     * 用户扩展资料 JSON（过渡期兼容；显式字段优先）。
     * 可含 {@code pharPracCertNo}、{@code drProfttlCode}、{@code ybNo} 等。
     */
    private String extraProperties;
}
