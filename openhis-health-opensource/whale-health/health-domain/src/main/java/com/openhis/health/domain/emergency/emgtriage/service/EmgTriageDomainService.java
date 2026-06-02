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

package com.openhis.health.domain.emergency.emgtriage.service;

import com.openhis.health.domain.emergency.emgtriage.command.AmendEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.command.SaveEmgTriageAssessmentItemCommand;
import com.openhis.health.domain.emergency.emgtriage.command.SaveEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.command.VoidEmgTriageCommand;
import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriage;
import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriageAssessment;
import com.openhis.health.domain.emergency.emgtriage.repository.EmgTriageAssessmentRepository;
import com.openhis.health.domain.emergency.emgtriage.repository.EmgTriageRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.emergency.emgtriage.EmgTriageCreatedEvent;
import com.openhis.health.domain.share.events.emergency.emgtriage.EmgTriageGreenChannelActivatedEvent;
import com.openhis.health.domain.share.events.emergency.emgtriage.EmgTriageLevelOverriddenEvent;
import com.openhis.health.domain.share.events.emergency.emgtriage.EmgTriageSupersededEvent;
import com.openhis.health.domain.share.events.emergency.emgtriage.EmgTriageUpdatedEvent;
import com.openhis.health.domain.share.events.emergency.emgtriage.EmgTriageVoidedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 急诊分诊聚合写侧（SDD §5.1 / DM-EMG-001 v2.11）。
 */
@Service
@RequiredArgsConstructor
public class EmgTriageDomainService {

    private final EmgTriageRepository emgTriageRepository;
    private final EmgTriageAssessmentRepository emgTriageAssessmentRepository;
    private final IDomainEventPublisher domainEventPublisher;

    public EmgTriage saveEmgTriage(SaveEmgTriageCommand cmd) {
        assertSavePrerequisites(cmd);
        if (cmd.getId() != null && cmd.getPriorEmgTriageId() != null) {
            throw new BusinessException(
                    ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "二次分诊须新建分诊行，不能同时指定 id 与 priorEmgTriageId");
        }
        if (cmd.getPriorEmgTriageId() != null) {
            return saveSecondaryEmgTriage(cmd);
        }

        boolean isNew = cmd.getId() == null;
        Integer oldLevel = null;
        Boolean previousGreen = null;
        EmgTriage beforeRoot = null;

        EmgTriage root;
        if (isNew) {
            assertEncounterUnique(cmd.getEncounterId(), null);
            root = new EmgTriage();
            root.setIsDeleted(Boolean.FALSE);
            applySaveCommand(cmd, root);
        } else {
            root = emgTriageRepository
                    .findByIdAndTriageStatusEnumAndIsDeletedFalse(cmd.getId(), EmgTriage.STATUS_ACTIVE)
                    .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "分诊记录不存在或已失效"));
            assertEncounterUnique(cmd.getEncounterId(), root.getId());
            oldLevel = root.getTriageLevelEnum();
            previousGreen = root.getIsGreenChannel();
            beforeRoot = snapshotForDiff(root);
            boolean needsRevisionReason = criticalRevision(cmd, root);
            applySaveCommand(cmd, root);
            if (needsRevisionReason && StringUtils.isBlank(cmd.getChangeReasonCode())) {
                throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "修订分级或去向等须填写 changeReasonCode");
            }
            if (needsRevisionReason) {
                root.setLastAmendedBy(cmd.getOperatorId());
                root.setLastAmendedAt(LocalDateTime.now());
            }
        }

        boolean staged = Boolean.TRUE.equals(cmd.getIsStaged());

        root.assertChiefComplaintJsonForSave(Boolean.TRUE.equals(cmd.getChiefComplaintOptional()));
        if (!staged) {
            root.assertLevelAdjustReasonWhenOverride();
            root.assertGreenChannelLevel();
            root.assertDestinationModeAndSecondary();
            root.assertTransferInWhenExternal();
        }
        root.assertQueuePriorityPositive();
        root.assertStagingDoesNotQueue();

        root = emgTriageRepository.save(root);
        Long triageId = root.getId();

        syncAssessments(triageId, cmd.getAssessmentItems(), cmd.getOperatorId());

        root.assertChiefComplaintJsonForSave(Boolean.TRUE.equals(cmd.getChiefComplaintOptional()));
        if (!staged) {
            root.assertLevelAdjustReasonWhenOverride();
            root.assertGreenChannelLevel();
        }

        root = emgTriageRepository.save(root);

        if (isNew) {
            domainEventPublisher.publish(new EmgTriageCreatedEvent(
                    root.getId(), root.getPatientId(), root.getEncounterId(), root.getTriageLevelEnum()));
        } else {
            String summary = beforeRoot != null ? diffRoots(beforeRoot, root) : "updated";
            domainEventPublisher.publish(new EmgTriageUpdatedEvent(root.getId(), null, summary));
            if (root.isLevelOverride(oldLevel)) {
                domainEventPublisher.publish(new EmgTriageLevelOverriddenEvent(
                        root.getId(),
                        oldLevel,
                        root.getTriageLevelEnum(),
                        root.getLevelAdjustReason(),
                        cmd.getOperatorId()));
            }
            if (root.isGreenChannelActivation(previousGreen)) {
                domainEventPublisher.publish(new EmgTriageGreenChannelActivatedEvent(
                        root.getId(), root.getEncounterId(), root.getTriageLevelEnum()));
            }
        }
        return root;
    }

    /**
     * 二次分诊：前序行 {@code SUPERSEDED}，新建行 {@code ACTIVE}，同窗补齐 {@code superseded_by_id}（BR-015/017）。
     */
    private EmgTriage saveSecondaryEmgTriage(SaveEmgTriageCommand cmd) {
        EmgTriage prior =
                emgTriageRepository.findById(cmd.getPriorEmgTriageId()).orElseThrow(
                        () -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "前序分诊不存在"));
        if (!EmgTriage.STATUS_ACTIVE.equals(prior.getTriageStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_STATUS, "前序分诊须为 ACTIVE");
        }
        if (!prior.getEncounterId().equals(cmd.getEncounterId()) || !prior.getPatientId().equals(cmd.getPatientId())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "前序分诊与本次就诊/患者不一致");
        }

        // 修改原因归属被取代的前序行，不写入新建 ACTIVE 行（避免 UI 与审计错位）。
        if (StringUtils.isNotBlank(cmd.getChangeReasonCode())) {
            prior.setChangeReasonCode(cmd.getChangeReasonCode());
            prior.setChangeRemark(cmd.getChangeRemark());
        }

        prior.setTriageStatusEnum(EmgTriage.STATUS_SUPERSEDED);
        prior.setSupersededById(null);
        emgTriageRepository.save(prior);

        EmgTriage root = new EmgTriage();
        root.setIsDeleted(Boolean.FALSE);
        applySaveCommand(cmd, root);
        root.setChangeReasonCode(null);
        root.setChangeRemark(null);
        root.setTriageStatusEnum(cmd.getTriageStatusEnum() != null ? cmd.getTriageStatusEnum() : EmgTriage.STATUS_ACTIVE);

        root.assertChiefComplaintJsonForSave(Boolean.TRUE.equals(cmd.getChiefComplaintOptional()));
        root.assertLevelAdjustReasonWhenOverride();
        root.assertGreenChannelLevel();
        root.assertDestinationModeAndSecondary();
        root.assertTransferInWhenExternal();
        root.assertQueuePriorityPositive();
        root.assertStagingDoesNotQueue();

        root = emgTriageRepository.save(root);
        syncAssessments(root.getId(), cmd.getAssessmentItems(), cmd.getOperatorId());

        prior.setSupersededById(root.getId());
        emgTriageRepository.save(prior);

        domainEventPublisher.publish(new EmgTriageCreatedEvent(
                root.getId(), root.getPatientId(), root.getEncounterId(), root.getTriageLevelEnum()));
        domainEventPublisher.publish(new EmgTriageSupersededEvent(
                prior.getId(), root.getId(), root.getEncounterId(), root.getPatientId()));

        return root;
    }

    public EmgTriage amendEmgTriage(AmendEmgTriageCommand cmd) {
        if (cmd.getEmgTriageId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "分诊主键与操作人不能为空");
        }
        if (StringUtils.isBlank(cmd.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "修订须填写 changeReasonCode");
        }
        EmgTriage root = emgTriageRepository
                .findByIdAndTriageStatusEnumAndIsDeletedFalse(cmd.getEmgTriageId(), EmgTriage.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "分诊记录不存在或已失效"));

        Integer oldLevel = root.getTriageLevelEnum();
        Boolean previousGreen = root.getIsGreenChannel();
        EmgTriage before = snapshotForDiff(root);

        root.assertAmendAllowed();
        applyAmendCommand(cmd, root);

        root.assertLevelAdjustReasonWhenOverride();
        root.assertGreenChannelLevel();
        root.assertDestinationModeAndSecondary();
        root.assertQueuePriorityPositive();

        LocalDateTime amendedAt = cmd.getOccurredAt() != null ? cmd.getOccurredAt() : LocalDateTime.now();
        root.setLastAmendedBy(cmd.getOperatorId());
        root.setLastAmendedAt(amendedAt);

        root = emgTriageRepository.save(root);

        domainEventPublisher.publish(new EmgTriageUpdatedEvent(root.getId(), null, diffRoots(before, root)));
        boolean levelOverride = !Objects.equals(oldLevel, root.getTriageLevelEnum())
                && StringUtils.isNotBlank(cmd.getLevelAdjustReason());
        if (levelOverride) {
            domainEventPublisher.publish(new EmgTriageLevelOverriddenEvent(
                    root.getId(),
                    oldLevel,
                    root.getTriageLevelEnum(),
                    cmd.getLevelAdjustReason(),
                    cmd.getOperatorId()));
        }
        if (root.isGreenChannelActivation(previousGreen)) {
            domainEventPublisher.publish(new EmgTriageGreenChannelActivatedEvent(
                    root.getId(), root.getEncounterId(), root.getTriageLevelEnum()));
        }
        return root;
    }

    public boolean voidEmgTriage(VoidEmgTriageCommand cmd) {
        if (cmd.getEmgTriageId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "分诊主键不能为空");
        }
        EmgTriage root = emgTriageRepository.findById(cmd.getEmgTriageId()).orElseThrow(
                () -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "分诊记录不存在"));
        root.applyVoid(cmd.getOperatorId(), cmd.getVoidReason());
        emgTriageRepository.save(root);
        domainEventPublisher.publish(new EmgTriageVoidedEvent(root.getId(), cmd.getVoidReason(), cmd.getOperatorId()));
        return true;
    }

    private void assertSavePrerequisites(SaveEmgTriageCommand cmd) {
        if (cmd.getEncounterId() == null
                || cmd.getPatientId() == null
                || cmd.getTriageTime() == null
                || cmd.getTriageLevelEnum() == null
                || cmd.getOrganizationId() == null
                || cmd.getLocationId() == null
                || cmd.getDestinationModeEnum() == null
                || cmd.getQueuePriority() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "分诊保存必填字段不完备");
        }
    }

    private void assertEncounterUnique(Long encounterId, Long excludeId) {
        Optional<EmgTriage> other =
                emgTriageRepository.findByEncounterIdAndTriageStatusEnumAndIsDeletedFalse(encounterId, EmgTriage.STATUS_ACTIVE);
        if (other.isPresent() && (excludeId == null || !other.get().getId().equals(excludeId))) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_CONFLICT, "同一就诊仅允许一条有效分诊");
        }
    }

    private boolean criticalRevision(SaveEmgTriageCommand cmd, EmgTriage beforeApply) {
        return !Objects.equals(beforeApply.getTriageLevelEnum(), cmd.getTriageLevelEnum())
                || !Objects.equals(beforeApply.getOrganizationId(), cmd.getOrganizationId())
                || !Objects.equals(beforeApply.getLocationId(), cmd.getLocationId())
                || !Objects.equals(beforeApply.getSecondaryLocationId(), cmd.getSecondaryLocationId())
                || !Objects.equals(beforeApply.getDestinationModeEnum(), cmd.getDestinationModeEnum())
                || !Objects.equals(beforeApply.getQueuePriority(), cmd.getQueuePriority());
    }

    private void applySaveCommand(SaveEmgTriageCommand cmd, EmgTriage root) {
        root.setEncounterId(cmd.getEncounterId());
        root.setPatientId(cmd.getPatientId());
        root.setTriageTime(cmd.getTriageTime());
        root.setTriageStatusEnum(cmd.getTriageStatusEnum() != null ? cmd.getTriageStatusEnum() : EmgTriage.STATUS_ACTIVE);
        root.setChiefComplaintJson(cmd.getChiefComplaintJson());
        root.setSymptomJson(cmd.getSymptomJson());
        root.setTriageLevelEnum(cmd.getTriageLevelEnum());
        root.setSuggestedLevelEnum(cmd.getSuggestedLevelEnum());
        root.setLevelAdjustReason(cmd.getLevelAdjustReason());
        root.setChangeReasonCode(cmd.getChangeReasonCode());
        root.setChangeRemark(cmd.getChangeRemark());
        root.setOrganizationId(cmd.getOrganizationId());
        root.setLocationId(cmd.getLocationId());
        root.setAssignedDoctorSnapshot(cmd.getAssignedDoctorSnapshot());
        root.setDiseaseTypesJson(cmd.getDiseaseTypesJson());
        root.setPatientSource(cmd.getPatientSource());
        root.setIsGreenChannel(Boolean.TRUE.equals(cmd.getIsGreenChannel()));
        root.setIsFreeRegister(Boolean.TRUE.equals(cmd.getIsFreeRegister()));
        root.setQueuePriority(cmd.getQueuePriority());
        root.setNote(cmd.getNote());
        root.setTriageRegistrationModeEnum(cmd.getTriageRegistrationModeEnum());
        root.setIsStaged(Boolean.TRUE.equals(cmd.getIsStaged()));
        root.setStageQueueSeq(cmd.getStageQueueSeq());
        root.setProvisionalTriageNo(cmd.getProvisionalTriageNo());
        root.setSpecialPopulationTagsJson(cmd.getSpecialPopulationTagsJson());
        root.setMciEventId(cmd.getMciEventId());
        root.setBlacklistAlertAck(cmd.getBlacklistAlertAck());
        root.setArrivalModeEnum(cmd.getArrivalModeEnum());
        root.setTransferInOrgId(cmd.getTransferInOrgId());
        root.setDestinationModeEnum(cmd.getDestinationModeEnum());
        root.setSecondaryLocationId(cmd.getSecondaryLocationId());
        root.setGreenChannelOnsetTime(cmd.getGreenChannelOnsetTime());
        root.setEpidemicSurveyJson(cmd.getEpidemicSurveyJson());
        root.setSpecialtyGreenChannelCode(cmd.getSpecialtyGreenChannelCode());
        root.setAmbulance120Json(cmd.getAmbulance120Json());
        root.setPolice110Json(cmd.getPolice110Json());
        root.setMonitorDeviceBindingJson(cmd.getMonitorDeviceBindingJson());
        root.setWristbandPrintedAt(cmd.getWristbandPrintedAt());
        root.setVitalsAutoTierPolicyId(cmd.getVitalsAutoTierPolicyId());
        if (cmd.getPriorEmgTriageId() != null) {
            root.setPriorEmgTriageId(cmd.getPriorEmgTriageId());
        }
    }

    private void applyAmendCommand(AmendEmgTriageCommand cmd, EmgTriage root) {
        if (cmd.getTriageLevelEnum() != null) {
            root.setTriageLevelEnum(cmd.getTriageLevelEnum());
        }
        if (cmd.getSuggestedLevelEnum() != null) {
            root.setSuggestedLevelEnum(cmd.getSuggestedLevelEnum());
        }
        if (cmd.getLevelAdjustReason() != null) {
            root.setLevelAdjustReason(cmd.getLevelAdjustReason());
        }
        if (cmd.getOrganizationId() != null) {
            root.setOrganizationId(cmd.getOrganizationId());
        }
        if (cmd.getLocationId() != null) {
            root.setLocationId(cmd.getLocationId());
        }
        if (cmd.getSecondaryLocationId() != null) {
            root.setSecondaryLocationId(cmd.getSecondaryLocationId());
        }
        if (cmd.getDestinationModeEnum() != null) {
            root.setDestinationModeEnum(cmd.getDestinationModeEnum());
        }
        if (cmd.getQueuePriority() != null) {
            root.setQueuePriority(cmd.getQueuePriority());
        }
        if (cmd.getNote() != null) {
            root.setNote(cmd.getNote());
        }
        if (cmd.getIsGreenChannel() != null) {
            root.setIsGreenChannel(cmd.getIsGreenChannel());
        }
        if (cmd.getIsFreeRegister() != null) {
            root.setIsFreeRegister(cmd.getIsFreeRegister());
        }
        if (cmd.getGreenChannelOnsetTime() != null) {
            root.setGreenChannelOnsetTime(cmd.getGreenChannelOnsetTime());
        }
        root.setChangeReasonCode(cmd.getChangeReasonCode());
        root.setChangeRemark(cmd.getChangeRemark());
    }

    private void syncAssessments(Long triageId, List<SaveEmgTriageAssessmentItemCommand> items, Long operatorId) {
        List<EmgTriageAssessment> existing = emgTriageAssessmentRepository.findByEmgTriageIdAndIsDeletedFalse(triageId);
        Set<Long> incomingIds =
                items.stream().map(SaveEmgTriageAssessmentItemCommand::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (EmgTriageAssessment row : existing) {
            if (!incomingIds.contains(row.getId())) {
                row.markDeleted(operatorId);
                emgTriageAssessmentRepository.save(row);
            }
        }
        for (SaveEmgTriageAssessmentItemCommand item : items) {
            if (item.getId() != null) {
                EmgTriageAssessment row = emgTriageAssessmentRepository
                        .findByIdAndEmgTriageIdAndIsDeletedFalse(item.getId(), triageId)
                        .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_NOT_FOUND, "分诊评估不存在"));
                applyAssessmentItem(item, row);
                emgTriageAssessmentRepository.save(row);
            } else {
                EmgTriageAssessment row = new EmgTriageAssessment();
                row.setEmgTriageId(triageId);
                row.setIsDeleted(Boolean.FALSE);
                applyAssessmentItem(item, row);
                emgTriageAssessmentRepository.save(row);
            }
        }
    }

    private void applyAssessmentItem(SaveEmgTriageAssessmentItemCommand item, EmgTriageAssessment row) {
        if (item.getAssessmentType() == null
                || item.getAssessmentData() == null
                || item.getAssessedAt() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "评估 assessmentType/assessmentData/assessedAt 不能为空");
        }
        row.setAssessmentType(item.getAssessmentType());
        row.setAssessmentData(item.getAssessmentData());
        row.setAssessmentScore(item.getAssessmentScore());
        row.setAssessedAt(item.getAssessedAt());
        row.setAssessedBy(item.getAssessedBy());
    }

    private EmgTriage snapshotForDiff(EmgTriage src) {
        EmgTriage copy = new EmgTriage();
        copy.setEncounterId(src.getEncounterId());
        copy.setPatientId(src.getPatientId());
        copy.setTriageTime(src.getTriageTime());
        copy.setTriageStatusEnum(src.getTriageStatusEnum());
        copy.setChiefComplaintJson(src.getChiefComplaintJson());
        copy.setSymptomJson(src.getSymptomJson());
        copy.setTriageLevelEnum(src.getTriageLevelEnum());
        copy.setSuggestedLevelEnum(src.getSuggestedLevelEnum());
        copy.setLevelAdjustReason(src.getLevelAdjustReason());
        copy.setOrganizationId(src.getOrganizationId());
        copy.setLocationId(src.getLocationId());
        copy.setSecondaryLocationId(src.getSecondaryLocationId());
        copy.setDestinationModeEnum(src.getDestinationModeEnum());
        copy.setQueuePriority(src.getQueuePriority());
        copy.setIsGreenChannel(src.getIsGreenChannel());
        copy.setIsFreeRegister(src.getIsFreeRegister());
        copy.setNote(src.getNote());
        copy.setGreenChannelOnsetTime(src.getGreenChannelOnsetTime());
        return copy;
    }

    private String diffRoots(EmgTriage before, EmgTriage after) {
        List<String> fields = new ArrayList<>();
        addIfDiff(fields, "triageLevelEnum", before.getTriageLevelEnum(), after.getTriageLevelEnum());
        addIfDiff(fields, "suggestedLevelEnum", before.getSuggestedLevelEnum(), after.getSuggestedLevelEnum());
        addIfDiff(fields, "levelAdjustReason", before.getLevelAdjustReason(), after.getLevelAdjustReason());
        addIfDiff(fields, "chiefComplaintJson", before.getChiefComplaintJson(), after.getChiefComplaintJson());
        addIfDiff(fields, "symptomJson", before.getSymptomJson(), after.getSymptomJson());
        addIfDiff(fields, "organizationId", before.getOrganizationId(), after.getOrganizationId());
        addIfDiff(fields, "locationId", before.getLocationId(), after.getLocationId());
        addIfDiff(fields, "secondaryLocationId", before.getSecondaryLocationId(), after.getSecondaryLocationId());
        addIfDiff(fields, "destinationModeEnum", before.getDestinationModeEnum(), after.getDestinationModeEnum());
        addIfDiff(fields, "queuePriority", before.getQueuePriority(), after.getQueuePriority());
        addIfDiff(fields, "isGreenChannel", before.getIsGreenChannel(), after.getIsGreenChannel());
        addIfDiff(fields, "isFreeRegister", before.getIsFreeRegister(), after.getIsFreeRegister());
        addIfDiff(fields, "note", before.getNote(), after.getNote());
        addIfDiff(fields, "greenChannelOnsetTime", before.getGreenChannelOnsetTime(), after.getGreenChannelOnsetTime());
        return fields.isEmpty() ? "unchanged" : String.join(",", fields);
    }

    private void addIfDiff(List<String> fields, String name, Object a, Object b) {
        if (!Objects.equals(a, b)) {
            fields.add(name);
        }
    }
}
