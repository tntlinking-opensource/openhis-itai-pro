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

package com.openhis.health.domain.emergency.patientalert.queries;

import com.openhis.health.domain.emergency.patientalert.entity.PatientAlert;
import com.openhis.health.domain.emergency.patientalert.entity.VisitRestriction;
import com.openhis.health.domain.emergency.patientalert.queries.model.PatientAlertActiveListItemQueryModel;
import com.openhis.health.domain.emergency.patientalert.queries.model.VisitRestrictionListItemQueryModel;
import com.openhis.health.domain.emergency.patientalert.repository.PatientAlertRepository;
import com.openhis.health.domain.emergency.patientalert.repository.VisitRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 患者警示读侧（DM-EMG-005 §5.2）。
 */
@Service
@RequiredArgsConstructor
public class PatientAlertQueryService {

    private final PatientAlertRepository patientAlertRepository;
    private final VisitRestrictionRepository visitRestrictionRepository;

    public List<PatientAlertActiveListItemQueryModel> listActiveAlertsByPatient(Long patientId, LocalDateTime asOf) {
        if (patientId == null) {
            return List.of();
        }
        LocalDateTime point = asOf != null ? asOf : LocalDateTime.now();
        List<PatientAlert> rows = patientAlertRepository.findAllByPatientIdAndStatusEnumAndIsDeletedFalseOrderByCreatedAtDesc(
            patientId, PatientAlert.STATUS_ACTIVE);
        List<PatientAlertActiveListItemQueryModel> result = new ArrayList<>();
        for (PatientAlert row : rows) {
            if (!row.isEffectiveAt(point)) {
                continue;
            }
            result.add(toActiveModel(row));
        }
        return result;
    }

    public List<VisitRestrictionListItemQueryModel> listRestrictionsByEncounter(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        return visitRestrictionRepository.findAllByEncounterIdAndIsDeletedFalseOrderByCreatedAtDesc(encounterId).stream()
            .map(this::toRestrictionModel)
            .toList();
    }

    private static PatientAlertActiveListItemQueryModel toActiveModel(PatientAlert row) {
        PatientAlertActiveListItemQueryModel m = new PatientAlertActiveListItemQueryModel();
        m.setId(row.getId());
        m.setPatientId(row.getPatientId());
        m.setAlertTypeEnum(row.getAlertTypeEnum());
        m.setMarkTypeCode(row.getMarkTypeCode());
        m.setReasonText(row.getReasonText());
        m.setStatusEnum(row.getStatusEnum());
        m.setEffectiveFrom(row.getEffectiveFrom());
        m.setEffectiveTo(row.getEffectiveTo());
        m.setCreatedBy(row.getCreatedBy());
        m.setCreatedAt(row.getCreatedAt());
        return m;
    }

    private VisitRestrictionListItemQueryModel toRestrictionModel(VisitRestriction row) {
        VisitRestrictionListItemQueryModel m = new VisitRestrictionListItemQueryModel();
        m.setId(row.getId());
        m.setEncounterId(row.getEncounterId());
        m.setRestrictionTypeEnum(row.getRestrictionTypeEnum());
        m.setReasonText(row.getReasonText());
        m.setStatusEnum(row.getStatusEnum());
        m.setLiftedAt(row.getLiftedAt());
        m.setOperatedBy(row.getOperatedBy());
        m.setCreatedAt(row.getCreatedAt());
        return m;
    }
}
