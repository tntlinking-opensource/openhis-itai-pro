/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.bridge.integration;

import com.zxplat.domain.share.extension.campus.PeisCampusStaffArchiveExtension;
import com.zxplat.domain.share.extension.campus.PeisCampusStaffUpsertCommand;
import com.zxplat.domain.share.extension.campus.PeisCampusStaffUpsertResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import com.zxplat.plugin.campus.domain.archivechangelog.service.ArchiveChangeLogDomainService;
import com.zxplat.plugin.campus.domain.campusdepartment.entity.CampusDepartment;
import com.zxplat.plugin.campus.domain.campusdepartment.service.CampusDepartmentQueryService;
import com.zxplat.plugin.campus.domain.campussite.entity.CampusSite;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.plugin.campus.domain.patientstaff.entity.PatientStaff;
import com.zxplat.plugin.campus.domain.patientstaff.service.PatientStaffDomainService;
import com.zxplat.plugin.campus.domain.patientstaff.service.PatientStaffQueryService;
import com.zxplat.plugin.campus.domain.shared.CampusArchiveConstants;
import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * PEIS 团检名册 → Campus 教职工档案写侧 Extension 实现（bridge.integration，无 application 依赖）。
 */
@Service
@RequiredArgsConstructor
public class PeisCampusStaffArchiveExtensionImpl implements PeisCampusStaffArchiveExtension {

    private final PatientStaffQueryService patientStaffQueryService;
    private final PatientStaffDomainService patientStaffDomainService;
    private final CampusDepartmentQueryService campusDepartmentQueryService;
    private final CampusSiteRepository campusSiteRepository;
    private final ArchiveChangeLogDomainService archiveChangeLogDomainService;

    @Override
    @Transactional
    public PeisCampusStaffUpsertResult upsertStaffForRosterImport(PeisCampusStaffUpsertCommand command) {
        validateCommand(command);
        Long tenantId = resolveTenant(command.getTenantId());
        Long operatorId = command.getOperatorId();
        String employeeNo = command.getEmployeeNo().trim();

        Optional<PatientStaff> existingOpt = patientStaffQueryService.findByEmployeeNo(tenantId, employeeNo);
        if (existingOpt.isPresent()) {
            return upsertExisting(existingOpt.get(), command, tenantId, operatorId);
        }
        return createStaff(command, tenantId, operatorId, employeeNo);
    }

    private PeisCampusStaffUpsertResult upsertExisting(
            PatientStaff existing,
            PeisCampusStaffUpsertCommand command,
            Long tenantId,
            Long operatorId) {
        Long expectedPatientId = command.getPatientId();
        Long boundPatientId = existing.getPatientId();
        if (boundPatientId != null && !boundPatientId.equals(expectedPatientId)) {
            throw new BusinessException("STAFF_PATIENT_CONFLICT", "工号已绑定其他患者档案");
        }
        PatientStaff saved = existing;
        if (boundPatientId == null) {
            saved = patientStaffDomainService.bindPatient(tenantId, existing.getId(), expectedPatientId, operatorId);
        }
        return toResult(saved.getId(), false, saved.getPatientId());
    }

    private PeisCampusStaffUpsertResult createStaff(
            PeisCampusStaffUpsertCommand command,
            Long tenantId,
            Long operatorId,
            String employeeNo) {
        Long departmentId = resolveDepartmentId(command, tenantId);
        Long campusSiteId = resolveCampusSiteId(command, tenantId, departmentId);

        PatientStaff entity = new PatientStaff();
        entity.setTenantId(tenantId);
        entity.setName(command.getFullName().trim());
        entity.setEmployeeNo(employeeNo);
        entity.setPatientId(command.getPatientId());
        entity.setGenderEnum(command.getGenderEnum());
        entity.setIdCard(trimToNull(command.getIdCardNo()));
        entity.setPhone(trimToNull(command.getMobilePhone()));
        entity.setDepartmentId(departmentId);
        entity.setCampusSiteId(campusSiteId);

        PatientStaff saved = patientStaffDomainService.create(entity, operatorId);
        appendCreateChangeLog(saved, operatorId);
        return toResult(saved.getId(), true, saved.getPatientId());
    }

    private void appendCreateChangeLog(PatientStaff saved, Long operatorId) {
        ArchiveChangeLog entry = new ArchiveChangeLog();
        entry.setTenantId(saved.getTenantId());
        entry.setSubjectTypeEnum(CampusDomainConstants.SUBJECT_STAFF);
        entry.setSubjectId(saved.getId());
        entry.setCampusCode(resolveCampusCode(saved.getTenantId(), saved.getCampusSiteId()));
        entry.setChangeTypeEnum(CampusArchiveConstants.CHANGE_TYPE_CREATE);
        entry.setFieldName(CampusArchiveConstants.FIELD_WILDCARD);
        entry.setNewValue("created");
        entry.setChangeSourceEnum(CampusArchiveConstants.CHANGE_SOURCE_MANUAL);
        archiveChangeLogDomainService.appendBatch(List.of(entry), operatorId);
    }

    private Long resolveDepartmentId(PeisCampusStaffUpsertCommand command, Long tenantId) {
        if (command.getDepartmentId() != null) {
            return command.getDepartmentId();
        }
        String departmentName = trimToNull(command.getDepartmentName());
        if (departmentName == null) {
            throw new BusinessException("DEPARTMENT_NOT_FOUND", "教职工导入须填写部门");
        }
        List<CampusDepartment> departments =
            campusDepartmentQueryService.listActive(tenantId, null, command.getCollegeId());
        for (CampusDepartment department : departments) {
            if (departmentName.equals(department.getDepartmentName())) {
                return department.getId();
            }
        }
        throw new BusinessException("DEPARTMENT_NOT_FOUND", "未找到 Campus 部门：" + departmentName);
    }

    private Long resolveCampusSiteId(PeisCampusStaffUpsertCommand command, Long tenantId, Long departmentId) {
        if (command.getCampusSiteId() != null) {
            return command.getCampusSiteId();
        }
        CampusDepartment department = campusDepartmentQueryService.getById(tenantId, departmentId);
        if (department.getCampusSiteId() != null) {
            return department.getCampusSiteId();
        }
        List<CampusSite> primarySites = campusSiteRepository.findByTenantIdAndIsPrimaryTrueAndIsDeletedFalse(tenantId);
        if (!primarySites.isEmpty()) {
            return primarySites.get(0).getId();
        }
        List<CampusSite> sites =
            campusSiteRepository.findByTenantIdAndActiveFlagTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc(tenantId);
        if (sites.isEmpty()) {
            throw new BusinessException("CAMPUS_NOT_FOUND", "未配置校区，无法同步教职工档案");
        }
        return sites.get(0).getId();
    }

    private String resolveCampusCode(Long tenantId, Long campusSiteId) {
        if (campusSiteId == null) {
            return null;
        }
        return campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(campusSiteId, tenantId)
            .map(CampusSite::getCampusCode)
            .orElse(null);
    }

    private static void validateCommand(PeisCampusStaffUpsertCommand command) {
        if (command == null) {
            throw new BusinessException("INVALID_COMMAND", "同步命令不能为空");
        }
        if (command.getOperatorId() == null) {
            throw new BusinessException("OPERATOR_REQUIRED", "操作人不能为空");
        }
        if (command.getPatientId() == null) {
            throw new BusinessException("PATIENT_ID_REQUIRED", "患者主键不能为空");
        }
        if (command.getFullName() == null || command.getFullName().isBlank()) {
            throw new BusinessException("NAME_REQUIRED", "姓名不能为空");
        }
        if (command.getEmployeeNo() == null || command.getEmployeeNo().isBlank()) {
            throw new BusinessException("EMPLOYEE_NO_REQUIRED", "工号不能为空");
        }
    }

    private static PeisCampusStaffUpsertResult toResult(Long staffSubjectId, boolean created, Long patientId) {
        PeisCampusStaffUpsertResult result = new PeisCampusStaffUpsertResult();
        result.setStaffSubjectId(staffSubjectId);
        result.setCreated(created);
        result.setPatientId(patientId);
        return result;
    }

    private static Long resolveTenant(Long tenantId) {
        Long resolved = tenantId != null ? tenantId : TenantUtils.getCurrentTenantId();
        if (resolved == null) {
            throw new BusinessException("TENANT_REQUIRED", "租户不能为空");
        }
        return resolved;
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
