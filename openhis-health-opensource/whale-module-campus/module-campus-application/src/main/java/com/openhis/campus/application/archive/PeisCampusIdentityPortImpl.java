/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.archive;

import com.openhis.campus.domain.patientstaff.entity.PatientStaff;
import com.openhis.campus.domain.patientstaff.service.PatientStaffQueryService;
import com.openhis.campus.domain.patientstudent.entity.PatientStudent;
import com.openhis.campus.domain.patientstudent.service.PatientStudentQueryService;
import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityPort;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityView;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * PEIS Campus 身份 SPI 实现（Campus 启用时装配）。
 */
@Service
@RequiredArgsConstructor
public class PeisCampusIdentityPortImpl implements PeisCampusIdentityPort {

    private final PatientStudentQueryService patientStudentQueryService;
    private final PatientStaffQueryService patientStaffQueryService;

    @Override
    public Optional<PeisCampusIdentityView> resolveByPatientId(Long tenantId, Long patientId) {
        Long tid = resolveTenant(tenantId);
        if (tid == null || patientId == null) {
            return Optional.empty();
        }
        Optional<PatientStudent> student = patientStudentQueryService.findByPatientId(tid, patientId);
        if (student.isPresent()) {
            return Optional.of(toStudentView(student.get()));
        }
        return patientStaffQueryService.findByPatientId(tid, patientId).map(this::toStaffView);
    }

    @Override
    public Optional<PeisCampusIdentityView> resolveByStudentNo(Long tenantId, String studentNo) {
        Long tid = resolveTenant(tenantId);
        if (tid == null || studentNo == null || studentNo.isBlank()) {
            return Optional.empty();
        }
        return patientStudentQueryService.findByStudentNo(tid, studentNo.trim()).map(this::toStudentView);
    }

    @Override
    public Optional<PeisCampusIdentityView> resolveByEmployeeNo(Long tenantId, String employeeNo) {
        Long tid = resolveTenant(tenantId);
        if (tid == null || employeeNo == null || employeeNo.isBlank()) {
            return Optional.empty();
        }
        return patientStaffQueryService.findByEmployeeNo(tid, employeeNo.trim()).map(this::toStaffView);
    }

    private PeisCampusIdentityView toStudentView(PatientStudent student) {
        PeisCampusIdentityView view = new PeisCampusIdentityView();
        view.setSubjectType(CampusDomainConstants.SUBJECT_STUDENT);
        view.setPatientId(student.getPatientId());
        view.setSubjectId(student.getId());
        view.setName(student.getName());
        view.setStudentNo(student.getStudentNo());
        view.setSchoolStatusEnum(student.getSchoolStatusEnum());
        view.setSchoolYear(student.getSchoolYear());
        view.setEnrollTime(student.getEnrollTime());
        view.setLeaveTime(student.getLeaveTime());
        return view;
    }

    private PeisCampusIdentityView toStaffView(PatientStaff staff) {
        PeisCampusIdentityView view = new PeisCampusIdentityView();
        view.setSubjectType(CampusDomainConstants.SUBJECT_STAFF);
        view.setPatientId(staff.getPatientId());
        view.setSubjectId(staff.getId());
        view.setName(staff.getName());
        view.setEmployeeNo(staff.getEmployeeNo());
        view.setJobStatusEnum(staff.getJobStatusEnum());
        return view;
    }

    private static Long resolveTenant(Long tenantId) {
        return tenantId != null ? tenantId : TenantUtils.getCurrentTenantId();
    }
}
