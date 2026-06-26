/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.reader;

import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.whale.mrms.contracts.reader.HealthEncounterHistoryReader;
import com.openhis.whale.mrms.contracts.snapshot.TransferLineSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link HealthEncounterHistoryReader} 实现：离院方式与转科路径。
 */
@Component
@RequiredArgsConstructor
public class HealthEncounterHistoryReaderAdapter implements HealthEncounterHistoryReader {

    private final EncounterHistoryQueryService encounterHistoryQueryService;
    private final OrganizationQueryService organizationQueryService;

    @Override
    public String resolveDischargeWayCode(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        var way = encounterHistoryQueryService.resolveDischargeWay(encounterId);
        if (way == null) {
            return null;
        }
        return way.getOutWayCode();
    }

    @Override
    public List<TransferLineSnapshot> listTransfers(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<EncounterHistoryMilestoneQueryModel> milestones =
                encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        if (milestones.isEmpty()) {
            return List.of();
        }
        List<EncounterHistoryMilestoneQueryModel> transferMilestones = new ArrayList<>();
        Set<Long> orgIds = new LinkedHashSet<>();
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (!isTransferMilestone(milestone.getName())) {
                continue;
            }
            transferMilestones.add(milestone);
            if (milestone.getOriginalOrganizationId() != null) {
                orgIds.add(milestone.getOriginalOrganizationId());
            }
            if (milestone.getTargetOrganizationId() != null) {
                orgIds.add(milestone.getTargetOrganizationId());
            }
        }
        if (transferMilestones.isEmpty()) {
            return List.of();
        }
        Map<Long, String> orgNames = organizationQueryService.findOrganizationNamesByIds(orgIds);
        List<TransferLineSnapshot> lines = new ArrayList<>();
        for (EncounterHistoryMilestoneQueryModel milestone : transferMilestones) {
            TransferLineSnapshot line = new TransferLineSnapshot();
            line.setFromDeptName(resolveOrgName(orgNames, milestone.getOriginalOrganizationId()));
            line.setToDeptName(resolveOrgName(orgNames, milestone.getTargetOrganizationId()));
            lines.add(line);
        }
        return lines;
    }

    private boolean isTransferMilestone(String name) {
        if (name == null) {
            return false;
        }
        if (CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equals(name)) {
            return true;
        }
        return CustomConstants.EncounterHistoryMilestoneName.ADMISSION_TO_DEPT.equals(name);
    }

    private String resolveOrgName(Map<Long, String> orgNames, Long organizationId) {
        if (organizationId == null || orgNames == null) {
            return null;
        }
        String name = orgNames.get(organizationId);
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim();
    }
}
