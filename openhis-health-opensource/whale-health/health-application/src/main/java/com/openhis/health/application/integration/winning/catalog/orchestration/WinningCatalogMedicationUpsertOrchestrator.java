/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.integration.winning.catalog.orchestration;

import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogSyncFailureRow;
import com.openhis.health.application.integration.winning.catalog.dto.WinningMedicationCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.mapper.WinningCatalogSyncMapper;
import com.openhis.health.domain.medication.medicationdefinition.command.CreateMedicationDefinitionCommand;
import com.openhis.health.domain.medication.medicationdefinition.command.EditMedicationDefinitionCommand;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionDomainService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 药品目录幂等 upsert（按 orgId + busNo），供卫宁同步使用。
 */
@Component
@RequiredArgsConstructor
public class WinningCatalogMedicationUpsertOrchestrator {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final MedicationDefinitionDomainService medicationDefinitionDomainService;
    private final WinningCatalogSyncMapper mapper;

    public MedicationUpsertResult upsert(List<WinningMedicationCatalogItemDto> items, Long operatorId, boolean overwrite) {
        if (items == null || items.isEmpty()) {
            return MedicationUpsertResult.empty();
        }
        Long orgId = resolveSingleOrgId(items);
        List<String> busNos = new ArrayList<>();
        Map<String, WinningMedicationCatalogItemDto> itemByBusNo = new HashMap<>();
        List<WinningCatalogSyncFailureRow> failures = new ArrayList<>();
        int line = 0;
        for (WinningMedicationCatalogItemDto item : items) {
            line++;
            String busNo = mapper.resolveBusNo(item.getBusNo(), item.getExternalItemCode());
            if (busNo == null || StringUtils.isBlank(item.getName())) {
                failures.add(failure(line, busNo, item.getExternalItemCode(), "busNo 或名称为空"));
                continue;
            }
            if (item.getOrganizationId() == null) {
                item.setOrganizationId(orgId);
            }
            if (itemByBusNo.containsKey(busNo)) {
                failures.add(failure(line, busNo, item.getExternalItemCode(), "本批次 busNo 重复"));
                continue;
            }
            itemByBusNo.put(busNo, item);
            busNos.add(busNo);
        }
        List<MedicationDefinition> existed =
            medicationDefinitionQueryService.findByOrgIdAndBusNos(orgId, busNos);
        Map<String, MedicationDefinition> existedByBusNo = new HashMap<>();
        for (MedicationDefinition row : existed) {
            existedByBusNo.put(row.getBusNo(), row);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        Map<String, Long> instanceIdByBusNo = new HashMap<>();

        List<CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand> createItems = new ArrayList<>();
        for (Map.Entry<String, WinningMedicationCatalogItemDto> entry : itemByBusNo.entrySet()) {
            String busNo = entry.getKey();
            WinningMedicationCatalogItemDto src = entry.getValue();
            MedicationDefinition target = existedByBusNo.get(busNo);
            if (target == null) {
                createItems.add(toCreateItem(src, busNo));
            } else if (overwrite) {
                editExisting(target.getId(), src, operatorId);
                updated++;
                instanceIdByBusNo.put(busNo, target.getId());
            } else {
                skipped++;
                instanceIdByBusNo.put(busNo, target.getId());
            }
        }
        if (!createItems.isEmpty()) {
            CreateMedicationDefinitionCommand createCmd = new CreateMedicationDefinitionCommand();
            createCmd.setOperatorId(operatorId);
            createCmd.setItems(createItems);
            medicationDefinitionDomainService.createMedicationDefinition(createCmd);
            created += createItems.size();
            reloadInstanceIds(orgId, createItems, instanceIdByBusNo);
        }
        return new MedicationUpsertResult(created, updated, skipped, failures, instanceIdByBusNo);
    }

    private void reloadInstanceIds(
        Long orgId,
        List<CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand> createItems,
        Map<String, Long> instanceIdByBusNo) {
        Set<String> busNos = new HashSet<>();
        for (CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand item : createItems) {
            busNos.add(item.getBusNo());
        }
        for (MedicationDefinition row : medicationDefinitionQueryService.findByOrgIdAndBusNos(
            orgId, new ArrayList<>(busNos))) {
            instanceIdByBusNo.put(row.getBusNo(), row.getId());
        }
    }

    private void editExisting(Long id, WinningMedicationCatalogItemDto src, Long operatorId) {
        EditMedicationDefinitionCommand cmd = new EditMedicationDefinitionCommand();
        cmd.setOperatorId(operatorId);
        EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand item =
            new EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand();
        item.setId(id);
        item.setName(src.getName().trim());
        item.setCategoryCode(src.getCategoryCode());
        item.setPurchaseUnitCode(StringUtils.firstNonBlank(src.getPurchaseUnitCode(), "U"));
        cmd.setItems(List.of(item));
        medicationDefinitionDomainService.editMedicationDefinition(cmd);
    }

    private static CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand toCreateItem(
        WinningMedicationCatalogItemDto src,
        String busNo) {
        CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand item =
            new CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand();
        item.setBusNo(busNo);
        item.setName(src.getName().trim());
        item.setCategoryCode(src.getCategoryCode());
        item.setPurchaseUnitCode(StringUtils.firstNonBlank(src.getPurchaseUnitCode(), "U"));
        item.setOrgId(src.getOrganizationId());
        return item;
    }

    private static Long resolveSingleOrgId(List<WinningMedicationCatalogItemDto> items) {
        Long orgId = null;
        for (WinningMedicationCatalogItemDto item : items) {
            if (item.getOrganizationId() == null) {
                continue;
            }
            if (orgId == null) {
                orgId = item.getOrganizationId();
            } else if (!orgId.equals(item.getOrganizationId())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "一次同步仅支持单一机构");
            }
        }
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "药品同步缺少 organizationId");
        }
        return orgId;
    }

    private static WinningCatalogSyncFailureRow failure(int line, String busNo, String external, String reason) {
        WinningCatalogSyncFailureRow row = new WinningCatalogSyncFailureRow();
        row.setLineNo(line);
        row.setBusNo(busNo);
        row.setExternalItemCode(external);
        row.setReason(reason);
        return row;
    }

    @Getter
    public static final class MedicationUpsertResult {
        private final int createdCount;
        private final int updatedCount;
        private final int skippedCount;
        private final List<WinningCatalogSyncFailureRow> failures;
        private final Map<String, Long> instanceIdByBusNo;

        public MedicationUpsertResult(
            int createdCount,
            int updatedCount,
            int skippedCount,
            List<WinningCatalogSyncFailureRow> failures,
            Map<String, Long> instanceIdByBusNo) {
            this.createdCount = createdCount;
            this.updatedCount = updatedCount;
            this.skippedCount = skippedCount;
            this.failures = failures;
            this.instanceIdByBusNo = instanceIdByBusNo;
        }

        public static MedicationUpsertResult empty() {
            return new MedicationUpsertResult(0, 0, 0, List.of(), Map.of());
        }
    }
}
