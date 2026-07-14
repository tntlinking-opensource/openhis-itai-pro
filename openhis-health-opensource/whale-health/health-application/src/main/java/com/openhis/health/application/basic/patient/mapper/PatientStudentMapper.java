/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.patient.mapper;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.basic.patient.dto.PatientStudentDto;
import com.openhis.health.application.basic.patient.request.SavePatientStudentRequest;
import com.openhis.health.domain.administration.patient.command.UpsertPatientStudentRecordCommand;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientStudentQueryModel;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 患者学籍相关对象映射器。
 *
 * <p>说明：</p>
 * <ul>
 *     <li>时间字符串统一按 {@link GeneralSharedConstants#DATE_TIME_PATTERN} 解析与输出。</li>
 *     <li>姓名/性别/证件号/拼音五笔等主档字段以 {@link PatientDetailQueryModel#getPatient()} 为权威来源。</li>
 *     <li>学籍 DTO 为“一患一学籍”视角：从 students 列表中取首条记录合并主档字段。</li>
 * </ul>
 */
@Component
public class PatientStudentMapper {

    /**
     * 组装维护学籍领域命令。
     *
     * @param req 前端请求（学籍可编辑字段）
     * @param detail 患者详情（主档字段为权威来源）
     * @param existedStudentId 已存在学籍主键（更新语义）；为空表示新增语义
     * @return 领域命令
     */
    public UpsertPatientStudentRecordCommand toUpsertCommand(SavePatientStudentRequest req,
                                                             PatientDetailQueryModel detail,
                                                             Long existedStudentId) {
        // 入参校验：mapper 只做转换前的最小校验，避免组装出必填缺失的命令对象。
        if (req == null) {
            throw new ValidationException("保存学籍失败：请求体不能为空");
        }
        if (detail == null || detail.getPatient() == null) {
            throw new EntityNotFoundException("患者不存在或已删除");
        }
        if (req.getPatientId() == null) {
            throw new ValidationException("保存学籍失败：patientId 不能为空");
        }
        if (req.getStudentId() == null || req.getStudentId().trim().isEmpty()) {
            throw new ValidationException("保存学籍失败：studentId（学号）不能为空");
        }

        UpsertPatientStudentRecordCommand command = new UpsertPatientStudentRecordCommand();
        UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand item =
            new UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand();
        item.setPatientId(req.getPatientId());
        item.setStudentId(existedStudentId);
        item.setStudentNo(req.getStudentId().trim());

        // 主档权威字段：姓名与证件号随主档变更而同步到学籍。
        item.setName(detail.getPatient().getName());
        item.setIdCard(detail.getPatient().getIdCard());

        // 学籍可编辑字段：直接取请求体。
        item.setPhone(req.getPhone());
        item.setCollege(req.getCollege());
        item.setMajor(req.getMajor());
        item.setEduLevel(req.getEducationLevel());
        item.setEnrollTime(this.parseDateTimeOrNull(req.getEnrollmentDate(), "enrollmentDate"));
        item.setLeaveTime(this.parseDateTimeOrNull(req.getGraduationDate(), "graduationDate"));
        item.setGrade(req.getGrade());
        item.setStudyType(req.getStudyMode());
        item.setSchoolStatus(req.getStudentStatus());
        item.setPhysicalExamResult(req.getPhysicalExamResult());
        item.setCounselor(req.getCounselor());
        item.setCounselorPhone(req.getCounselorPhone());

        List<UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand> items = new ArrayList<>();
        items.add(item);
        command.setItems(items);
        return command;
    }

    /**
     * 将患者详情中的“主档 + 学籍记录列表”合并为单条学籍 DTO。
     *
     * @param detail 患者详情
     * @return 单条学籍 DTO；若无学籍记录则返回仅含主档字段的空学籍 DTO
     */
    public PatientStudentDto toStudentDto(PatientDetailQueryModel detail) {
        if (detail == null || detail.getPatient() == null) {
            throw new EntityNotFoundException("患者不存在或已删除");
        }

        PatientStudentDto dto = new PatientStudentDto();
        dto.setPatientId(detail.getPatient().getId());
        dto.setName(detail.getPatient().getName());
        dto.setGender(detail.getPatient().getGenderEnum());
        dto.setIdNumber(detail.getPatient().getIdCard());
        dto.setPyStr(detail.getPatient().getPyStr());
        dto.setWbStr(detail.getPatient().getWbStr());

        LocalDate birthDate = detail.getPatient().getBirthDate();
        if (birthDate != null) {
            dto.setAge(AgeCalculatorUtil.calculateAge(birthDate));
        }

        // 无学籍记录时仅回传主档派生字段，便于前端回显「未维护学籍」空态。
        if (detail.getStudents() == null || detail.getStudents().isEmpty() || detail.getStudents().get(0) == null) {
            return dto;
        }

        PatientStudentQueryModel student = detail.getStudents().get(0);
        dto.setId(student.getId());
        dto.setStudentId(student.getStudentNo());
        dto.setPhone(student.getPhone());
        dto.setCollege(student.getCollege());
        dto.setMajor(student.getMajor());
        dto.setEducationLevel(student.getEduLevel());
        dto.setEnrollmentDate(this.formatDateTimeOrNull(student.getEnrollTime()));
        dto.setGraduationDate(this.formatDateTimeOrNull(student.getLeaveTime()));
        dto.setGrade(student.getGrade());
        dto.setStudyMode(student.getStudyType());
        dto.setStudentStatus(student.getSchoolStatus());
        dto.setPhysicalExamResult(student.getPhysicalExamResult());
        dto.setCounselor(student.getCounselor());
        dto.setCounselorPhone(student.getCounselorPhone());
        return dto;
    }

    /**
     * 解析时间字符串为 LocalDateTime。
     *
     * @param input 前端入参，格式 {@link GeneralSharedConstants#DATE_TIME_PATTERN}
     * @param fieldName 字段名，用于拼接校验失败提示
     * @return input 为空返回 null；格式非法抛 {@link ValidationException}
     */
    private LocalDateTime parseDateTimeOrNull(String input, String fieldName) {
        // 允许不传时间字段，由业务默认或空值回显。
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(input.trim(), GeneralSharedConstants.DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("保存学籍失败：" + fieldName + " 格式不正确，应为 " + GeneralSharedConstants.DATE_TIME_PATTERN);
        }
    }

    /**
     * 将 LocalDateTime 格式化为前端字符串。
     *
     * @param time 时间值
     * @return time 为空返回 null
     */
    private String formatDateTimeOrNull(LocalDateTime time) {
        // 允许为空，未维护字段不回填默认值。
        if (time == null) {
            return null;
        }
        return GeneralSharedConstants.DATE_TIME_FORMATTER.format(time);
    }
}
