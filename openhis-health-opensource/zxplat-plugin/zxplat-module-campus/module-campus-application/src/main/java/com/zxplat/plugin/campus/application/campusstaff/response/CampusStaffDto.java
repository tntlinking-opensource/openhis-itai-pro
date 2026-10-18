/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstaff.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CampusStaffDto {

    private Long id;
    private Long patientId;
    private String name;
    private String pyStr;
    private String wbStr;
    private String genderEnum;
    private String idCard;
    private String phone;
    private String employeeNo;
    private String medicalCode;
    private Long departmentId;
    private Long campusSiteId;
    private String staffTypeEnum;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String jobStatusEnum;
    private Boolean activeFlag;
}
