/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

import com.zxplat.plugin.peis.application.group.dto.RosterImportRowDto;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupCompany;
import com.zxplat.domain.share.extension.campus.PeisCampusStaffArchiveExtension;
import com.zxplat.domain.share.extension.campus.PeisCampusStaffUpsertCommand;
import com.zxplat.domain.share.extension.campus.PeisCampusStaffUpsertResult;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 团检名册导入时，教职工同步 Campus 档案编排。
 */
@Component
@RequiredArgsConstructor
public class GroupRosterCampusStaffSyncSupport {

    private final ObjectProvider<PeisCampusStaffArchiveExtension> peisCampusStaffArchiveExtension;

    public PeisCampusStaffUpsertResult syncStaffForRosterImport(
            GroupRosterImportMemberKind memberKind,
            Boolean syncCampus,
            RosterImportRowDto row,
            PeisGroupCompany company,
            Long patientId,
            Long operatorId) {
        if (memberKind != GroupRosterImportMemberKind.STAFF) {
            return null;
        }
        if (Boolean.FALSE.equals(syncCampus)) {
            return null;
        }
        PeisCampusStaffArchiveExtension extension = peisCampusStaffArchiveExtension.getIfAvailable();
        if (extension == null) {
            return null;
        }
        if (row == null || StringUtils.isEmpty(trimToNull(row.getEmployeeNo()))) {
            throw new IllegalArgumentException("教职工导入须填写工号");
        }
        PeisCampusStaffUpsertCommand command = new PeisCampusStaffUpsertCommand();
        command.setTenantId(TenantUtils.getCurrentTenantId());
        command.setOperatorId(operatorId);
        command.setEmployeeNo(trimToNull(row.getEmployeeNo()));
        command.setPatientId(patientId);
        command.setFullName(row.getFullName());
        command.setGenderEnum(trimToNull(row.getGenderEnum()));
        command.setIdCardNo(trimToNull(row.getIdCardNo()));
        command.setMobilePhone(trimToNull(row.getMobilePhone()));
        command.setDepartmentName(trimToNull(row.getClassName()));
        if (company != null) {
            command.setCollegeId(company.getCampusCollegeId());
        }
        return extension.upsertStaffForRosterImport(command);
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
