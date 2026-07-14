/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.patient.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 患者学籍扩展信息 DTO：与患者主档一对一，用于校医院等场景下的学籍维护与回显。
 *
 * <p>姓名、证件号、拼音五笔等权威字段来自患者主表，保存时由应用层与领域服务回填，请求体不可覆盖。</p>
 */
@Data
public class PatientStudentDto {

    /** 学籍主键 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联患者 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 姓名（来自患者） */
    private String name;

    /** 拼音码（保存时由患者姓名在后端生成） */
    private String pyStr;

    /** 五笔码（保存时由患者姓名在后端生成） */
    private String wbStr;

    /** 性别（与患者 gender_enum 一致） */
    private String gender;

    /** 年龄 */
    private Integer age;

    /** 学号 */
    private String studentId;

    /** 身份证号（来自患者证件号） */
    private String idNumber;

    /** 电话 */
    private String phone;

    /** 学院 */
    private String college;

    /** 专业 */
    private String major;

    /** 学历层次（EducationLevel） */
    private Integer educationLevel;

    /** 入校时间 yyyy-MM-dd HH:mm:ss */
    private String enrollmentDate;

    /** 离校时间 yyyy-MM-dd HH:mm:ss */
    private String graduationDate;

    /** 年级 */
    private String grade;

    /** 学习形式（StudyMode） */
    private Integer studyMode;

    /** 在校状态（StudentStatus） */
    private Integer studentStatus;

    /** 体检结果（PhysicalExamResult） */
    private Integer physicalExamResult;

    /** 辅导员 */
    private String counselor;

    /** 辅导员电话 */
    private String counselorPhone;
}
