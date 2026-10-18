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
public class UpdateStaffRequest {

    private Long tenantId;

    @NotNull
    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    private String employeeNo;

    @NotNull
    private Long departmentId;

    @NotNull
    private Long campusSiteId;

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
}
