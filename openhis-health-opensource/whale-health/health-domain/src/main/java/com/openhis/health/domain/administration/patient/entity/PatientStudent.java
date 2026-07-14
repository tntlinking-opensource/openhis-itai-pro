/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 公费学生资质子实体。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_patient_student")
public class PatientStudent extends BaseAuditedEntity {

    /**
     * 学生姓名。
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 拼音码。
     */
    @Column(name = "py_str")
    private String pyStr;

    /**
     * 五笔码。
     */
    @Column(name = "wb_str")
    private String wbStr;

    /**
     * 性别编码。
     */
    @Column(name = "gender_enum")
    private String genderEnum;

    /**
     * 年龄。
     */
    @Column(name = "age")
    private Integer age;

    /**
     * 学号。
     */
    @Column(name = "student_no", nullable = false)
    private String studentNo;

    /**
     * 身份证号。
     */
    @Column(name = "id_card")
    private String idCard;

    /**
     * 联系电话。
     */
    @Column(name = "phone")
    private String phone;

    /**
     * 学院。
     */
    @Column(name = "college")
    private String college;

    /**
     * 专业。
     */
    @Column(name = "major")
    private String major;

    /**
     * 学历层次。
     */
    @Column(name = "edu_level")
    private Integer eduLevel;

    /**
     * 入校时间。
     */
    @Column(name = "enroll_time")
    private LocalDateTime enrollTime;

    /**
     * 离校时间。
     */
    @Column(name = "leave_time")
    private LocalDateTime leaveTime;

    /**
     * 年级。
     */
    @Column(name = "grade")
    private String grade;

    /**
     * 学习形式。
     */
    @Column(name = "study_type")
    private Integer studyType;

    /**
     * 在校状态。
     */
    @Column(name = "school_status")
    private Integer schoolStatus;

    /**
     * 体检结果。
     */
    @Column(name = "physical_exam_result")
    private Integer physicalExamResult;

    /**
     * 辅导员。
     */
    @Column(name = "counselor")
    private String counselor;

    /**
     * 辅导员电话。
     */
    @Column(name = "counselor_phone")
    private String counselorPhone;

    /**
     * 所属患者标识。
     */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /**
     * 校验学籍记录参数。
     */
    public void assertValid() {
        if (StringUtils.isEmpty(this.studentNo)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "学号不能为空");
        }
        if (StringUtils.isEmpty(this.name)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "学生姓名不能为空");
        }
    }

    /**
     * 停用学籍记录。
     *
     * @param operatorId 操作人标识
     */
    public void disable(Long operatorId) {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "学籍记录已停用");
        }
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedAt(LocalDateTime.now());
        this.setDeletedBy(operatorId);
    }
}
