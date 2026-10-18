/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.patientstaff.service;

import com.zxplat.plugin.campus.domain.campusdepartment.entity.CampusDepartment;
import com.zxplat.plugin.campus.domain.campusdepartment.repository.CampusDepartmentRepository;
import com.zxplat.plugin.campus.domain.campussite.entity.CampusSite;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.plugin.campus.domain.patientstaff.entity.PatientStaff;
import com.zxplat.plugin.campus.domain.patientstaff.repository.PatientStaffRepository;
import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 教职工档案写侧。
 */
@Service
@RequiredArgsConstructor
public class PatientStaffDomainService {

    private final PatientStaffRepository repository;
    private final CampusDepartmentRepository departmentRepository;
    private final CampusSiteRepository campusSiteRepository;

    public PatientStaff create(PatientStaff staff, Long operatorId) {
        Long tenantId = resolveTenantId(staff.getTenantId());
        staff.setTenantId(tenantId);
        validateEmployeeNoUnique(tenantId, staff.getEmployeeNo(), null);
        validateMedicalCodeUnique(tenantId, staff.getMedicalCode(), null);
        validateDepartmentAndCampus(tenantId, staff.getDepartmentId(), staff.getCampusSiteId());
        applyDefaults(staff);
        touchAudit(staff, operatorId, true);
        return repository.save(staff);
    }

    public PatientStaff update(PatientStaff incoming, Long operatorId) {
        Long tenantId = resolveTenantId(incoming.getTenantId());
        PatientStaff existing = requireStaff(tenantId, incoming.getId());
        validateEmployeeNoUnique(tenantId, incoming.getEmployeeNo(), existing.getId());
        validateMedicalCodeUnique(tenantId, incoming.getMedicalCode(), existing.getId());
        validateDepartmentAndCampus(tenantId, incoming.getDepartmentId(), incoming.getCampusSiteId());

        existing.setName(incoming.getName());
        existing.setPyStr(incoming.getPyStr());
        existing.setWbStr(incoming.getWbStr());
        existing.setGenderEnum(incoming.getGenderEnum());
        existing.setIdCard(incoming.getIdCard());
        existing.setPhone(incoming.getPhone());
        existing.setEmployeeNo(incoming.getEmployeeNo());
        existing.setMedicalCode(incoming.getMedicalCode());
        existing.setDepartmentId(incoming.getDepartmentId());
        existing.setCampusSiteId(incoming.getCampusSiteId());
        existing.setStaffTypeEnum(incoming.getStaffTypeEnum());
        existing.setValidFrom(incoming.getValidFrom());
        existing.setValidTo(incoming.getValidTo());
        existing.setJobStatusEnum(
            incoming.getJobStatusEnum() != null ? incoming.getJobStatusEnum() : existing.getJobStatusEnum());
        if (incoming.getActiveFlag() != null) {
            existing.setActiveFlag(incoming.getActiveFlag());
        }
        touchAudit(existing, operatorId, false);
        return repository.save(existing);
    }

    public PatientStaff disable(Long tenantId, Long id, Long operatorId) {
        PatientStaff staff = requireStaff(resolveTenantId(tenantId), id);
        staff.setActiveFlag(Boolean.FALSE);
        staff.setIsDeleted(Boolean.TRUE);
        staff.setDeletedAt(LocalDateTime.now());
        staff.setDeletedBy(operatorId);
        touchAudit(staff, operatorId, false);
        return repository.save(staff);
    }

    public PatientStaff bindPatient(Long tenantId, Long staffId, Long patientId, Long operatorId) {
        PatientStaff staff = requireStaff(resolveTenantId(tenantId), staffId);
        if (patientId == null) {
            throw new BusinessException("PATIENT_ID_REQUIRED", "患者主键不能为空");
        }
        repository.findByTenantIdAndPatientIdAndIsDeletedFalse(staff.getTenantId(), patientId).ifPresent(existing -> {
            if (!existing.getId().equals(staff.getId())) {
                throw new BusinessException("PATIENT_ALREADY_BOUND", "该患者已绑定其他教职工档案");
            }
        });
        staff.setPatientId(patientId);
        touchAudit(staff, operatorId, false);
        return repository.save(staff);
    }

    private void validateEmployeeNoUnique(Long tenantId, String employeeNo, Long excludeId) {
        repository.findByTenantIdAndEmployeeNoAndIsDeletedFalse(tenantId, employeeNo).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new BusinessException("EMPLOYEE_NO_DUPLICATE", "工号已存在");
            }
        });
    }

    private void validateMedicalCodeUnique(Long tenantId, String medicalCode, Long excludeId) {
        if (medicalCode == null || medicalCode.isBlank()) {
            return;
        }
        repository.findByTenantIdAndMedicalCodeAndIsDeletedFalse(tenantId, medicalCode).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new BusinessException("MEDICAL_CODE_DUPLICATE", "医疗证号已存在");
            }
        });
    }

    private void validateDepartmentAndCampus(Long tenantId, Long departmentId, Long campusSiteId) {
        if (departmentId == null) {
            throw new BusinessException("DEPT_NOT_FOUND", "部门不存在");
        }
        if (campusSiteId == null) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "校区不存在");
        }
        CampusDepartment department = departmentRepository.findByIdAndTenantIdAndIsDeletedFalse(departmentId, tenantId)
            .orElseThrow(() -> new BusinessException("DEPT_NOT_FOUND", "部门不存在"));
        if (!Boolean.TRUE.equals(department.getActiveFlag())) {
            throw new BusinessException("DEPT_INACTIVE", "部门未启用");
        }
        if (!campusSiteId.equals(department.getCampusSiteId())) {
            throw new BusinessException("DEPT_CAMPUS_MISMATCH", "部门与校区归属不一致");
        }
        CampusSite site = campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(campusSiteId, tenantId)
            .orElseThrow(() -> new BusinessException("CAMPUS_NOT_FOUND", "校区不存在"));
        if (!Boolean.TRUE.equals(site.getActiveFlag())) {
            throw new BusinessException("CAMPUS_INACTIVE", "校区未启用");
        }
    }

    private PatientStaff requireStaff(Long tenantId, Long id) {
        if (id == null) {
            throw new BusinessException("STAFF_NOT_FOUND", "教职工档案不存在");
        }
        return repository.findByIdAndTenantIdAndIsDeletedFalse(id, tenantId)
            .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "教职工档案不存在"));
    }

    private void applyDefaults(PatientStaff staff) {
        if (staff.getJobStatusEnum() == null) {
            staff.setJobStatusEnum(CampusDomainConstants.JOB_STATUS_ACTIVE);
        }
        if (staff.getActiveFlag() == null) {
            staff.setActiveFlag(Boolean.TRUE);
        }
    }

    private void touchAudit(PatientStaff staff, Long operatorId, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            staff.setCreatedAt(now);
            staff.setCreatedBy(operatorId);
            staff.setIsDeleted(Boolean.FALSE);
        }
        staff.setUpdatedAt(now);
        staff.setUpdatedBy(operatorId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
