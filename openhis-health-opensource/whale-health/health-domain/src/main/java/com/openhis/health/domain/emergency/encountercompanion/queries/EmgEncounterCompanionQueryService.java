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

package com.openhis.health.domain.emergency.encountercompanion.queries;

import com.openhis.health.domain.emergency.encountercompanion.entity.EmgEncounterCompanion;
import com.openhis.health.domain.emergency.encountercompanion.queries.model.EmgEncounterCompanionListItemQueryModel;
import com.openhis.health.domain.emergency.encountercompanion.repository.EmgEncounterCompanionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmgEncounterCompanionQueryService {

    private final EmgEncounterCompanionRepository repository;

    public List<EmgEncounterCompanionListItemQueryModel> listActiveCompanionsByEncounter(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<EmgEncounterCompanionListItemQueryModel> result = new ArrayList<>();
        for (EmgEncounterCompanion row : repository.findAllByEncounterIdAndStatusEnumAndIsDeletedFalseOrderByCreatedAtDesc(
            encounterId, EmgEncounterCompanion.STATUS_ACTIVE)) {
            result.add(toModel(row));
        }
        return result;
    }

    /**
     * 同就诊下是否存在其他生效陪护使用相同身份证号（软提示，不拦截保存）。
     */
    public boolean hasActiveDuplicateIdCard(Long encounterId, String idCard, Long excludeCompanionId) {
        if (encounterId == null || StringUtils.isBlank(idCard)) {
            return false;
        }
        String normalized = idCard.trim();
        List<EmgEncounterCompanion> rows = repository.findAllByEncounterIdAndIdCardAndStatusEnumAndIsDeletedFalse(
            encounterId, normalized, EmgEncounterCompanion.STATUS_ACTIVE);
        if (rows.isEmpty()) {
            return false;
        }
        if (excludeCompanionId == null) {
            return true;
        }
        return rows.stream().anyMatch(r -> !excludeCompanionId.equals(r.getId()));
    }

    public EmgEncounterCompanionListItemQueryModel findActiveById(Long companionId) {
        if (companionId == null) {
            return null;
        }
        return repository.findByIdAndIsDeletedFalse(companionId)
            .filter(r -> EmgEncounterCompanion.STATUS_ACTIVE.equals(r.getStatusEnum()))
            .map(EmgEncounterCompanionQueryService::toModel)
            .orElse(null);
    }

    private static EmgEncounterCompanionListItemQueryModel toModel(EmgEncounterCompanion row) {
        EmgEncounterCompanionListItemQueryModel m = new EmgEncounterCompanionListItemQueryModel();
        m.setId(row.getId());
        m.setEncounterId(row.getEncounterId());
        m.setPatientId(row.getPatientId());
        m.setName(row.getName());
        m.setIdCard(row.getIdCard());
        m.setGenderEnum(row.getGenderEnum());
        m.setPhone(row.getPhone());
        m.setRelationshipCode(row.getRelationshipCode());
        m.setIdCardSourceEnum(row.getIdCardSourceEnum());
        m.setWristbandPrintedAt(row.getWristbandPrintedAt());
        m.setRemark(row.getRemark());
        m.setCreatedAt(row.getCreatedAt());
        return m;
    }
}
