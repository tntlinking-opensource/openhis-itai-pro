/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 名册导入行 DTO。
 */
@Getter
@Setter
public class RosterImportRowDto {

    private Long patientId;
    private String fullName;
    private String studentNo;
    private String employeeNo;
    private Long collegeId;
    private String className;
    private String idCardNo;
    private String mobilePhone;
}
