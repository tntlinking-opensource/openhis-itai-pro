/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import com.openhis.health.application.peis.group.dto.GroupRosterDeskContextDto;
import com.openhis.health.application.peis.group.dto.PackageOptionDto;
import com.openhis.health.application.peis.group.request.LookupGroupRosterForDeskRequest;
import com.openhis.health.application.peis.group.response.LookupGroupRosterForDeskResponse;
import com.openhis.health.application.peis.group.service.PeisGroupCheckAdminAppService;
import com.openhis.health.application.peis.orchestration.result.RegisterPersonalCheckInResult;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.extension.peis.PeisGroupPackageOptionView;
import com.openhis.health.domain.share.extension.peis.PeisGroupRosterDeskView;
import com.openhis.health.domain.share.extension.peis.PeisGroupRosterPatientPort;
import com.openhis.health.domain.share.extension.peis.PeisGroupRosterSelectResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 患者端团检名册选定 Port：读侧委托 AdminAppService；写侧经 SelectionSupport 传入操作人。
 */
@Component
@RequiredArgsConstructor
public class PeisGroupRosterPatientPortImpl implements PeisGroupRosterPatientPort {

    private final PeisGroupCheckAdminAppService peisGroupCheckAdminAppService;
    private final GroupRosterPackageSelectionSupport groupRosterPackageSelectionSupport;
    private final PeisGroupCheckQueryService peisGroupCheckQueryService;

    @Override
    public List<PeisGroupRosterDeskView> lookupByPatientId(Long patientId) {
        if (patientId == null) {
            throw new ValidationException("查询团检名册失败：患者不能为空");
        }
        LookupGroupRosterForDeskRequest request = new LookupGroupRosterForDeskRequest();
        request.setPatientId(patientId);
        LookupGroupRosterForDeskResponse response = peisGroupCheckAdminAppService.lookupRosterForDesk(request);
        List<PeisGroupRosterDeskView> views = new ArrayList<>();
        if (response == null || response.getMatches() == null) {
            return views;
        }
        for (GroupRosterDeskContextDto match : response.getMatches()) {
            views.add(toView(match));
        }
        return views;
    }

    @Override
    public PeisGroupRosterSelectResult selectPackage(
            Long patientId, Long rosterId, Long packageActivityId, Long orgId, Long operatorId) {
        assertRosterOwnedByPatient(patientId, rosterId);
        if (packageActivityId == null) {
            throw new ValidationException("选定建单失败：套餐不能为空");
        }
        if (operatorId == null) {
            throw new ValidationException("选定建单失败：操作人不能为空");
        }
        RegisterPersonalCheckInResult enrolled = groupRosterPackageSelectionSupport.selectAndEnroll(
            rosterId, packageActivityId, orgId, operatorId);
        PeisGroupRosterSelectResult result = new PeisGroupRosterSelectResult();
        result.setRosterId(rosterId);
        result.setEncounterId(enrolled.getEncounterId());
        result.setBusNo(enrolled.getBusNo());
        result.setSelectedPackageActivityId(packageActivityId);
        return result;
    }

    @Override
    public PeisGroupRosterSelectResult changePackage(
            Long patientId, Long rosterId, Long packageActivityId, Long operatorId) {
        assertRosterOwnedByPatient(patientId, rosterId);
        if (packageActivityId == null) {
            throw new ValidationException("换套餐失败：套餐不能为空");
        }
        if (operatorId == null) {
            throw new ValidationException("换套餐失败：操作人不能为空");
        }
        groupRosterPackageSelectionSupport.changeSelectedPackageBeforeArrival(
            rosterId, packageActivityId, operatorId);
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        PeisGroupRosterSelectResult result = new PeisGroupRosterSelectResult();
        result.setRosterId(roster.getId());
        result.setEncounterId(roster.getEncounterId());
        result.setSelectedPackageActivityId(packageActivityId);
        return result;
    }

    private void assertRosterOwnedByPatient(Long patientId, Long rosterId) {
        if (patientId == null || rosterId == null) {
            throw new ValidationException("名册操作失败：患者与名册不能为空");
        }
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(rosterId);
        if (roster.getPatientId() == null || !patientId.equals(roster.getPatientId())) {
            throw new BusinessException("ROSTER_PATIENT_MISMATCH", "名册不属于当前就诊人");
        }
    }

    private static PeisGroupRosterDeskView toView(GroupRosterDeskContextDto dto) {
        PeisGroupRosterDeskView view = new PeisGroupRosterDeskView();
        if (dto == null) {
            return view;
        }
        view.setRosterId(dto.getRosterId());
        view.setBatchId(dto.getBatchId());
        view.setPatientId(dto.getPatientId());
        view.setFullName(dto.getFullName());
        view.setCompanyId(dto.getCompanyId());
        view.setCompanyName(dto.getCompanyName());
        view.setBatchName(dto.getBatchName());
        view.setContractId(dto.getContractId());
        view.setPackageActivityId(dto.getPackageActivityId());
        view.setPackageName(dto.getPackageName());
        view.setEncounterId(dto.getEncounterId());
        view.setPayStatusCode(dto.getPayStatusCode());
        view.setArrivalStatusCode(dto.getArrivalStatusCode());
        view.setEnrolled(dto.getEnrolled());
        view.setArrived(dto.getArrived());
        view.setSelectedPackageActivityId(dto.getSelectedPackageActivityId());
        view.setPackageSelected(dto.getPackageSelected());
        view.setCanChangePackage(dto.getCanChangePackage());
        if (dto.getOptionalPackages() != null) {
            for (PackageOptionDto option : dto.getOptionalPackages()) {
                if (option == null) {
                    continue;
                }
                PeisGroupPackageOptionView opt = new PeisGroupPackageOptionView();
                opt.setPackageActivityId(option.getPackageActivityId());
                opt.setPackageName(option.getPackageName());
                view.getOptionalPackages().add(opt);
            }
        }
        return view;
    }
}
