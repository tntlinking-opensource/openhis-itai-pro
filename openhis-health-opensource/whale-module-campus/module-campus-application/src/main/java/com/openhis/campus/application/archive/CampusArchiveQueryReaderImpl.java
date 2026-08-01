/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.archive;

import com.openhis.campus.application.campusarchive.mapper.CampusArchiveMapper;
import com.openhis.campus.domain.archive.CampusArchiveQueryReader;
import com.openhis.campus.domain.archive.view.ArchiveTransferView;
import com.openhis.campus.domain.archive.view.CampusProfileView;
import com.openhis.campus.domain.archive.view.StudentCampusView;
import com.openhis.campus.domain.archivetransfer.entity.ArchiveTransfer;
import com.openhis.campus.domain.archivetransfer.service.ArchiveTransferQueryService;
import com.openhis.campus.domain.campussite.entity.CampusSite;
import com.openhis.campus.domain.campussite.repository.CampusSiteRepository;
import com.openhis.campus.domain.patientstaff.entity.PatientStaff;
import com.openhis.campus.domain.patientstaff.service.PatientStaffQueryService;
import com.openhis.campus.domain.patientstudent.entity.PatientStudent;
import com.openhis.campus.domain.patientstudent.service.PatientStudentQueryService;
import com.openhis.campus.domain.publicexpenseprofile.entity.PublicExpenseProfile;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseProfileQueryService;
import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CampusArchiveQueryReaderImpl implements CampusArchiveQueryReader {

    private final PatientStudentQueryService patientStudentQueryService;
    private final PatientStaffQueryService patientStaffQueryService;
    private final PublicExpenseProfileQueryService publicExpenseProfileQueryService;
    private final ArchiveTransferQueryService archiveTransferQueryService;
    private final CampusSiteRepository campusSiteRepository;
    private final CampusArchiveMapper campusArchiveMapper;

    @Override
    public Optional<StudentCampusView> resolveStudentCampus(Long tenantId, Long patientId) {
        return patientStudentQueryService.findByPatientId(tenantId, patientId).map(this::toStudentCampusView);
    }

    @Override
    public Optional<StudentCampusView> resolveStudentCampusByStudentNo(Long tenantId, String studentNo) {
        return patientStudentQueryService.findByStudentNo(tenantId, studentNo).map(this::toStudentCampusView);
    }

    @Override
    public Optional<CampusProfileView> resolveCampusProfileByPatientId(Long tenantId, Long patientId) {
        Optional<PatientStudent> student = patientStudentQueryService.findByPatientId(tenantId, patientId);
        if (student.isPresent()) {
            return Optional.of(buildStudentProfileView(student.get()));
        }
        Optional<PatientStaff> staff = patientStaffQueryService.findByPatientId(tenantId, patientId);
        if (staff.isPresent()) {
            return Optional.of(buildStaffProfileView(staff.get()));
        }
        return Optional.empty();
    }

    @Override
    public List<ArchiveTransferView> listTransfers(Long tenantId, String subjectType, Long subjectId) {
        List<ArchiveTransferView> views = new ArrayList<>();
        for (ArchiveTransfer transfer : archiveTransferQueryService.listBySubject(tenantId, subjectType, subjectId)) {
            views.add(campusArchiveMapper.toTransferView(transfer));
        }
        return views;
    }

    private StudentCampusView toStudentCampusView(PatientStudent student) {
        StudentCampusView view = new StudentCampusView();
        view.setPatientId(student.getPatientId());
        view.setStudentId(student.getId());
        view.setStudentNo(student.getStudentNo());
        view.setCampusCode(student.getCampusCode());
        view.setCollegeId(student.getCollegeId());
        view.setCollegeName(student.getCollegeName());
        view.setClassName(student.getClassName());
        view.setName(student.getName());
        return view;
    }

    @Override
    public List<StudentCampusView> listStudentsForRoster(Long tenantId, Long collegeId, String className) {
        List<StudentCampusView> rows = new ArrayList<>();
        for (PatientStudent student : patientStudentQueryService.listForRoster(tenantId, collegeId, className)) {
            rows.add(toStudentCampusView(student));
        }
        return rows;
    }

    private CampusProfileView buildStudentProfileView(PatientStudent student) {
        CampusProfileView view = new CampusProfileView();
        view.setSubjectType(CampusDomainConstants.SUBJECT_STUDENT);
        view.setSubjectId(student.getId());
        view.setCampusCode(student.getCampusCode());
        view.setCollegeId(student.getCollegeId());
        publicExpenseProfileQueryService.getBySubject(
                student.getTenantId(), CampusDomainConstants.SUBJECT_STUDENT, student.getId())
            .ifPresent(profile -> applyPeProfile(view, profile));
        return view;
    }

    private CampusProfileView buildStaffProfileView(PatientStaff staff) {
        CampusProfileView view = new CampusProfileView();
        view.setSubjectType(CampusDomainConstants.SUBJECT_STAFF);
        view.setSubjectId(staff.getId());
        view.setDepartmentId(staff.getDepartmentId());
        view.setCampusCode(resolveCampusCode(staff.getTenantId(), staff.getCampusSiteId()));
        publicExpenseProfileQueryService.getBySubject(
                staff.getTenantId(), CampusDomainConstants.SUBJECT_STAFF, staff.getId())
            .ifPresent(profile -> applyPeProfile(view, profile));
        if (view.getMedicalCode() == null) {
            view.setMedicalCode(staff.getMedicalCode());
        }
        return view;
    }

    private void applyPeProfile(CampusProfileView view, PublicExpenseProfile profile) {
        view.setCardNo(profile.getCardNo());
        view.setGfStatusEnum(profile.getGfStatusEnum());
        view.setMedicalCode(profile.getMedicalCode());
    }

    private String resolveCampusCode(Long tenantId, Long campusSiteId) {
        if (campusSiteId == null) {
            return null;
        }
        Long resolvedTenantId = tenantId != null ? tenantId : TenantUtils.getCurrentTenantId();
        return campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(campusSiteId, resolvedTenantId)
            .map(CampusSite::getCampusCode)
            .orElse(null);
    }
}
