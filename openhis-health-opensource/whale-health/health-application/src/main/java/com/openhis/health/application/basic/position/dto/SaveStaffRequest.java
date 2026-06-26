/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
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

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String userName;
    private String password;
    private String name;
    private String email;
    private String phoneNumber;
    private Boolean isActive;

    /** 平台角色主键列表。 */
    private List<Long> roleIds;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    private String genderEnum;
    private String busNo;

    /** 出诊科室（{@code DOCTOR}）。 */
    private List<StaffOrgLocationItemDto> doctorVisitOrgList;

    /** 可操作药库（{@code WAREHOUSE_ADMIN} → {@code adm_pr_assign.location_id}，{@code adm_location.form_enum=WAREHOUSE}）。 */
    private List<StaffOrgLocationItemDto> warehouseLocationList;

    /** 可操作药房（{@code PHARMACIST} → {@code adm_pr_assign.location_id}，{@code adm_location.form_enum=PHARMACY}）。 */
    private List<StaffOrgLocationItemDto> pharmacyLocationList;

    /** 护士管理科室（{@code NURSE}，按 org）。 */
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
