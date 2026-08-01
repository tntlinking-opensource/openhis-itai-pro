/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检名册行 DTO。
 */
@Getter
@Setter
public class GroupRosterRowDto {

    private Long rosterId;
    private Long batchId;
    private Long patientId;
    private String fullName;
    private String idCardNo;
    private String studentNo;
    private String employeeNo;
    private Long collegeId;
    private String className;
    private String mobilePhone;
    private Long encounterId;
    private String payStatusCode;
    private String arrivalStatusCode;
}
