/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.adapter;

import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.bridge.shared.organization.OrganizationDisplayResolver;
import com.openhis.whale.mrms.port.EncounterHistoryPort;
import com.openhis.whale.mrms.port.model.TransferLineSnapshot;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * health 对 MRMS {@link EncounterHistoryPort} 的实现：转科轨迹与出院方式。
 */
@Component
@Primary
public class HealthMrmsEncounterHistoryAdapter implements EncounterHistoryPort {

    private final EncounterHistoryQueryService encounterHistoryQueryService;
    private final OrganizationDisplayResolver organizationDisplayResolver;

    public HealthMrmsEncounterHistoryAdapter(
            EncounterHistoryQueryService encounterHistoryQueryService,
            OrganizationDisplayResolver organizationDisplayResolver) {
        this.encounterHistoryQueryService = encounterHistoryQueryService;
        this.organizationDisplayResolver = organizationDisplayResolver;
    }

    @Override
    public List<TransferLineSnapshot> listTransfers(long encounterId) {
        List<EncounterHistoryMilestoneQueryModel> milestones =
                encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        Set<Long> organizationIds = new HashSet<>();
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (!CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equalsIgnoreCase(milestone.getName())) {
                continue;
            }
            if (milestone.getOriginalOrganizationId() != null) {
                organizationIds.add(milestone.getOriginalOrganizationId());
            }
            if (milestone.getTargetOrganizationId() != null) {
                organizationIds.add(milestone.getTargetOrganizationId());
            }
        }
        Map<Long, String> organizationNameById = organizationDisplayResolver.resolveDisplayNames(organizationIds);
        List<TransferLineSnapshot> result = new ArrayList<>();
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (!CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equalsIgnoreCase(milestone.getName())) {
                continue;
            }
            TransferLineSnapshot snapshot = new TransferLineSnapshot();
            snapshot.setTransferAt(milestone.getCreatedAt());
            if (milestone.getOriginalOrganizationId() != null) {
                Long fromOrgId = milestone.getOriginalOrganizationId();
                snapshot.setFromDeptCode(String.valueOf(fromOrgId));
                snapshot.setFromDeptName(resolveOrganizationName(fromOrgId, organizationNameById));
            }
            if (milestone.getTargetOrganizationId() != null) {
                Long toOrgId = milestone.getTargetOrganizationId();
                snapshot.setToDeptCode(String.valueOf(toOrgId));
                snapshot.setToDeptName(resolveOrganizationName(toOrgId, organizationNameById));
            }
            result.add(snapshot);
        }
        return result;
    }

    @Override
    public Optional<String> resolveDischargeWayCode(long encounterId) {
        return Optional.ofNullable(encounterHistoryQueryService.resolveDischargeWay(encounterId))
                .map(model -> model.getOutWayCode());
    }

    private static String resolveOrganizationName(Long organizationId, Map<Long, String> organizationNameById) {
        String name = organizationNameById.get(organizationId);
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return String.valueOf(organizationId);
    }
}
