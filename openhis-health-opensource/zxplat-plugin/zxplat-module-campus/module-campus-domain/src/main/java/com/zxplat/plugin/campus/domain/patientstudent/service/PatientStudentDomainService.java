/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.patientstudent.service;

import com.zxplat.plugin.campus.domain.campuscollege.entity.CampusCollege;
import com.zxplat.plugin.campus.domain.campuscollege.repository.CampusCollegeRepository;
import com.zxplat.plugin.campus.domain.campussite.entity.CampusSite;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.plugin.campus.domain.patientstudent.entity.PatientStudent;
import com.zxplat.plugin.campus.domain.patientstudent.repository.PatientStudentRepository;
import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 学生档案写侧。
 */
@Service
@RequiredArgsConstructor
public class PatientStudentDomainService {

    private final PatientStudentRepository repository;
    private final CampusCollegeRepository collegeRepository;
    private final CampusSiteRepository campusSiteRepository;

    public PatientStudent create(PatientStudent student, Long operatorId) {
        Long tenantId = resolveTenantId(student.getTenantId());
        student.setTenantId(tenantId);
        validateStudentNoUnique(tenantId, student.getStudentNo(), null);
        CampusCollege college = validateCollege(tenantId, student.getCollegeId());
        validateCampusCode(tenantId, student.getCampusCode());
        student.setCollegeName(college.getCollegeName());
        applyDefaults(student);
        touchAudit(student, operatorId, true);
        return repository.save(student);
    }

    public PatientStudentUpdateResult update(PatientStudent incoming, Long operatorId) {
        Long tenantId = resolveTenantId(incoming.getTenantId());
        PatientStudent existing = requireStudent(tenantId, incoming.getId());
        validateStudentNoUnique(tenantId, incoming.getStudentNo(), existing.getId());

        boolean orgChanged = !Objects.equals(existing.getCollegeId(), incoming.getCollegeId())
            || !Objects.equals(existing.getCampusCode(), incoming.getCampusCode());

        if (orgChanged) {
            CampusCollege college = validateCollege(tenantId, incoming.getCollegeId());
            validateCampusCode(tenantId, incoming.getCampusCode());
            existing.setCollegeId(incoming.getCollegeId());
            existing.setCollegeName(college.getCollegeName());
            existing.setCampusCode(incoming.getCampusCode());
        }

        existing.setName(incoming.getName());
        existing.setPyStr(incoming.getPyStr());
        existing.setWbStr(incoming.getWbStr());
        existing.setGenderEnum(incoming.getGenderEnum());
        existing.setAge(incoming.getAge());
        existing.setBirthday(incoming.getBirthday());
        existing.setStudentNo(incoming.getStudentNo());
        existing.setIdCard(incoming.getIdCard());
        existing.setPhone(incoming.getPhone());
        existing.setMajor(incoming.getMajor());
        existing.setMajorCode(incoming.getMajorCode());
        existing.setEduLevelEnum(incoming.getEduLevelEnum());
        existing.setEnrollTime(incoming.getEnrollTime());
        existing.setLeaveTime(incoming.getLeaveTime());
        existing.setGrade(incoming.getGrade());
        existing.setClassName(incoming.getClassName());
        existing.setSchoolYear(incoming.getSchoolYear());
        existing.setStudyTypeEnum(incoming.getStudyTypeEnum());
        existing.setSchoolStatusEnum(
            incoming.getSchoolStatusEnum() != null ? incoming.getSchoolStatusEnum() : existing.getSchoolStatusEnum());
        existing.setPhysicalExamResultEnum(incoming.getPhysicalExamResultEnum());
        existing.setNativePlace(incoming.getNativePlace());
        existing.setCounselorName(incoming.getCounselorName());
        existing.setCounselorPhone(incoming.getCounselorPhone());
        existing.setAddress(incoming.getAddress());
        existing.setDoorplate(incoming.getDoorplate());
        if (incoming.getActiveFlag() != null) {
            existing.setActiveFlag(incoming.getActiveFlag());
        }
        touchAudit(existing, operatorId, false);
        return new PatientStudentUpdateResult(repository.save(existing), orgChanged);
    }

    public PatientStudentUpdateResult updateReturningOrgChange(PatientStudent incoming, Long operatorId) {
        return update(incoming, operatorId);
    }

    public PatientStudent disable(Long tenantId, Long id, Long operatorId) {
        PatientStudent student = requireStudent(resolveTenantId(tenantId), id);
        student.setActiveFlag(Boolean.FALSE);
        student.setIsDeleted(Boolean.TRUE);
        student.setDeletedAt(LocalDateTime.now());
        student.setDeletedBy(operatorId);
        touchAudit(student, operatorId, false);
        return repository.save(student);
    }

    public PatientStudent bindPatient(Long tenantId, Long studentId, Long patientId, Long operatorId) {
        PatientStudent student = requireStudent(resolveTenantId(tenantId), studentId);
        if (patientId == null) {
            throw new BusinessException("PATIENT_ID_REQUIRED", "患者主键不能为空");
        }
        repository.findByTenantIdAndPatientIdAndIsDeletedFalse(student.getTenantId(), patientId).ifPresent(existing -> {
            if (!existing.getId().equals(student.getId())) {
                throw new BusinessException("PATIENT_ALREADY_BOUND", "该患者已绑定其他学生档案");
            }
        });
        student.setPatientId(patientId);
        touchAudit(student, operatorId, false);
        return repository.save(student);
    }

    private void validateStudentNoUnique(Long tenantId, String studentNo, Long excludeId) {
        repository.findByTenantIdAndStudentNoAndIsDeletedFalse(tenantId, studentNo).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new BusinessException("STUDENT_NO_DUPLICATE", "学号已存在");
            }
        });
    }

    private CampusCollege validateCollege(Long tenantId, Long collegeId) {
        if (collegeId == null) {
            throw new BusinessException("COLLEGE_NOT_FOUND", "学院不存在");
        }
        CampusCollege college = collegeRepository.findByIdAndTenantIdAndIsDeletedFalse(collegeId, tenantId)
            .orElseThrow(() -> new BusinessException("COLLEGE_NOT_FOUND", "学院不存在"));
        if (!Boolean.TRUE.equals(college.getActiveFlag())) {
            throw new BusinessException("COLLEGE_INACTIVE", "学院未启用");
        }
        return college;
    }

    private void validateCampusCode(Long tenantId, String campusCode) {
        if (campusCode == null || campusCode.isBlank()) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "校区不存在");
        }
        CampusSite site = campusSiteRepository.findByTenantIdAndCampusCodeAndIsDeletedFalse(tenantId, campusCode)
            .orElseThrow(() -> new BusinessException("CAMPUS_NOT_FOUND", "校区不存在"));
        if (!Boolean.TRUE.equals(site.getActiveFlag())) {
            throw new BusinessException("CAMPUS_INACTIVE", "校区未启用");
        }
    }

    private PatientStudent requireStudent(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("STUDENT_NOT_FOUND", "学生档案不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("STUDENT_NOT_FOUND", "学生档案不存在"));
    }

    private void applyDefaults(PatientStudent student) {
        if (student.getSchoolStatusEnum() == null) {
            student.setSchoolStatusEnum(CampusDomainConstants.SCHOOL_STATUS_IN_SCHOOL);
        }
        if (student.getActiveFlag() == null) {
            student.setActiveFlag(Boolean.TRUE);
        }
    }

    private void touchAudit(PatientStudent student, Long operatorId, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            student.setCreatedAt(now);
            student.setCreatedBy(operatorId);
            student.setIsDeleted(Boolean.FALSE);
        }
        student.setUpdatedAt(now);
        student.setUpdatedBy(operatorId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
