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

package com.openhis.health.application.emergency.patientmanagement.service;

import com.openhis.health.application.emergency.patientmanagement.request.AdmitEmergencyPatientRequest;
import com.openhis.health.application.emergency.patientmanagement.request.DischargeFromEmergencyAreaRequest;
import com.openhis.health.application.emergency.patientmanagement.request.EndEmergencyVisitRequest;
import com.openhis.health.application.emergency.patientmanagement.request.TransferEmergencyAreaRequest;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.repository.EncounterLocationRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.custom.encounterhistory.command.RecordAdmissionMilestoneCommand;
import com.openhis.health.domain.custom.encounterhistory.command.RecordDischargeMilestoneCommand;
import com.openhis.health.domain.custom.encounterhistory.command.RecordTransferMilestoneCommand;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryDomainService;
import com.openhis.health.domain.emergency.emgtriage.command.AmendEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriage;
import com.openhis.health.domain.emergency.emgtriage.repository.EmgTriageRepository;
import com.openhis.health.domain.emergency.emgtriage.service.EmgTriageDomainService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.events.administration.encounter.EmergencyEncounterCompletedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 患者管理工作站流转编排（应用服务）：入科、转区、出科、结束就诊。
 * <p>跨 Encounter / EmgTriage / EncounterHistory 聚合编排，不属于独立领域模块。</p>
 */
@Service
@RequiredArgsConstructor
public class PatientManagementFlowAppService {

    public static final String OUT_WAY_ED_LEAVE_AREA = "ed_leave_area";
    public static final String DESTINATION_END_VISIT = "end_visit";

    private final EncounterRepository encounterRepository;
    private final EncounterLocationRepository encounterLocationRepository;
    private final EmgTriageRepository emgTriageRepository;
    private final EmgTriageDomainService emgTriageDomainService;
    private final EncounterHistoryDomainService encounterHistoryDomainService;
    private final IDomainEventPublisher domainEventPublisher;

    public void admitEmergencyPatient(AdmitEmergencyPatientRequest request, Long operatorId) {
        requireFlowRequest(request.getEncounterId(), request.getEmgTriageId());
        if (request.getBedId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "床位不能为空");
        }
        LocalDateTime at = LocalDateTime.now();
        Encounter encounter = requireEmergencyEncounter(request.getEncounterId());
        assertEncounterActive(encounter);

        activateBed(request.getEncounterId(), request.getBedId(), at);
        updateTriageArea(request.getEmgTriageId(), request.getAreaLocationId(), operatorId, at);

        RecordAdmissionMilestoneCommand milestone = new RecordAdmissionMilestoneCommand();
        milestone.setEncounterId(request.getEncounterId());
        milestone.setTargetOrganizationId(encounter.getOrganizationId());
        milestone.setTargetLocationId(request.getBedId());
        milestone.setOperatorId(operatorId);
        encounterHistoryDomainService.recordAdmissionMilestone(milestone);
    }

    public void transferEmergencyArea(TransferEmergencyAreaRequest request, Long operatorId) {
        requireFlowRequest(request.getEncounterId(), request.getEmgTriageId());
        if (request.getTargetAreaLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "目标区域不能为空");
        }
        LocalDateTime at = LocalDateTime.now();
        Encounter encounter = requireEmergencyEncounter(request.getEncounterId());
        assertEncounterActive(encounter);

        Long fromBedId = resolveActiveBedId(request.getEncounterId());
        EmgTriage triage = requireActiveTriage(request.getEmgTriageId());
        Long fromAreaId = triage.getLocationId();

        if (request.getTargetBedId() != null) {
            activateBed(request.getEncounterId(), request.getTargetBedId(), at);
        } else if (fromBedId != null) {
            completeActiveLocations(request.getEncounterId(), at);
        }
        updateTriageArea(request.getEmgTriageId(), request.getTargetAreaLocationId(), operatorId, at);

        RecordTransferMilestoneCommand milestone = new RecordTransferMilestoneCommand();
        milestone.setEncounterId(request.getEncounterId());
        milestone.setOriginalOrganizationId(encounter.getOrganizationId());
        milestone.setTargetOrganizationId(encounter.getOrganizationId());
        Long originalLocationId;
        if (fromAreaId != null) {
            originalLocationId = fromAreaId;
        } else {
            originalLocationId = fromBedId;
        }
        milestone.setOriginalLocationId(originalLocationId);
        Long targetLocationId;
        if (request.getTargetBedId() != null) {
            targetLocationId = request.getTargetBedId();
        } else {
            targetLocationId = request.getTargetAreaLocationId();
        }
        milestone.setTargetLocationId(targetLocationId);
        milestone.setOperatorId(operatorId);
        encounterHistoryDomainService.recordTransferMilestone(milestone);
    }

    public void dischargeFromEmergencyArea(DischargeFromEmergencyAreaRequest request, Long operatorId) {
        requireFlowRequest(request.getEncounterId(), request.getEmgTriageId());
        LocalDateTime at = LocalDateTime.now();
        Encounter encounter = requireEmergencyEncounter(request.getEncounterId());
        assertEncounterActive(encounter);

        Long fromBedId = resolveActiveBedId(request.getEncounterId());
        EmgTriage triage = requireActiveTriage(request.getEmgTriageId());
        completeActiveLocations(request.getEncounterId(), at);

        RecordDischargeMilestoneCommand milestone = new RecordDischargeMilestoneCommand();
        milestone.setEncounterId(request.getEncounterId());
        milestone.setOriginalOrganizationId(encounter.getOrganizationId());
        Long dischargeOriginalLocationId;
        if (fromBedId != null) {
            dischargeOriginalLocationId = fromBedId;
        } else {
            dischargeOriginalLocationId = triage.getLocationId();
        }
        milestone.setOriginalLocationId(dischargeOriginalLocationId);
        milestone.setOutWayCode(StringUtils.defaultIfBlank(request.getOutWayCode(), OUT_WAY_ED_LEAVE_AREA));
        milestone.setOperatorId(operatorId);
        encounterHistoryDomainService.recordDischargeMilestone(milestone);
    }

    public void endEmergencyVisit(EndEmergencyVisitRequest request, Long operatorId) {
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        LocalDateTime at = LocalDateTime.now();
        Encounter encounter = requireEmergencyEncounter(request.getEncounterId());
        if (EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.CANCELLED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "就诊已结束，无法重复操作");
        }
        completeActiveLocations(request.getEncounterId(), at);
        encounter.transitToDischarged(at);
        encounterRepository.save(encounter);

        EmergencyEncounterCompletedEvent event = new EmergencyEncounterCompletedEvent(
            encounter.getId(),
            encounter.getPatientId(),
            encounter.getEndTime(),
            StringUtils.defaultIfBlank(request.getDestinationCode(), DESTINATION_END_VISIT));
        event.setStatusEnum(encounter.getStatusEnum());
        domainEventPublisher.publish(event);
    }

    private void requireFlowRequest(Long encounterId, Long emgTriageId) {
        if (encounterId == null || emgTriageId == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊或分诊标识不能为空");
        }
    }

    private Encounter requireEmergencyEncounter(Long encounterId) {
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
        encounter.assertClass(EncounterClass.EMER.getCode());
        return encounter;
    }

    private EmgTriage requireActiveTriage(Long emgTriageId) {
        return emgTriageRepository
            .findByIdAndTriageStatusEnumAndIsDeletedFalse(emgTriageId, EmgTriage.STATUS_ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "分诊记录不存在或已失效"));
    }

    private void assertEncounterActive(Encounter encounter) {
        if (EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.CANCELLED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.ENTERED_IN_ERROR.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "就诊已结束，无法操作");
        }
    }

    private void updateTriageArea(Long emgTriageId, Long areaLocationId, Long operatorId, LocalDateTime at) {
        if (areaLocationId == null) {
            return;
        }
        AmendEmgTriageCommand amend = new AmendEmgTriageCommand();
        amend.setEmgTriageId(emgTriageId);
        amend.setLocationId(areaLocationId);
        amend.setOperatorId(operatorId);
        amend.setChangeReasonCode("patient_management");
        amend.setChangeRemark("患者管理流转");
        amend.setOccurredAt(at);
        emgTriageDomainService.amendEmgTriage(amend);
    }

    private void activateBed(Long encounterId, Long bedId, LocalDateTime at) {
        List<EncounterLocation> toSave = new ArrayList<>();
        List<EncounterLocation> active = encounterLocationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        for (EncounterLocation old : active) {
            old.markCompleted(at);
            toSave.add(old);
        }
        EncounterLocation location = new EncounterLocation();
        location.setEncounterId(encounterId);
        location.setLocationId(bedId);
        location.setFormEnum(LocationForm.BED.getCode());
        location.markActive(at);
        toSave.add(location);
        encounterLocationRepository.saveAll(toSave);
    }

    private void completeActiveLocations(Long encounterId, LocalDateTime at) {
        List<EncounterLocation> active = encounterLocationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        if (active.isEmpty()) {
            return;
        }
        for (EncounterLocation old : active) {
            old.markCompleted(at);
        }
        encounterLocationRepository.saveAll(active);
    }

    private Long resolveActiveBedId(Long encounterId) {
        List<EncounterLocation> active = encounterLocationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        Optional<EncounterLocation> bed = active.stream()
            .filter(el -> LocationForm.BED.getCode().equals(el.getFormEnum()) || el.getFormEnum() == null)
            .findFirst();
        if (bed.isPresent()) {
            return bed.get().getLocationId();
        }
        if (active.isEmpty()) {
            return null;
        }
        return active.get(0).getLocationId();
    }
}
