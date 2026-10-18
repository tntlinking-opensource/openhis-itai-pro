/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 名册导入行 DTO。
 */
@Getter
@Setter
public class RosterImportRowDto {

    /** 前端勿传；导入时由后台按身份证匹配或自动建档。 */
    private Long patientId;
    private String fullName;
    /** 性别编码（AdministrativeGender，如 MALE/FEMALE/UNKNOWN）。 */
    private String genderEnum;
    private String studentNo;
    private String employeeNo;
    private Long collegeId;
    private String className;
    private String idCardNo;
    private String mobilePhone;

    /** Excel「分组」列覆盖默认分组名。 */
    private String batchNameOverride;

    /** Excel「套餐」列（顿号/逗号分隔名称）。 */
    private String packageNames;
}
