/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.patient.request;

import lombok.Data;

/**
 * 保存患者学籍（新增或更新）。姓名、性别、证件号、拼音五笔由后端从患者主表带入，请求体无需传 name/gender/idNumber/py/wb。
 */
@Data
public class SavePatientStudentRequest {

    /** 患者主键 */
    private Long patientId;

    /** 年龄 */
    private Integer age;

    /** 学号 */
    private String studentId;

    /** 电话 */
    private String phone;

    /** 学院 */
    private String college;

    /** 专业 */
    private String major;

    /** 学历层次 */
    private Integer educationLevel;

    /** 入校时间 yyyy-MM-dd HH:mm:ss */
    private String enrollmentDate;

    /** 离校时间 yyyy-MM-dd HH:mm:ss */
    private String graduationDate;

    /** 年级 */
    private String grade;

    /** 学习形式 */
    private Integer studyMode;

    /** 在校状态 */
    private Integer studentStatus;

    /** 体检结果 */
    private Integer physicalExamResult;

    /** 辅导员 */
    private String counselor;

    /** 辅导员电话 */
    private String counselorPhone;
}
