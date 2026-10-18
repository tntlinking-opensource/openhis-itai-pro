/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstudent.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateStudentRequest {

    private Long tenantId;

    @NotNull
    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    private String studentNo;

    @NotNull
    private Long collegeId;

    @NotBlank
    private String campusCode;

    private String pyStr;
    private String wbStr;
    private String genderEnum;
    private Integer age;
    private LocalDate birthday;
    private String idCard;
    private String phone;
    private String major;
    private String majorCode;
    private String eduLevelEnum;
    private LocalDateTime enrollTime;
    private LocalDateTime leaveTime;
    private String grade;
    private String className;
    private Integer studyYears;
    private Integer schoolYear;
    private String studyTypeEnum;
    private String schoolStatusEnum;
    private String physicalExamResultEnum;
    private String nativePlace;
    private String counselorName;
    private String counselorPhone;
    private String address;
    private String doorplate;
    private Boolean activeFlag;
}
