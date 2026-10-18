/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstaff.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateStaffRequest {

    private Long tenantId;

    @NotBlank
    private String name;

    @NotBlank
    private String employeeNo;

    @NotNull
    private Long departmentId;

    @NotNull
    private Long campusSiteId;

    private Long patientId;
    private String pyStr;
    private String wbStr;
    private String genderEnum;
    private String idCard;
    private String phone;
    private String medicalCode;
    private String staffTypeEnum;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String jobStatusEnum;
    private Boolean activeFlag;

    private String peCardNo;
    private String peMedicalCode;
    private String peGfStatusEnum;
    private String peCategoryEnum;
    private Boolean peEligibleFlag;
    private String peRemark;
}
