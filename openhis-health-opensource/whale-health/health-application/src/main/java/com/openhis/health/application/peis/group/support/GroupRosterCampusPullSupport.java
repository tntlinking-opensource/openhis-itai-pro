/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import com.openhis.health.application.peis.group.dto.CampusRosterCandidateDto;
import com.openhis.health.application.peis.group.dto.CampusRosterDepartmentOptionDto;
import com.openhis.health.domain.peis.group.entity.PeisGroupCompany;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.share.extension.campus.PeisCampusDepartmentOptionView;
import com.openhis.health.domain.share.extension.campus.PeisCampusRosterQueryExtension;
import com.openhis.health.domain.share.extension.campus.PeisCampusStaffRosterView;
import com.openhis.health.domain.share.extension.campus.PeisCampusStudentRosterView;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 Campus 拉取团检名册候选并转换为 PEIS 名册行。
 */
@Component
@RequiredArgsConstructor
public class GroupRosterCampusPullSupport {

    private final ObjectProvider<PeisCampusRosterQueryExtension> peisCampusRosterQueryExtension;

    public List<CampusRosterDepartmentOptionDto> listDepartments(PeisGroupCompany company) {
        PeisCampusRosterQueryExtension extension = requireExtension();
        Long collegeId = company == null ? null : company.getCampusCollegeId();
        List<CampusRosterDepartmentOptionDto> rows = new ArrayList<>();
        for (PeisCampusDepartmentOptionView view : extension.listDepartments(
            TenantUtils.getCurrentTenantId(), collegeId)) {
            CampusRosterDepartmentOptionDto dto = new CampusRosterDepartmentOptionDto();
            dto.setDepartmentId(view.getDepartmentId());
            dto.setDepartmentName(view.getDepartmentName());
            dto.setCollegeId(view.getCollegeId());
            rows.add(dto);
        }
        return rows;
    }

    public List<String> listClassNames(PeisGroupCompany company) {
        PeisCampusRosterQueryExtension extension = requireExtension();
        Long collegeId = requireCollegeId(company);
        return extension.listStudentClassNames(TenantUtils.getCurrentTenantId(), collegeId);
    }

    public List<CampusRosterCandidateDto> previewCandidates(
            GroupRosterImportMemberKind memberKind,
            PeisGroupCompany company,
            String className,
            Long departmentId) {
        if (memberKind == GroupRosterImportMemberKind.STUDENT) {
            return listStudentCandidates(company, className);
        }
        if (memberKind == GroupRosterImportMemberKind.STAFF) {
            return listStaffCandidates(departmentId);
        }
        throw new ValidationException("从 Campus 拉取名册仅支持学生或教职工");
    }

    public List<PeisGroupRoster> buildRosterRows(
            GroupRosterImportMemberKind memberKind,
            PeisGroupCompany company,
            String className,
            Long departmentId,
            List<Long> patientIds) {
        List<CampusRosterCandidateDto> candidates =
            previewCandidates(memberKind, company, className, departmentId);
        Set<Long> filter = patientIds == null || patientIds.isEmpty()
            ? null
            : new HashSet<>(patientIds);
        List<PeisGroupRoster> rows = new ArrayList<>();
        for (CampusRosterCandidateDto candidate : candidates) {
            if (filter != null && !filter.contains(candidate.getPatientId())) {
                continue;
            }
            PeisGroupRoster entity = new PeisGroupRoster();
            entity.setPatientId(candidate.getPatientId());
            entity.setFullName(candidate.getFullName());
            entity.setStudentNo(trimToNull(candidate.getStudentNo()));
            entity.setEmployeeNo(trimToNull(candidate.getEmployeeNo()));
            entity.setClassName(trimToNull(candidate.getClassName()));
            if (company != null && company.getCampusCollegeId() != null) {
                entity.setCollegeId(company.getCampusCollegeId());
            }
            rows.add(entity);
        }
        return rows;
    }

    private List<CampusRosterCandidateDto> listStudentCandidates(PeisGroupCompany company, String className) {
        PeisCampusRosterQueryExtension extension = requireExtension();
        Long collegeId = requireCollegeId(company);
        List<CampusRosterCandidateDto> rows = new ArrayList<>();
        for (PeisCampusStudentRosterView view
            : extension.listStudentsForRoster(TenantUtils.getCurrentTenantId(), collegeId, className)) {
            if (view.getPatientId() == null) {
                continue;
            }
            CampusRosterCandidateDto dto = new CampusRosterCandidateDto();
            dto.setPatientId(view.getPatientId());
            dto.setFullName(view.getFullName());
            dto.setStudentNo(view.getStudentNo());
            dto.setClassName(view.getClassName());
            rows.add(dto);
        }
        return rows;
    }

    private List<CampusRosterCandidateDto> listStaffCandidates(Long departmentId) {
        if (departmentId == null) {
            throw new ValidationException("请选择 Campus 部门");
        }
        PeisCampusRosterQueryExtension extension = requireExtension();
        List<CampusRosterCandidateDto> rows = new ArrayList<>();
        for (PeisCampusStaffRosterView view
            : extension.listStaffForRoster(TenantUtils.getCurrentTenantId(), departmentId)) {
            if (view.getPatientId() == null) {
                continue;
            }
            CampusRosterCandidateDto dto = new CampusRosterCandidateDto();
            dto.setPatientId(view.getPatientId());
            dto.setFullName(view.getFullName());
            dto.setEmployeeNo(view.getEmployeeNo());
            dto.setDepartmentName(view.getDepartmentName());
            rows.add(dto);
        }
        return rows;
    }

    private PeisCampusRosterQueryExtension requireExtension() {
        PeisCampusRosterQueryExtension extension = peisCampusRosterQueryExtension.getIfAvailable();
        if (extension == null) {
            throw new ValidationException("Campus 模块未启用，无法拉取名册");
        }
        return extension;
    }

    private static Long requireCollegeId(PeisGroupCompany company) {
        if (company == null || company.getCampusCollegeId() == null) {
            throw new ValidationException("团检单位未绑定 Campus 学院，无法拉取学生名册");
        }
        return company.getCampusCollegeId();
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
