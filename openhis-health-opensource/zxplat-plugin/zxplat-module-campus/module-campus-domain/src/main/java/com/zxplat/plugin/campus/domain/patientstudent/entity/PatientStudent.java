/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.patientstudent.entity;

import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学生学籍档案（cmp_patient_student）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_patient_student")
public class PatientStudent extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "py_str", length = 64)
    private String pyStr;

    @Column(name = "wb_str", length = 64)
    private String wbStr;

    @Column(name = "gender_enum", length = 32)
    private String genderEnum;

    @Column(name = "age")
    private Integer age;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "student_no", nullable = false, length = 64)
    private String studentNo;

    @Column(name = "id_card", length = 32)
    private String idCard;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "college_id", nullable = false)
    private Long collegeId;

    @Column(name = "college_name", length = 128)
    private String collegeName;

    @Column(name = "campus_code", nullable = false, length = 64)
    private String campusCode;

    @Column(name = "major", length = 128)
    private String major;

    @Column(name = "major_code", length = 64)
    private String majorCode;

    @Column(name = "edu_level_enum", length = 32)
    private String eduLevelEnum;

    @Column(name = "enroll_time")
    private LocalDateTime enrollTime;

    @Column(name = "leave_time")
    private LocalDateTime leaveTime;

    @Column(name = "grade", length = 32)
    private String grade;

    /** 行政班（团检按班级筛名册）。 */
    @Column(name = "class_name", length = 128)
    private String className;

    /** 学制（年），如本科 4；供小程序展开学年体检。 */
    @Column(name = "study_years")
    private Integer studyYears;

    @Column(name = "school_year")
    private Integer schoolYear;

    @Column(name = "study_type_enum", length = 32)
    private String studyTypeEnum;

    @Column(name = "school_status_enum", nullable = false, length = 32)
    private String schoolStatusEnum = CampusDomainConstants.SCHOOL_STATUS_IN_SCHOOL;

    @Column(name = "physical_exam_result_enum", length = 32)
    private String physicalExamResultEnum;

    @Column(name = "native_place", length = 128)
    private String nativePlace;

    @Column(name = "counselor_name", length = 128)
    private String counselorName;

    @Column(name = "counselor_phone", length = 32)
    private String counselorPhone;

    @Column(name = "address", length = 512)
    private String address;

    @Column(name = "doorplate", length = 64)
    private String doorplate;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;
}
