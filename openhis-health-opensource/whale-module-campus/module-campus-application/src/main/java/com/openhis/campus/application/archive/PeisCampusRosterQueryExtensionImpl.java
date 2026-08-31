/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.archive;

import com.openhis.campus.domain.campusdepartment.entity.CampusDepartment;
import com.openhis.campus.domain.campusdepartment.service.CampusDepartmentQueryService;
import com.openhis.campus.domain.org.CampusOrgQueryReader;
import com.openhis.campus.domain.org.view.CampusDepartmentView;
import com.openhis.campus.domain.patientstaff.entity.PatientStaff;
import com.openhis.campus.domain.patientstaff.service.PatientStaffQueryService;
import com.openhis.campus.domain.patientstudent.entity.PatientStudent;
import com.openhis.campus.domain.patientstudent.service.PatientStudentQueryService;
import com.openhis.health.domain.share.extension.campus.PeisCampusDepartmentOptionView;
import com.openhis.health.domain.share.extension.campus.PeisCampusRosterQueryExtension;
import com.openhis.health.domain.share.extension.campus.PeisCampusStaffRosterView;
import com.openhis.health.domain.share.extension.campus.PeisCampusStudentRosterView;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PeisCampusRosterQueryExtensionImpl implements PeisCampusRosterQueryExtension {

    private final CampusOrgQueryReader campusOrgQueryReader;
    private final CampusDepartmentQueryService campusDepartmentQueryService;
    private final PatientStudentQueryService patientStudentQueryService;
    private final PatientStaffQueryService patientStaffQueryService;

    @Override
    public List<PeisCampusDepartmentOptionView> listDepartments(Long tenantId, Long collegeIdOrNull) {
        Long tid = resolveTenant(tenantId);
        List<PeisCampusDepartmentOptionView> rows = new ArrayList<>();
        for (CampusDepartmentView department
            : campusOrgQueryReader.listDepartments(tid, collegeIdOrNull, null)) {
            PeisCampusDepartmentOptionView view = new PeisCampusDepartmentOptionView();
            view.setDepartmentId(department.getId());
            view.setDepartmentName(department.getDepartmentName());
            view.setCollegeId(department.getCollegeId());
            rows.add(view);
        }
        return rows;
    }

    @Override
    public List<String> listStudentClassNames(Long tenantId, Long collegeId) {
        return patientStudentQueryService.listDistinctClassNames(resolveTenant(tenantId), collegeId);
    }

    @Override
    public List<PeisCampusStudentRosterView> listStudentsForRoster(
            Long tenantId, Long collegeId, String className) {
        List<PeisCampusStudentRosterView> rows = new ArrayList<>();
        for (PatientStudent student
            : patientStudentQueryService.listForRoster(resolveTenant(tenantId), collegeId, className)) {
            rows.add(toStudentView(student));
        }
        return rows;
    }

    @Override
    public List<PeisCampusStaffRosterView> listStaffForRoster(Long tenantId, Long departmentId) {
        Long tid = resolveTenant(tenantId);
        String departmentName = resolveDepartmentName(tid, departmentId);
        List<PeisCampusStaffRosterView> rows = new ArrayList<>();
        for (PatientStaff staff : patientStaffQueryService.listForRoster(tid, departmentId)) {
            PeisCampusStaffRosterView view = new PeisCampusStaffRosterView();
            view.setPatientId(staff.getPatientId());
            view.setStaffId(staff.getId());
            view.setEmployeeNo(staff.getEmployeeNo());
            view.setFullName(staff.getName());
            view.setDepartmentId(staff.getDepartmentId());
            view.setDepartmentName(departmentName);
            rows.add(view);
        }
        return rows;
    }

    private PeisCampusStudentRosterView toStudentView(PatientStudent student) {
        PeisCampusStudentRosterView view = new PeisCampusStudentRosterView();
        view.setPatientId(student.getPatientId());
        view.setStudentId(student.getId());
        view.setStudentNo(student.getStudentNo());
        view.setFullName(student.getName());
        view.setClassName(student.getClassName());
        view.setCollegeId(student.getCollegeId());
        view.setCollegeName(student.getCollegeName());
        return view;
    }

    private String resolveDepartmentName(Long tenantId, Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        try {
            CampusDepartment department = campusDepartmentQueryService.getById(tenantId, departmentId);
            return department.getDepartmentName();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Long resolveTenant(Long tenantId) {
        return tenantId != null ? tenantId : TenantUtils.getCurrentTenantId();
    }
}
