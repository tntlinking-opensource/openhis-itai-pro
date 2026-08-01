/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.publicexpenseprofile.service;

import com.openhis.campus.domain.campuscollege.repository.CampusCollegeRepository;
import com.openhis.campus.domain.campusdepartment.repository.CampusDepartmentRepository;
import com.openhis.campus.domain.campussite.repository.CampusSiteRepository;
import com.openhis.campus.domain.gfswitch.repository.GfSwitchRepository;
import com.openhis.campus.domain.patientstaff.entity.PatientStaff;
import com.openhis.campus.domain.patientstaff.repository.PatientStaffRepository;
import com.openhis.campus.domain.patientstudent.entity.PatientStudent;
import com.openhis.campus.domain.patientstudent.repository.PatientStudentRepository;
import com.openhis.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import com.openhis.campus.domain.publicexpenseprofile.model.EligibilityResult;
import com.openhis.campus.domain.publicexpenseprofile.repository.PublicExpenseProfileRepository;
import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 公费资格校验（ADR D9 严规则）。
 */
@Service
@RequiredArgsConstructor
public class PublicExpenseEligibilityDomainService {

    private final GfSwitchRepository gfSwitchRepository;
    private final PublicExpenseProfileRepository profileRepository;
    private final PatientStudentRepository studentRepository;
    private final PatientStaffRepository staffRepository;
    private final CampusCollegeRepository collegeRepository;
    private final CampusSiteRepository campusSiteRepository;
    private final CampusDepartmentRepository departmentRepository;

    public EligibilityResult check(Long tenantId, Long patientId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || patientId == null) {
            return EligibilityResult.fail(
                "SUBJECT_NOT_FOUND", "未找到 Campus 档案", null, null, null, null);
        }
        Optional<PatientStudent> student = studentRepository.findByTenantIdAndPatientIdAndIsDeletedFalse(
            resolvedTenantId, patientId);
        if (student.isPresent()) {
            return checkStudent(resolvedTenantId, student.get());
        }
        Optional<PatientStaff> staff = staffRepository.findByTenantIdAndPatientIdAndIsDeletedFalse(
            resolvedTenantId, patientId);
        if (staff.isPresent()) {
            return checkStaff(resolvedTenantId, staff.get());
        }
        return EligibilityResult.fail(
            "SUBJECT_NOT_FOUND", "未找到 Campus 档案", null, null, null, null);
    }

    public EligibilityResult checkBySubject(Long tenantId, String subjectTypeEnum, Long subjectId) {
        Long resolvedTenantId = resolveTenantId(tenantId);
        if (resolvedTenantId == null || subjectTypeEnum == null || subjectId == null) {
            return EligibilityResult.fail(
                "SUBJECT_NOT_FOUND", "未找到 Campus 档案", null, null, null, null);
        }
        if (CampusDomainConstants.SUBJECT_STUDENT.equals(subjectTypeEnum)) {
            return studentRepository.findByIdAndTenantIdAndIsDeletedFalse(subjectId, resolvedTenantId)
                .map(student -> checkStudent(resolvedTenantId, student))
                .orElseGet(() -> EligibilityResult.fail(
                    "SUBJECT_NOT_FOUND", "学生档案不存在", null, null, null, null));
        }
        if (CampusDomainConstants.SUBJECT_STAFF.equals(subjectTypeEnum)) {
            return staffRepository.findByIdAndTenantIdAndIsDeletedFalse(subjectId, resolvedTenantId)
                .map(staff -> checkStaff(resolvedTenantId, staff))
                .orElseGet(() -> EligibilityResult.fail(
                    "SUBJECT_NOT_FOUND", "教职工档案不存在", null, null, null, null));
        }
        return EligibilityResult.fail(
            "SUBJECT_NOT_FOUND", "未知档案类型", null, null, null, null);
    }

    private EligibilityResult checkStudent(Long tenantId, PatientStudent student) {
        if (!Boolean.TRUE.equals(student.getActiveFlag())) {
            return failForStudent(student, "PROFILE_DISABLED", "学生档案已停用", null);
        }
        if (!isMasterSwitchOn(tenantId)) {
            return failForStudent(student, "SWITCH_OFF", "公费总开关已关闭", null);
        }
        PublicExpenseProfile profile = profileRepository
            .findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
                tenantId, CampusDomainConstants.SUBJECT_STUDENT, student.getId())
            .orElse(null);
        EligibilityResult profileResult = validateProfile(profile, student.getCampusCode());
        if (profileResult != null) {
            return profileResult;
        }
        String gfStatus = profile.getGfStatusEnum();
        if (!CampusDomainConstants.GF_STATUS_ELIGIBLE.equals(gfStatus)) {
            return failForStudent(student, "GF_STATUS_NOT_ELIGIBLE", "公费状态不符合资格", gfStatus);
        }
        if (!CampusDomainConstants.SCHOOL_STATUS_IN_SCHOOL.equals(student.getSchoolStatusEnum())) {
            return failForStudent(student, "NOT_IN_SCHOOL", "学生不在校", gfStatus);
        }
        if (!CampusDomainConstants.PHYSICAL_EXAM_PASS.equals(student.getPhysicalExamResultEnum())) {
            return failForStudent(student, "PHYSICAL_EXAM_NOT_PASS", "体检未合格", gfStatus);
        }
        if (!isStudentOrgActive(tenantId, student)) {
            return failForStudent(student, "ORG_DISABLED", "学院或校区未启用", gfStatus);
        }
        return EligibilityResult.pass(
            gfStatus,
            profile.getCardNo(),
            profile.getMedicalCode(),
            student.getCampusCode());
    }

    private EligibilityResult checkStaff(Long tenantId, PatientStaff staff) {
        if (!Boolean.TRUE.equals(staff.getActiveFlag())) {
            return failForStaff(staff, "PROFILE_DISABLED", "教职工档案已停用", null);
        }
        if (!isMasterSwitchOn(tenantId)) {
            return failForStaff(staff, "SWITCH_OFF", "公费总开关已关闭", null);
        }
        PublicExpenseProfile profile = profileRepository
            .findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
                tenantId, CampusDomainConstants.SUBJECT_STAFF, staff.getId())
            .orElse(null);
        String campusCode = resolveStaffCampusCode(tenantId, staff.getCampusSiteId());
        EligibilityResult profileResult = validateProfile(profile, campusCode);
        if (profileResult != null) {
            return profileResult;
        }
        String gfStatus = profile.getGfStatusEnum();
        if (!CampusDomainConstants.GF_STATUS_ELIGIBLE.equals(gfStatus)) {
            return failForStaff(staff, "GF_STATUS_NOT_ELIGIBLE", "公费状态不符合资格", gfStatus);
        }
        if (!CampusDomainConstants.JOB_STATUS_ACTIVE.equals(staff.getJobStatusEnum())) {
            return failForStaff(staff, "NOT_IN_SERVICE", "教职工不在职", gfStatus);
        }
        if (!isWithinValidPeriod(staff.getValidFrom(), staff.getValidTo())) {
            return failForStaff(staff, "OUT_OF_VALID_PERIOD", "教职工有效期外", gfStatus);
        }
        if (!isStaffOrgActive(tenantId, staff)) {
            return failForStaff(staff, "ORG_DISABLED", "部门或校区未启用", gfStatus);
        }
        return EligibilityResult.pass(
            gfStatus,
            profile.getCardNo(),
            profile.getMedicalCode() != null ? profile.getMedicalCode() : staff.getMedicalCode(),
            campusCode);
    }

    private EligibilityResult validateProfile(PublicExpenseProfile profile, String campusCode) {
        if (profile == null) {
            return EligibilityResult.fail(
                "GF_STATUS_NOT_ELIGIBLE", "未配置公费资格档案", CampusDomainConstants.GF_STATUS_UNKNOWN, null, null, campusCode);
        }
        if (!Boolean.TRUE.equals(profile.getEligibleFlag())) {
            return EligibilityResult.fail(
                "PROFILE_DISABLED",
                "公费资格档案已停用",
                profile.getGfStatusEnum(),
                profile.getCardNo(),
                profile.getMedicalCode(),
                campusCode);
        }
        if (!isWithinValidPeriod(profile.getValidFrom(), profile.getValidTo())) {
            return EligibilityResult.fail(
                "OUT_OF_VALID_PERIOD",
                "不在公费有效期内",
                profile.getGfStatusEnum(),
                profile.getCardNo(),
                profile.getMedicalCode(),
                campusCode);
        }
        return null;
    }

    private EligibilityResult failForStudent(PatientStudent student, String code, String message, String gfStatus) {
        PublicExpenseProfile profile = profileRepository
            .findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
                student.getTenantId(), CampusDomainConstants.SUBJECT_STUDENT, student.getId())
            .orElse(null);
        String resolvedGfStatus = gfStatus != null ? gfStatus : profile != null ? profile.getGfStatusEnum() : null;
        return EligibilityResult.fail(
            code,
            message,
            resolvedGfStatus,
            profile != null ? profile.getCardNo() : null,
            profile != null ? profile.getMedicalCode() : null,
            student.getCampusCode());
    }

    private EligibilityResult failForStaff(PatientStaff staff, String code, String message, String gfStatus) {
        PublicExpenseProfile profile = profileRepository
            .findByTenantIdAndSubjectTypeEnumAndSubjectIdAndIsDeletedFalse(
                staff.getTenantId(), CampusDomainConstants.SUBJECT_STAFF, staff.getId())
            .orElse(null);
        String resolvedGfStatus = gfStatus != null ? gfStatus : profile != null ? profile.getGfStatusEnum() : null;
        return EligibilityResult.fail(
            code,
            message,
            resolvedGfStatus,
            profile != null ? profile.getCardNo() : null,
            profile != null ? profile.getMedicalCode() : staff.getMedicalCode(),
            resolveStaffCampusCode(staff.getTenantId(), staff.getCampusSiteId()));
    }

    private boolean isMasterSwitchOn(Long tenantId) {
        return gfSwitchRepository.findByTenantIdAndSwitchKeyAndIsDeletedFalse(
                tenantId, CampusDomainConstants.GF_SWITCH_KEY_MASTER)
            .map(sw -> isTruthySwitchValue(sw.getSwitchValue()))
            .orElse(true);
    }

    private boolean isTruthySwitchValue(String switchValue) {
        if (switchValue == null || switchValue.isBlank()) {
            return true;
        }
        String normalized = switchValue.trim().toLowerCase(Locale.ROOT);
        return !("false".equals(normalized) || "0".equals(normalized) || "off".equals(normalized) || "n".equals(normalized));
    }

    private boolean isWithinValidPeriod(LocalDateTime validFrom, LocalDateTime validTo) {
        LocalDateTime now = LocalDateTime.now();
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        return true;
    }

    private boolean isStudentOrgActive(Long tenantId, PatientStudent student) {
        return collegeRepository.findByIdAndTenantIdAndIsDeletedFalse(student.getCollegeId(), tenantId)
                .filter(college -> Boolean.TRUE.equals(college.getActiveFlag()))
                .isPresent()
            && campusSiteRepository.findByTenantIdAndCampusCodeAndIsDeletedFalse(tenantId, student.getCampusCode())
                .filter(site -> Boolean.TRUE.equals(site.getActiveFlag()))
                .isPresent();
    }

    private boolean isStaffOrgActive(Long tenantId, PatientStaff staff) {
        return departmentRepository.findByIdAndTenantIdAndIsDeletedFalse(staff.getDepartmentId(), tenantId)
                .filter(dept -> Boolean.TRUE.equals(dept.getActiveFlag()))
                .isPresent()
            && campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(staff.getCampusSiteId(), tenantId)
                .filter(site -> Boolean.TRUE.equals(site.getActiveFlag()))
                .isPresent();
    }

    private String resolveStaffCampusCode(Long tenantId, Long campusSiteId) {
        if (campusSiteId == null) {
            return null;
        }
        return campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(campusSiteId, tenantId)
            .map(site -> site.getCampusCode())
            .orElse(null);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
