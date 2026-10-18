/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检名册行 DTO。
 */
@Getter
@Setter
public class GroupRosterRowDto {

    private Long rosterId;
    private Long batchId;
    private Long companyId;
    private Long patientId;
    private String fullName;
    private String genderEnum;
    private String idCardNo;
    private String studentNo;
    private String employeeNo;
    private Long collegeId;
    private String className;
    private String mobilePhone;
    private Long encounterId;
    private String payStatusCode;
    private String arrivalStatusCode;
    /** 可选套餐 AD 列表。 */
    private List<Long> optionalPackageActivityIds = new ArrayList<>();
    /** 已选定套餐 AD。 */
    private Long selectedPackageActivityId;
}
