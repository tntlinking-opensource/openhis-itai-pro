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

package com.openhis.health.domain.administration.encounter.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.EncounterConstants;
import com.openhis.health.domain.share.constants.RegistrationSlipConstants;
import com.openhis.health.domain.administration.encounter.command.*;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.repository.EncounterDiagnosisRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterLocationRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterParticipantRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.repository.ConditionRepository;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.clinical.condition.service.ConditionDomainService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.events.administration.encounter.*;
import com.openhis.health.domain.share.utils.CommandBatchUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 就诊写侧领域服务：门诊/住院开立、状态迁移、参与者与位置、诊断子表及领域事件发布（B.14 先落库后发事件）。
 */
@Service
@RequiredArgsConstructor
public class EncounterDomainService {

    private final EncounterRepository encounterRepository;
    private final EncounterParticipantRepository participantRepository;
    private final EncounterLocationRepository locationRepository;
    private final EncounterDiagnosisRepository diagnosisRepository;
    private final ConditionRepository conditionRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 就诊等业务流水号，按日递增，前缀对齐 {@link AggregateTypeConstants#ENCOUNTER}。 */
    private final BusinessSequenceService businessSequenceService;
    /** 病情事实写侧（入院新开诊断）。 */
    private final ConditionDomainService conditionDomainService;
    /** 就诊账户（住院登记时开立 0 元账户）。 */
    private final AccountDomainService accountDomainService;
    /** 就诊读侧：活动床位/病房位置解析（与 {@link #requireActiveBedId} 共用口径）。 */
    private final EncounterQueryService encounterQueryService;
    /**
     * 开立门诊就诊。
     *
     * @param command 命令
     * @return 首条就诊标识
     */
    public Long registerOutpatientEncounter(RegisterOutpatientEncounterCommand command) {
        List<RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand> items = command.resolveItems();
        List<Encounter> toSave = new ArrayList<>();
        LocalDateTime occurredAt = command.getOccurredAt();
        // 业务分支处理。
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
        List<String> encounterBusNos = this.allocateEncounterBusNos(items.size());
        for (int index = 0; index < items.size(); index++) {
            RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand item = items.get(index);
            // 先执行门诊防重：同日、同患者、同目标科室且已存在已交费就诊时不允许再开立草稿。
            this.assertNoOutpatientDuplicate(item.getPatientId(), item.getOrganizationId(), occurredAt.toLocalDate());
            Encounter encounter = new Encounter();
            encounter.setPatientId(item.getPatientId());
            encounter.setServiceTypeId(item.getServiceTypeId());
            encounter.setOrganizationId(item.getOrganizationId());
            // 就诊 busNo 为业务流水：按自然日分段递增，前缀取 AggregateType.ENCOUNTER。
            encounter.setBusNo(encounterBusNos.get(index));
            encounter.initOutpatientPlanned();
            // 若号源类型为急诊，委托聚合根 applyEmergencyClassIfNeeded() 覆盖初始 classEnum。
            encounter.applyEmergencyClassIfNeeded(item.getClassEnum());
            // 挂号发生时刻落库 start_time，供挂号列表/日期窗 coalesce(start_time, created_at) 与 QueryService 直读。
            LocalDateTime registrationStartTime = item.getStartTime();
            if (registrationStartTime == null) {
                registrationStartTime = occurredAt;
            }
            encounter.setStartTime(registrationStartTime);
            encounter.setRegistrarId(item.getRegistrarId());
            encounter.setDisplayOrder(item.getDisplayOrder());
            encounter.assertOutpatientRegistrationRequiredFields();
            toSave.add(encounter);
        }
        List<Encounter> saved = encounterRepository.saveAll(toSave);
        List<EncounterParticipant> initParticipants = new ArrayList<>();
        for (int index = 0; index < saved.size(); index++) {
            Encounter encounter = saved.get(index);
            RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand item = items.get(index);
            // 完成落库后再发布事件，避免先发后失败导致幽灵事件。
            OutpatientRegistrationPlannedEvent event = new OutpatientRegistrationPlannedEvent(
                encounter.getId(), encounter.getPatientId(), encounter.getServiceTypeId(),
                encounter.getOrganizationId(), encounter.getBusNo());
            event.setSlipNo(RegistrationSlipConstants.SLIP_NO_PREFIX + encounter.getId());
            // 将应用层装配的展示快照写入事件，供挂号读模型投影器一次性物化列表行。
            event.setPatientBusNo(item.getPatientBusNo());
            event.setPatientName(item.getPatientName());
            event.setPyStr(item.getPyStr());
            event.setWbStr(item.getWbStr());
            event.setGenderEnum(item.getGenderEnum());
            event.setGenderEnumText(item.getGenderEnumText());
            event.setPhone(item.getPhone());
            event.setBirthDate(item.getBirthDate());
            event.setPatientAgeText(item.getPatientAgeText());
            event.setIdCard(item.getIdCard());
            event.setOrganizationName(item.getOrganizationName());
            event.setServiceTypeName(item.getServiceTypeName());
            event.setStartTime(encounter.getStartTime());
            event.setDisplayOrder(item.getDisplayOrder());
            event.setRegistrarId(item.getRegistrarId());
            event.setRegistrarName(item.getRegistrarName());
            event.setClassEnum(encounter.getClassEnum());
            event.setStatusEnum(encounter.getStatusEnum());
            // 挂号阶段若已指定医师，将主键与姓名快照写入事件，列表与挂号单可在接诊前展示意向医生。
            event.setRegistrationDoctorId(item.getPractitionerId());
            event.setRegistrationDoctorName(item.getPractitionerName());
            domainEventPublisher.publish(event);
            // 业务分支处理。
            if (item.getPractitionerId() != null) {
                EncounterParticipant participant = new EncounterParticipant();
                participant.setEncounterId(encounter.getId());
                participant.setRoleCode(EncounterConstants.ROLE_ADMITTER);
                participant.setPractitionerId(item.getPractitionerId());
                participant.markActive(occurredAt);
                initParticipants.add(participant);
            }
        }
        // 业务分支处理。
        if (!initParticipants.isEmpty()) {
            participantRepository.saveAll(initParticipants);
        }
        return saved.get(0).getId();
    }

    /**
     * 门急诊分诊。
     *
     * @param command 命令
     */
    public void triageEncounter(TriageEncounterCommand command) {
        List<TriageEncounterCommand.TriageEncounterItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, TriageEncounterCommand.TriageEncounterItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterParticipant> participants = new ArrayList<>();
        for (TriageEncounterCommand.TriageEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertTriagedFromPlanned();
            encounter.transitToTriaged();
            toSave.add(encounter);
            EncounterParticipant participant = new EncounterParticipant();
            participant.setEncounterId(encounter.getId());
            participant.setRoleCode(EncounterConstants.ROLE_TRIAGE_NURSE);
            participant.setPractitionerId(item.getTriageNurseId());
            participant.markActive(this.defaultNow(item.getTriageTime()));
            participants.add(participant);
        }
        encounterRepository.saveAll(toSave);
        participantRepository.saveAll(participants);
        for (TriageEncounterCommand.TriageEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            EncounterTriagedEvent triagedEvent = new EncounterTriagedEvent(item.getEncounterId(), item.getTriageNurseId(), item.getTriageLevelCode());
            triagedEvent.setStatusEnum(encounter.getStatusEnum());
            domainEventPublisher.publish(triagedEvent);
        }
    }

    /**
     * 门诊接诊。
     *
     * @param command 命令
     */
    public void startOutpatientReception(StartOutpatientReceptionCommand command) {
        List<StartOutpatientReceptionCommand.StartOutpatientReceptionItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, StartOutpatientReceptionCommand.StartOutpatientReceptionItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterParticipant> participantUpserts = new ArrayList<>();
        for (StartOutpatientReceptionCommand.StartOutpatientReceptionItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertReceptionAllowed();
            LocalDateTime receptionTime = this.defaultNow(item.getReceptionTime());
            encounter.transitToInProgress(receptionTime);
            toSave.add(encounter);

            // 同角色只能有一个活动参与者，先收口旧接诊医生。
            List<EncounterParticipant> activeDoctors = participantRepository
                .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                    encounter.getId(), EncounterConstants.ROLE_ADMITTER, EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterParticipant old : activeDoctors) {
                old.markCompleted(receptionTime);
                participantUpserts.add(old);
            }
            EncounterParticipant participant = new EncounterParticipant();
            participant.setEncounterId(encounter.getId());
            participant.setRoleCode(EncounterConstants.ROLE_ADMITTER);
            participant.setPractitionerId(item.getDoctorId());
            participant.markActive(receptionTime);
            participantUpserts.add(participant);
        }
        encounterRepository.saveAll(toSave);
        // 业务分支处理。
        if (!participantUpserts.isEmpty()) {
            participantRepository.saveAll(participantUpserts);
        }
        for (StartOutpatientReceptionCommand.StartOutpatientReceptionItemCommand item : items) {
            // 接诊事件须携带与事件字段一致的医师标识与展示名，供投影器反射读取，禁止误用历史 practitioner* 属性名。
            Encounter receptionEncounter = this.requireEncounter(encounterMap, item.getEncounterId());
            OutpatientReceptionStartedEvent receptionEvent =
                new OutpatientReceptionStartedEvent(item.getEncounterId(), item.getDoctorId(), item.getReceptionTime());
            receptionEvent.setDoctorName(item.getDoctorName());
            receptionEvent.setStatusEnum(receptionEncounter.getStatusEnum());
            domainEventPublisher.publish(receptionEvent);
        }
    }

    /**
     * 门诊暂离。
     *
     * @param command 命令
     */
    public void markOutpatientLeave(MarkOutpatientLeaveCommand command) {
        List<MarkOutpatientLeaveCommand.MarkOutpatientLeaveItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, MarkOutpatientLeaveCommand.MarkOutpatientLeaveItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        for (MarkOutpatientLeaveCommand.MarkOutpatientLeaveItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertCanMarkOnHold();
            encounter.transitToOnHold();
            toSave.add(encounter);
        }
        encounterRepository.saveAll(toSave);
        for (MarkOutpatientLeaveCommand.MarkOutpatientLeaveItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            OutpatientEncounterOnHoldEvent onHoldEvent = new OutpatientEncounterOnHoldEvent(item.getEncounterId(), item.getLeaveReason());
            onHoldEvent.setStatusEnum(encounter.getStatusEnum());
            domainEventPublisher.publish(onHoldEvent);
        }
    }

    /**
     * 门诊完诊。
     *
     * @param command 命令
     */
    public void completeOutpatientEncounter(CompleteOutpatientEncounterCommand command) {
        List<CompleteOutpatientEncounterCommand.CompleteOutpatientEncounterItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, CompleteOutpatientEncounterCommand.CompleteOutpatientEncounterItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        for (CompleteOutpatientEncounterCommand.CompleteOutpatientEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.AMB.getCode());
            encounter.assertCompletable();
            // BR-ENC-004：完诊前必须存在负责医生参与者。
            this.assertDoctorParticipantExists(encounter.getId());
            // BR-ENC-006：完诊前必须存在主诊断。
            this.assertPrimaryDiagnosisExists(encounter.getId());
            encounter.transitToDischarged(this.defaultNow(item.getCompletedAt()));
            toSave.add(encounter);
        }
        encounterRepository.saveAll(toSave);
        for (CompleteOutpatientEncounterCommand.CompleteOutpatientEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            OutpatientEncounterCompletedEvent completedEvent = new OutpatientEncounterCompletedEvent(
                encounter.getId(), encounter.getPatientId(), encounter.getEndTime());
            completedEvent.setStatusEnum(encounter.getStatusEnum());
            domainEventPublisher.publish(completedEvent);
        }
    }

    /**
     * 门诊挂号缴费确认：将就诊状态从 PLANNED 迁移至 REGISTERED。
     *
     * <p>仅适用于门诊（AMB）；由应用层在挂号费结算成功后编排调用。必须先完成聚合持久化，
     * 再发布 {@link OutpatientRegistrationConfirmedEvent}，供挂号列表、挂号单与门诊信息读模型按事件载荷刷新状态，
     * 与普通门诊收费结算路径解耦。</p>
     *
     * @param encounterId 就诊主键
     * @throws BusinessException 就诊不存在、类别不是门诊或当前状态不允许迁移时抛出
     */
    public void completeOutpatientRegistration(Long encounterId) {
        // 按主键加载门诊就诊聚合根，不存在则拒绝继续。
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
        // 仅门诊类型允许通过缴费确认迁移到已挂号状态。
        encounter.assertClass(EncounterClass.AMB.getCode());
        // 迁移到已挂号状态并持久化；聚合根内校验前置态须为 PLANNED（预结算待缴费）。
        encounter.transitToRegistered();
        encounterRepository.save(encounter);
        // 写库成功后发布挂号确认事件，载荷携带写模型当前状态码，供投影器物化（禁止先于 save 投递以防幽灵事件）。
        OutpatientRegistrationConfirmedEvent confirmedEvent =
            new OutpatientRegistrationConfirmedEvent(encounter.getId(), encounter.getPatientId());
        confirmedEvent.setStatusEnum(encounter.getStatusEnum());
        domainEventPublisher.publish(confirmedEvent);
    }

    /**
     * 取消门诊就诊（退号）。
     *
     * <p>持久化取消态后发布 {@link OutpatientRegistrationCancelledEvent}，事件中携带 {@code statusEnum} 与
     * {@code cancelledAt}，供门诊信息、挂号列表、挂号单及患者摘要等读模型更新展示状态。</p>
     *
     * @param command 批量取消命令（子项可带 {@code cancelAt}，缺省时退号时刻取当前时间）
     */
    public void cancelOutpatientRegistration(CancelOutpatientRegistrationCommand command) {
        List<CancelOutpatientRegistrationCommand.CancelOutpatientRegistrationItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, CancelOutpatientRegistrationCommand.CancelOutpatientRegistrationItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        for (CancelOutpatientRegistrationCommand.CancelOutpatientRegistrationItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.AMB.getCode());
            encounter.assertCancelable();
            encounter.transitToCancelled();
            toSave.add(encounter);
        }
        encounterRepository.saveAll(toSave);
        for (CancelOutpatientRegistrationCommand.CancelOutpatientRegistrationItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            // 就诊已持久化为取消态后再发布事件，载荷须带状态码与取消时刻，供门诊信息/患者侧读模型投影。
            OutpatientRegistrationCancelledEvent cancelledEvent = new OutpatientRegistrationCancelledEvent(
                encounter.getId(), encounter.getPatientId(), item.getCancelReason());
            cancelledEvent.setStatusEnum(encounter.getStatusEnum());
            LocalDateTime cancelledAt = item.getCancelAt();
            if (cancelledAt == null) {
                cancelledAt = LocalDateTime.now();
            }
            cancelledEvent.setCancelledAt(cancelledAt);
            domainEventPublisher.publish(cancelledEvent);
        }
    }

    /**
     * 急诊分级。
     *
     * @param command 命令
     */
    public void completeEmergencyTriage(CompleteEmergencyTriageCommand command) {
        List<CompleteEmergencyTriageCommand.CompleteEmergencyTriageItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, CompleteEmergencyTriageCommand.CompleteEmergencyTriageItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterParticipant> participants = new ArrayList<>();
        for (CompleteEmergencyTriageCommand.CompleteEmergencyTriageItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.EMER.getCode());
            encounter.assertTriagedFromPlanned();
            encounter.transitToTriaged();
            toSave.add(encounter);
            // 记录急诊分级护士参与者事实。
            EncounterParticipant participant = new EncounterParticipant();
            participant.setEncounterId(encounter.getId());
            participant.setRoleCode(EncounterConstants.ROLE_TRIAGE_NURSE);
            participant.setPractitionerId(item.getTriageNurseId());
            participant.markActive(this.defaultNow(item.getTriageTime()));
            participants.add(participant);
        }
        encounterRepository.saveAll(toSave);
        participantRepository.saveAll(participants);
        for (CompleteEmergencyTriageCommand.CompleteEmergencyTriageItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            EmergencyTriageCompletedEvent emergencyTriagedEvent = new EmergencyTriageCompletedEvent(
                item.getEncounterId(), item.getTriageNurseId(), item.getEmergencyLevel(), item.getTriageTime());
            emergencyTriagedEvent.setStatusEnum(encounter.getStatusEnum());
            domainEventPublisher.publish(emergencyTriagedEvent);
        }
    }

    /**
     * 急诊接诊。
     *
     * @param command 命令
     */
    public void startEmergencyReception(StartEmergencyReceptionCommand command) {
        List<StartEmergencyReceptionCommand.StartEmergencyReceptionItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, StartEmergencyReceptionCommand.StartEmergencyReceptionItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterParticipant> participantUpserts = new ArrayList<>();
        for (StartEmergencyReceptionCommand.StartEmergencyReceptionItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.EMER.getCode());
            encounter.assertReceptionAllowed();
            LocalDateTime receptionTime = this.defaultNow(item.getReceptionTime());
            encounter.transitToInProgress(receptionTime);
            toSave.add(encounter);
            // 同角色只能有一个活动参与者，先收口旧接诊医生。
            List<EncounterParticipant> activeDoctors = participantRepository
                .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                    encounter.getId(), EncounterConstants.ROLE_ADMITTER, EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterParticipant old : activeDoctors) {
                old.markCompleted(receptionTime);
                participantUpserts.add(old);
            }
            EncounterParticipant participant = new EncounterParticipant();
            participant.setEncounterId(encounter.getId());
            participant.setRoleCode(EncounterConstants.ROLE_ADMITTER);
            participant.setPractitionerId(item.getDoctorId());
            participant.markActive(receptionTime);
            participantUpserts.add(participant);
        }
        encounterRepository.saveAll(toSave);
        // 业务分支处理。
        if (!participantUpserts.isEmpty()) {
            participantRepository.saveAll(participantUpserts);
        }
        for (StartEmergencyReceptionCommand.StartEmergencyReceptionItemCommand item : items) {
            Encounter emergencyReceptionEncounter = this.requireEncounter(encounterMap, item.getEncounterId());
            EmergencyReceptionStartedEvent emergencyReceptionEvent = new EmergencyReceptionStartedEvent(
                item.getEncounterId(), item.getDoctorId(), item.getReceptionTime(), item.getRescueFlag());
            emergencyReceptionEvent.setDoctorName(item.getDoctorName());
            emergencyReceptionEvent.setStatusEnum(emergencyReceptionEncounter.getStatusEnum());
            domainEventPublisher.publish(emergencyReceptionEvent);
        }
    }

    /**
     * 急诊完诊。
     *
     * @param command 命令
     */
    public void completeEmergencyEncounter(CompleteEmergencyEncounterCommand command) {
        List<CompleteEmergencyEncounterCommand.CompleteEmergencyEncounterItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, CompleteEmergencyEncounterCommand.CompleteEmergencyEncounterItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        for (CompleteEmergencyEncounterCommand.CompleteEmergencyEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.EMER.getCode());
            encounter.assertCompletable();
            // BR-ENC-004：完诊前必须存在负责医生参与者。
            this.assertDoctorParticipantExists(encounter.getId());
            // BR-ENC-006：完诊前必须存在主诊断。
            this.assertPrimaryDiagnosisExists(encounter.getId());
            encounter.transitToDischarged(this.defaultNow(item.getCompletedAt()));
            toSave.add(encounter);
        }
        encounterRepository.saveAll(toSave);
        for (CompleteEmergencyEncounterCommand.CompleteEmergencyEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            EmergencyEncounterCompletedEvent emergencyCompletedEvent = new EmergencyEncounterCompletedEvent(
                encounter.getId(), encounter.getPatientId(), encounter.getEndTime(), item.getDestinationCode());
            emergencyCompletedEvent.setStatusEnum(encounter.getStatusEnum());
            domainEventPublisher.publish(emergencyCompletedEvent);
        }
    }

    /**
     * 维护就诊诊断关联（单条或少量 upsert，非医生站全量批次路径）。
     *
     * <p>步骤：集合规则校验 → 内存组装/更新 {@link EncounterDiagnosis} → {@code saveAll} →
     * 按就诊去重拼装诊断快照 → 逐条发布 {@link EncounterDiagnosisAssociationUpsertedEvent}（B.14：先落库后发事件）。</p>
     *
     * @param command 含就诊、病情事实及主诊/排序等字段
     * @throws BusinessException 诊断集合规则或主诊唯一性校验失败
     */
    public void upsertEncounterDiagnosisAssociation(UpsertEncounterDiagnosisAssociationCommand command) {
        List<UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand> items = command.resolveItems();
        this.assertDiagnosisCollectionRule(items);
        List<EncounterDiagnosis> toSave = new ArrayList<>();
        for (UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand item : items) {
            // 主诊断跨库唯一性：校验数据库已有记录不与本次冲突（BR-ENC-014）。
            if (Boolean.TRUE.equals(item.getPrimaryFlag())) {
                this.assertNoPrimaryDiagnosisConflict(item.getEncounterId(), item.getConditionId());
            }
            // 已有关联则覆盖写；否则新建行（不在此处软删未出现项——非全量语义）。
            EncounterDiagnosis diagnosis = diagnosisRepository
                .findByEncounterIdAndConditionId(item.getEncounterId(), item.getConditionId())
                .orElseGet(EncounterDiagnosis::new);
            diagnosis.setEncounterId(item.getEncounterId());
            diagnosis.setConditionId(item.getConditionId());
            diagnosis.setPrimaryFlag(item.getPrimaryFlag());
            diagnosis.setRank(item.getRank());
            diagnosis.setDescription(item.getDescription());
            diagnosis.setDiagTypeCode(item.getDiagTypeCode());
            diagnosis.setDiagCode(item.getDiagCode());
            diagnosis.setConditionCode(item.getConditionCode());
            diagnosis.setMedType(item.getMedType());
            diagnosis.setHighestDiagFlag(item.getHighestDiagFlag());
            diagnosis.setTcmSyndromeGroup(item.getTcmSyndromeGroup());
            diagnosis.setTcmFlag(item.getTcmFlag());
            toSave.add(diagnosis);
        }
        diagnosisRepository.saveAll(toSave);
        for (EncounterDiagnosis diagnosis : toSave) {
            EncounterDiagnosisAssociationUpsertedEvent event = new EncounterDiagnosisAssociationUpsertedEvent(
                diagnosis.getEncounterId(), diagnosis.getConditionId(), diagnosis.getPrimaryFlag(), diagnosis.getRank());
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 批量维护就诊诊断关联（医生站保存诊断后的主路径：全量批次 + 软删未再出现的旧关联）。
     *
     * <p>对每个批次子项：校验规则 → 将存量关联装入 Map → 合并本批活动病情 id → 未再出现的存量行软删 →
     * {@code saveAll} → 构建一次就诊快照 → 对本批每条活动诊断各发一条 upsert 事件（事件内嵌同一快照 JSON）。</p>
     *
     * @param command 含多条就诊子批次，每子批次带全量诊断项列表
     * @throws BusinessException 集合规则或主诊约束失败
     */
    public void batchUpsertEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand command) {
        List<BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand> items = command.resolveItems();
        for (BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand item : items) {
            this.assertDiagnosisCollectionRuleByBatchItem(item);
            List<EncounterDiagnosis> exists = diagnosisRepository.findAllByEncounterId(item.getEncounterId());
            Map<Long, EncounterDiagnosis> existsMap = new HashMap<>();
            for (EncounterDiagnosis old : exists) {
                existsMap.put(old.getConditionId(), old);
            }
            List<EncounterDiagnosis> toSave = new ArrayList<>();
            Set<Long> activeConditionIds = new HashSet<>();
            for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
                EncounterDiagnosis diagnosis = existsMap.get(diagnosisItem.getConditionId());
                // 本批次首见该病情事实：新建关联；否则在存量行上覆盖写展示与主诊等字段。
                if (diagnosis == null) {
                    diagnosis = new EncounterDiagnosis();
                }
                diagnosis.setEncounterId(item.getEncounterId());
                diagnosis.setConditionId(diagnosisItem.getConditionId());
                diagnosis.setPrimaryFlag(diagnosisItem.getPrimaryFlag());
                diagnosis.setRank(diagnosisItem.getRank());
                diagnosis.setDescription(diagnosisItem.getDescription());
                diagnosis.setDiagTypeCode(diagnosisItem.getDiagTypeCode());
                diagnosis.setDiagCode(diagnosisItem.getDiagCode());
                diagnosis.setConditionCode(diagnosisItem.getConditionCode());
                diagnosis.setMedType(diagnosisItem.getMedType());
                diagnosis.setHighestDiagFlag(diagnosisItem.getHighestDiagFlag());
                diagnosis.setTcmSyndromeGroup(diagnosisItem.getTcmSyndromeGroup());
                diagnosis.setTcmFlag(diagnosisItem.getTcmFlag());
                toSave.add(diagnosis);
                activeConditionIds.add(diagnosisItem.getConditionId());
            }
            for (EncounterDiagnosis old : exists) {
                // 全量批次未再出现的病情事实：软删关联行，表示医生已从本次就诊诊断列表移除。
                if (!activeConditionIds.contains(old.getConditionId())) {
                    old.setIsDeleted(Boolean.TRUE);
                    old.setDeletedAt(LocalDateTime.now());
                    toSave.add(old);
                }
            }
            diagnosisRepository.saveAll(toSave);
            for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
                EncounterDiagnosisAssociationUpsertedEvent event = new EncounterDiagnosisAssociationUpsertedEvent(
                    item.getEncounterId(), diagnosisItem.getConditionId(), diagnosisItem.getPrimaryFlag(), diagnosisItem.getRank());
                domainEventPublisher.publish(event);
            }
        }
    }

    /**
     * 移除就诊与指定病情事实之间的诊断关联（软删关联行并发布移除事件）。
     *
     * <p>顺序：加载关联 → 标记删除 → {@code saveAll} → 按就诊构建快照 →
     * 逐条 {@link EncounterDiagnosisAssociationRemovedEvent}（B.14）。</p>
     *
     * @param command 可含多条移除子项
     * @throws BusinessException 关联不存在
     */
    public void removeEncounterDiagnosisAssociation(RemoveEncounterDiagnosisAssociationCommand command) {
        List<RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand> items = command.resolveItems();
        List<EncounterDiagnosis> toSave = new ArrayList<>();
        for (RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item : items) {
            // 必须存在未删关联；幂等软删不在此路径表达，由应用层 del 接口吞并 NOT_FOUND。
            EncounterDiagnosis diagnosis = diagnosisRepository
                .findByEncounterIdAndConditionId(item.getEncounterId(), item.getConditionId())
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "诊断关联不存在"));
            diagnosis.setIsDeleted(Boolean.TRUE);
            diagnosis.setDeletedAt(LocalDateTime.now());
            toSave.add(diagnosis);
        }
        diagnosisRepository.saveAll(toSave);
        for (RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item : items) {
            EncounterDiagnosisAssociationRemovedEvent event = new EncounterDiagnosisAssociationRemovedEvent(
                item.getEncounterId(), item.getConditionId());
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 发起住院申请：INSERT 新 {@code IMP+PLANNED} 住院就诊；{@code ambEncounterId} 可空（无门诊锚点时）。
     *
     * @param command 命令
     * @return 首条新建住院就诊标识
     */
    public Long requestInpatientAdmission(RequestInpatientAdmissionCommand command) {
        List<RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand> items = command.resolveItems();
        Long operatorId = command.getOperatorId();
        List<Encounter> toSave = new ArrayList<>();
        List<String> inpatientBusNos = this.allocateInpatientBusNos(items.size());
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < items.size(); index++) {
            RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand item = items.get(index);
            // Pre：源门急诊就诊须存在且可转住院；同一 amb 锚点不得重复未取消住院线。
            if (item.getAmbEncounterId() != null) {
                this.assertAmbForInpatientWrite(item.getAmbEncounterId());
                this.assertNoOpenInpatientForAmb(item.getAmbEncounterId());
            }
            Encounter encounter = new Encounter();
            encounter.setPatientId(item.getPatientId());
            encounter.setOrganizationId(item.getTargetDepartmentId());
            encounter.setAdmitSourceCode(item.getAdmitSourceCode());
            encounter.setInWayCode(item.getInWayCode());
            encounter.setPriorityEnum(item.getPriorityCode());
            encounter.setAmbEncounterId(item.getAmbEncounterId());
            encounter.setBusNo(inpatientBusNos.get(index));
            encounter.initInpatientPlanned();
            toSave.add(encounter);
        }
        List<Encounter> saved = encounterRepository.saveAll(toSave);
        List<EncounterParticipant> participants = new ArrayList<>();
        List<EncounterLocation> locations = new ArrayList<>();
        for (int index = 0; index < saved.size(); index++) {
            Encounter encounter = saved.get(index);
            RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand item = items.get(index);
            if (item.getRequestDoctorId() != null) {
                EncounterParticipant participant = new EncounterParticipant();
                participant.setEncounterId(encounter.getId());
                participant.setRoleCode(EncounterConstants.ROLE_ADMITTER);
                participant.setPractitionerId(item.getRequestDoctorId());
                participant.markActive(now);
                participants.add(participant);
            }
            if (item.getWardId() != null) {
                EncounterLocation ward = new EncounterLocation();
                ward.setEncounterId(encounter.getId());
                ward.setLocationId(item.getWardId());
                ward.setFormEnum(LocationForm.WARD.getCode());
                ward.markActive(now);
                locations.add(ward);
            }
            this.saveAdmissionDx(encounter, item.getAdmissionDiagnoses(), operatorId);
        }
        if (!participants.isEmpty()) {
            participantRepository.saveAll(participants);
        }
        if (!locations.isEmpty()) {
            locationRepository.saveAll(locations);
        }
        for (int index = 0; index < saved.size(); index++) {
            Encounter encounter = saved.get(index);
            RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand item = items.get(index);
            InpatientAdmissionRequestedEvent event = new InpatientAdmissionRequestedEvent(
                encounter.getId(), encounter.getPatientId(), item.getTargetDepartmentId());
            event.setAmbEncounterId(item.getAmbEncounterId());
            event.setBusNo(encounter.getBusNo());
            event.setClassEnum(encounter.getClassEnum());
            event.setStatusEnum(encounter.getStatusEnum());
            event.setInWayCode(encounter.getInWayCode());
            event.setPriorityEnum(encounter.getPriorityEnum());
            event.setWardId(item.getWardId());
            domainEventPublisher.publish(event);
        }
        return saved.get(0).getId();
    }

    /**
     * 住院预入院。
     *
     * @param command 命令
     */
    public void startPreAdmission(StartPreAdmissionCommand command) {
        List<StartPreAdmissionCommand.StartPreAdmissionItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, StartPreAdmissionCommand.StartPreAdmissionItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        for (StartPreAdmissionCommand.StartPreAdmissionItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.IMP.getCode());
            encounter.assertStatus(EncounterStatus.PLANNED.getCode());
            encounter.setStatusEnum(EncounterStatus.PRE_ADMISSION.getCode());
            toSave.add(encounter);
        }
        encounterRepository.saveAll(toSave);
        for (StartPreAdmissionCommand.StartPreAdmissionItemCommand item : items) {
            domainEventPublisher.publish(new InpatientPreAdmissionStartedEvent(item.getEncounterId(), item.getPreAdmissionItems()));
        }
    }

    /**
     * 住院登记。
     *
     * @param command 命令
     */
    @Transactional
    public void registerInpatientEncounter(RegisterInpatientEncounterCommand command) {
        List<RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand> items = command.resolveItems();
        Long operatorId = command.getOperatorId();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterLocation> locationToSave = new ArrayList<>();
        for (RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.IMP.getCode());
            // 委托聚合根 assertInpatientRegistrationAllowed() 校验住院登记前置状态，遵守 §D3.2 硬门禁。
            encounter.assertInpatientRegistrationAllowed();
            LocalDateTime admitTime = this.defaultNow(item.getRegisterTime());
            encounter.transitToInpatientRegistered(admitTime);
            encounter.setRegistrarId(item.getRegistrarId());
            encounter.setOrganizationId(item.getDepartmentId());
            if (StringUtils.isNotBlank(item.getAdmitSourceCode())) {
                encounter.setAdmitSourceCode(item.getAdmitSourceCode());
            }
            if (StringUtils.isNotBlank(item.getInWayCode())) {
                encounter.setInWayCode(item.getInWayCode());
            }
            if (StringUtils.isNotBlank(item.getPriorityCode())) {
                encounter.setPriorityEnum(item.getPriorityCode());
            }
            toSave.add(encounter);
            if (item.getWardId() != null) {
                this.upsertActiveWard(encounter.getId(), item.getWardId(), admitTime, locationToSave);
            }
        }
        encounterRepository.saveAll(toSave);
        if (!locationToSave.isEmpty()) {
            locationRepository.saveAll(locationToSave);
        }
        for (RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            accountDomainService.openInpatientRegAccount(
                encounter.getPatientId(), encounter.getId(), item.getContractNo(), operatorId);
            this.applyPlannedRegistrationAdmissionDiagnosisPolicy(
                encounter, item.getAdmissionDiagnoses(), operatorId);
            this.publishInpatientAdmissionCompleted(encounter, item);
        }
    }

    /**
     * 无申请直登登记：INSERT {@code IMP+REGISTERED} 并开户，不经过 {@code PLANNED}。
     *
     * @param command 命令（{@link RegisterInpatientEncounterCommand#getPatientId()} 必填）
     * @return 新建住院就诊标识
     */
    @Transactional
    public Long registerDirectInpatientEncounter(RegisterInpatientEncounterCommand command) {
        if (command.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "直登登记缺少患者标识");
        }
        List<RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand> items = command.resolveItems();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "直登登记缺少登记项");
        }
        RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item = items.get(0);
        if (item.getEncounterId() != null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "直登登记不应携带既有就诊标识");
        }
        Long operatorId = command.getOperatorId();
        LocalDateTime admitTime = this.defaultNow(item.getRegisterTime());
        Encounter encounter = new Encounter();
        encounter.setPatientId(command.getPatientId());
        encounter.setOrganizationId(item.getDepartmentId());
        encounter.setBusNo(this.allocateInpatientBusNos(1).get(0));
        encounter.initInpatientRegistered();
        encounter.setStartTime(admitTime);
        encounter.setRegistrarId(item.getRegistrarId());
        if (StringUtils.isNotBlank(item.getAdmitSourceCode())) {
            encounter.setAdmitSourceCode(item.getAdmitSourceCode());
        }
        if (StringUtils.isNotBlank(item.getInWayCode())) {
            encounter.setInWayCode(item.getInWayCode());
        }
        if (StringUtils.isNotBlank(item.getPriorityCode())) {
            encounter.setPriorityEnum(item.getPriorityCode());
        }
        Encounter saved = encounterRepository.save(encounter);
        if (item.getWardId() != null) {
            EncounterLocation ward = new EncounterLocation();
            ward.setEncounterId(saved.getId());
            ward.setLocationId(item.getWardId());
            ward.setFormEnum(LocationForm.WARD.getCode());
            ward.markActive(admitTime);
            locationRepository.save(ward);
        }
        accountDomainService.openInpatientRegAccount(
            saved.getPatientId(), saved.getId(), item.getContractNo(), operatorId);
        this.assertDirectRegistrationAdmissionDiagnosesPresent(item.getAdmissionDiagnoses());
        this.saveAdmissionDx(saved, item.getAdmissionDiagnoses(), operatorId);
        this.publishInpatientAdmissionCompleted(saved, item);
        return saved.getId();
    }

    /**
     * 发布住院登记完成事件，载荷与读模型 §4.2 对齐。
     *
     * <p>经 {@code DomainEventPublisher} 在写事务 {@code beforeCommit} 同步投递。</p>
     *
     * @param encounter 已落库的住院就诊
     * @param item 登记项（含意向病区 {@code wardId}）
     */
    private void publishInpatientAdmissionCompleted(
        Encounter encounter,
        RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item) {
        InpatientAdmissionCompletedEvent event = new InpatientAdmissionCompletedEvent(
            encounter.getId(),
            encounter.getPatientId(),
            item.getDepartmentId(),
            item.getRegistrarId());
        event.setStatusEnum(encounter.getStatusEnum());
        event.setStartTime(encounter.getStartTime());
        event.setBusNo(encounter.getBusNo());
        event.setWardId(item.getWardId());
        domainEventPublisher.publish(event);
    }

    /**
     * 住院分床（DM-ADM-002 §6.1.17）：待入科 → 已入科，写入病房+床位位置与可选参与者。
     *
     * <p>位置：保留活动 {@code WARD}，仅收口 {@code HOUSE}/{@code BED} 后 INSERT 新活动行。
     * 参与者：提交列表时收口全部活动行后 INSERT（禁止原地 UPDATE）。
     * 持久化顺序：主档与位置/参与者落库后，再发布 {@link InpatientBedAssignedEvent}（B.14）。</p>
     *
     * @param command 分床命令，含目标床、病情与责任医护（可选）；入科/轨迹时刻取 {@link LocalDateTime#now()}
     */
    public void assignInpatientBed(AssignInpatientBedCommand command) {
        List<AssignInpatientBedCommand.AssignInpatientBedItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, AssignInpatientBedCommand.AssignInpatientBedItemCommand::getEncounterId));
        List<EncounterLocation> locationToSave = new ArrayList<>();
        List<EncounterParticipant> participantToSave = new ArrayList<>();
        for (AssignInpatientBedCommand.AssignInpatientBedItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            // Pre：仅待入科（REGISTERED）允许首次分床。
            encounter.assertInpatientBedAssignmentAllowed();
            LocalDateTime assignAt = LocalDateTime.now();
            if (StringUtils.isNotBlank(item.getPriorityEnum())) {
                encounter.setPriorityEnum(item.getPriorityEnum().trim());
            }
            List<AssignInpatientBedCommand.ParticipantLine> participantItems = item.getParticipants();
            if (participantItems != null && !participantItems.isEmpty()) {
                this.completeAllActiveParticipants(item.getEncounterId(), assignAt, participantToSave);
                this.appendParticipantsFromAssignLines(item.getEncounterId(), assignAt, participantItems, participantToSave);
            }
            if (item.getHouseId() == null || item.getBedId() == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "分床须指定病房与床位");
            }
            this.completeActiveHouseAndBedLocations(item.getEncounterId(), assignAt, locationToSave);
            this.appendActiveLocation(item.getEncounterId(), item.getHouseId(), LocationForm.HOUSE.getCode(),
                assignAt, locationToSave);
            String bedFormEnum = item.getFormEnum();
            if (StringUtils.isBlank(bedFormEnum)) {
                bedFormEnum = LocationForm.BED.getCode();
            }
            this.appendActiveLocation(item.getEncounterId(), item.getBedId(), bedFormEnum, assignAt, locationToSave);
            encounter.transitToInProgress(assignAt);
        }
        encounterRepository.saveAll(encounterMap.values());
        if (!locationToSave.isEmpty()) {
            locationRepository.saveAll(locationToSave);
        }
        if (!participantToSave.isEmpty()) {
            participantRepository.saveAll(participantToSave);
        }
        for (AssignInpatientBedCommand.AssignInpatientBedItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            InpatientBedAssignedEvent event = new InpatientBedAssignedEvent(item.getEncounterId(), item.getBedId(), item.getBedCode());
            event.setPatientId(encounter.getPatientId());
            event.setAdmissionTime(encounter.getStartTime());
            event.setStatusEnum(encounter.getStatusEnum());
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 确认转床（DM-ADM-002 §6.1.18）：已入科患者迁至目标床，状态保持 {@code IN_PROGRESS}。
     *
     * @param command 转床命令，{@code fromBedId} 须与当前活动床一致
     */
    public void confirmBedTransfer(ConfirmBedTransferCommand command) {
        List<ConfirmBedTransferCommand.ConfirmBedTransferItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, ConfirmBedTransferCommand.ConfirmBedTransferItemCommand::getEncounterId));
        List<EncounterLocation> toSave = new ArrayList<>();
        List<BedTransferredEvent> events = new ArrayList<>();
        for (ConfirmBedTransferCommand.ConfirmBedTransferItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            // Pre：仅已入科在院允许换床。
            encounter.assertInpatientBedTransferAllowed();
            LocalDateTime effectiveAt = LocalDateTime.now();
            Long activeBedId = this.requireActiveBedId(item.getEncounterId());
            if (!activeBedId.equals(item.getFromBedId())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                    "原床位与当前活动床位不一致");
            }
            if (item.getToHouseId() == null || item.getToBedId() == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "转床须指定目标病房与床位");
            }
            this.completeActiveHouseAndBedLocations(item.getEncounterId(), effectiveAt, toSave);
            this.appendActiveLocation(item.getEncounterId(), item.getToHouseId(), LocationForm.HOUSE.getCode(),
                effectiveAt, toSave);
            String toBedFormEnum = item.getToFormEnum();
            if (StringUtils.isBlank(toBedFormEnum)) {
                toBedFormEnum = LocationForm.BED.getCode();
            }
            this.appendActiveLocation(item.getEncounterId(), item.getToBedId(), toBedFormEnum, effectiveAt, toSave);
            events.add(new BedTransferredEvent(
                item.getEncounterId(), item.getFromBedId(), item.getToBedId(), effectiveAt));
        }
        if (!toSave.isEmpty()) {
            locationRepository.saveAll(toSave);
        }
        for (BedTransferredEvent event : events) {
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 互换床（DM-ADM-002 §6.1.18a）：两名在院患者对调活动床位位置。
     *
     * <p>双方保持 {@code IN_PROGRESS}；各发布一条 {@link BedTransferredEvent}。
     * Location 床态对调由应用层编排。</p>
     *
     * @param command 互换命令，通常 {@code items} 仅含一条 A/B 就诊对
     */
    public void swapInpatientBeds(SwapInpatientBedsCommand command) {
        List<SwapInpatientBedsCommand.SwapInpatientBedsItemCommand> items = command.resolveItems();
        for (SwapInpatientBedsCommand.SwapInpatientBedsItemCommand item : items) {
            List<EncounterLocation> toSave = new ArrayList<>();
            if (item.getEncounterIdA() == null || item.getEncounterIdB() == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "互换双方就诊不能为空");
            }
            if (item.getEncounterIdA().equals(item.getEncounterIdB())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "不能与自己互换床位");
            }
            Map<Long, Encounter> encounterMap = this.loadEncounterMap(
                List.of(item.getEncounterIdA(), item.getEncounterIdB()));
            Encounter encounterA = this.requireEncounter(encounterMap, item.getEncounterIdA());
            Encounter encounterB = this.requireEncounter(encounterMap, item.getEncounterIdB());
            encounterA.assertInpatientBedTransferAllowed();
            encounterB.assertInpatientBedTransferAllowed();
            LocalDateTime effectiveAt = LocalDateTime.now();
            Long bedIdA = this.requireActiveBedId(item.getEncounterIdA());
            Long bedIdB = this.requireActiveBedId(item.getEncounterIdB());
            if (item.getToHouseIdA() == null || item.getToHouseIdB() == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "互换须指定双方目标病房");
            }
            this.completeActiveHouseAndBedLocations(item.getEncounterIdA(), effectiveAt, toSave);
            this.completeActiveHouseAndBedLocations(item.getEncounterIdB(), effectiveAt, toSave);
            this.appendActiveLocation(item.getEncounterIdA(), item.getToHouseIdA(), LocationForm.HOUSE.getCode(),
                effectiveAt, toSave);
            this.appendActiveLocation(item.getEncounterIdA(), bedIdB, LocationForm.BED.getCode(), effectiveAt, toSave);
            this.appendActiveLocation(item.getEncounterIdB(), item.getToHouseIdB(), LocationForm.HOUSE.getCode(),
                effectiveAt, toSave);
            this.appendActiveLocation(item.getEncounterIdB(), bedIdA, LocationForm.BED.getCode(), effectiveAt, toSave);
            if (!toSave.isEmpty()) {
                locationRepository.saveAll(toSave);
            }
            // Event：双方各一条转床事件，消费方与单患者 confirmBedTransfer 相同。
            domainEventPublisher.publish(new BedTransferredEvent(
                item.getEncounterIdA(), bedIdA, bedIdB, effectiveAt));
            domainEventPublisher.publish(new BedTransferredEvent(
                item.getEncounterIdB(), bedIdB, bedIdA, effectiveAt));
        }
    }

    /**
     * 发起转科。
     *
     * @param command 命令
     */
    public void initiateDepartmentTransfer(InitiateDepartmentTransferCommand command) {
        List<InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterParticipant> participantUpdates = new ArrayList<>();
        for (InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.IMP.getCode());
            encounter.assertStatus(EncounterStatus.IN_PROGRESS.getCode());
            encounter.transitToOnHold();
            // organizationId 保持为转出科室，待 confirmDepartmentTransfer 时再更新为目标科室。
            toSave.add(encounter);
            // 关闭旧责任关系：收口当前全部活动参与者。
            LocalDateTime initiatedAt = this.defaultNow(item.getInitiatedAt());
            List<EncounterParticipant> active = participantRepository
                .findAllByEncounterIdAndStatusEnum(encounter.getId(), EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterParticipant old : active) {
                old.markCompleted(initiatedAt);
                participantUpdates.add(old);
            }
        }
        encounterRepository.saveAll(toSave);
        // 业务分支处理。
        if (!participantUpdates.isEmpty()) {
            participantRepository.saveAll(participantUpdates);
        }
        for (InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand item : items) {
            domainEventPublisher.publish(new DepartmentTransferInitiatedEvent(
                item.getEncounterId(), item.getFromDepartmentId(), item.getToDepartmentId()));
        }
    }

    /**
     * 确认转科。
     *
     * @param command 命令
     */
    public void confirmDepartmentTransfer(ConfirmDepartmentTransferCommand command) {
        List<ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterLocation> locationUpdates = new ArrayList<>();
        for (ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.IMP.getCode());
            encounter.assertStatus(EncounterStatus.ON_HOLD.getCode());
            LocalDateTime effectiveAt = this.defaultNow(item.getEffectiveAt());
            encounter.setStatusEnum(EncounterStatus.REGISTERED.getCode());
            // 确认转科时更新科室归属为目标科室。
            encounter.setOrganizationId(item.getTargetDepartmentId());
            toSave.add(encounter);
            // 关闭旧位置记录并激活目标科室位置（SDD §5.1.19）。
            List<EncounterLocation> activeLocations = locationRepository
                .findAllByEncounterIdAndStatusEnum(encounter.getId(), EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterLocation old : activeLocations) {
                old.markCompleted(effectiveAt);
                locationUpdates.add(old);
            }
            EncounterLocation newLocation = new EncounterLocation();
            newLocation.setEncounterId(encounter.getId());
            newLocation.setLocationId(item.getTargetDepartmentId());
            newLocation.markActive(effectiveAt);
            locationUpdates.add(newLocation);
        }
        encounterRepository.saveAll(toSave);
        // 业务分支处理。
        if (!locationUpdates.isEmpty()) {
            locationRepository.saveAll(locationUpdates);
        }
        for (ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand item : items) {
            domainEventPublisher.publish(new DepartmentTransferConfirmedEvent(
                item.getEncounterId(), item.getTargetDepartmentId(), this.defaultNow(item.getEffectiveAt())));
        }
    }

    /**
     * 住院出院医嘱开立（在科 → 待出院，{@code IN_PROGRESS} → {@code ON_HOLD}）。
     *
     * @param command 命令
     */
    public void dischargeInpatientEncounter(DischargeInpatientEncounterCommand command) {
        List<DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        for (DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.IMP.getCode());
            encounter.assertStatus(EncounterStatus.IN_PROGRESS.getCode());
            // BR-ENC-004：出院前必须存在负责医生参与者。
            this.assertDoctorParticipantExists(encounter.getId());
            // BR-ENC-006：出院前必须存在主诊断。
            this.assertPrimaryDiagnosisExists(encounter.getId());
            encounter.transitToOnHold();
            encounter.setOutWayCode(item.getOutWayCode());
            toSave.add(encounter);
        }
        encounterRepository.saveAll(toSave);
        for (DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            HospitalDischargedEvent event = new HospitalDischargedEvent(
                encounter.getId(), encounter.getPatientId(), encounter.getOutWayCode(), null);
            event.setStatusEnum(encounter.getStatusEnum());
            event.setDischargeTypeEnum(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 护士站办理出院：待出院 → 待出院结算（{@code ON_HOLD} → {@code PENDING}，DM-ADM-002 §6.1.22）。
     *
     * <p>前置由应用层保证为「待出院」分流（非待转科）；本方法仅校验 {@code IMP+ON_HOLD}。
     * 已为 {@code PENDING} 时幂等跳过。</p>
     *
     * @param command 命令
     */
    public void executeInpatientNurseDischarge(ExecuteInpatientNurseDischargeCommand command) {
        List<ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand> items =
            command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(
                items,
                ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        for (ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.IMP.getCode());
            if (EncounterStatus.PENDING.getCode().equals(encounter.getStatusEnum())) {
                continue;
            }
            if (!EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "仅待出院状态的住院就诊可办理出院");
            }
            LocalDateTime dischargedAt = this.defaultNow(item.getDischargedAt());
            encounter.transitToPending();
            encounter.setEndTime(dischargedAt);
            toSave.add(encounter);
        }
        if (!toSave.isEmpty()) {
            encounterRepository.saveAll(toSave);
            for (Encounter encounter : toSave) {
                HospitalDischargedEvent event = new HospitalDischargedEvent(
                    encounter.getId(),
                    encounter.getPatientId(),
                    encounter.getOutWayCode(),
                    encounter.getEndTime());
                event.setStatusEnum(encounter.getStatusEnum());
                domainEventPublisher.publish(event);
            }
        }
    }

    /**
     * 住院出院财务全结：待结算 → 已结算（DM-ADM-002 §6.1.23，AS-IP-FIN-003 BR-IP-STL-003）。
     *
     * <p>仅适用于 {@code IMP}；前置态须为 {@code PENDING}。中途结算不得调用本动作。</p>
     *
     * @param command 命令
     */
    public void completeInpatientFinancialSettlement(CompleteInpatientFinancialSettlementCommand command) {
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "住院就诊主键不能为空");
        }
        Encounter encounter = encounterRepository.findById(command.getEncounterId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
        encounter.assertClass(EncounterClass.IMP.getCode());
        if (!EncounterStatus.PENDING.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅待结算状态的住院就诊可办理出院财务全结");
        }
        encounter.transitToCompleted();
        encounterRepository.save(encounter);
        InpatientFinancialSettlementCompletedEvent event = new InpatientFinancialSettlementCompletedEvent(
            encounter.getId(), encounter.getPatientId());
        event.setStatusEnum(encounter.getStatusEnum());
        LocalDateTime settlementTime = command.getSettlementTime();
        if (settlementTime == null) {
            settlementTime = LocalDateTime.now();
        }
        event.setSettlementTime(settlementTime);
        domainEventPublisher.publish(event);
    }

    /**
     * 清床。
     *
     * @param command 命令
     */
    public void clearDischargedBed(ClearDischargedBedCommand command) {
        List<ClearDischargedBedCommand.ClearDischargedBedItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, ClearDischargedBedCommand.ClearDischargedBedItemCommand::getEncounterId));
        List<EncounterLocation> toSave = new ArrayList<>();
        List<EncounterParticipant> participants = new ArrayList<>();
        List<Encounter> encountersToSave = new ArrayList<>();
        for (ClearDischargedBedCommand.ClearDischargedBedItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClearBedAllowed();
            LocalDateTime clearedAt = this.defaultNow(item.getClearedAt());
            List<EncounterLocation> activeLocations = locationRepository
                .findAllByEncounterIdAndStatusEnum(item.getEncounterId(), EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterLocation old : activeLocations) {
                old.markCompleted(clearedAt);
                toSave.add(old);
            }
            List<EncounterParticipant> activeParticipants = participantRepository
                .findAllByEncounterIdAndStatusEnum(item.getEncounterId(), EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterParticipant old : activeParticipants) {
                old.markCompleted(clearedAt);
                participants.add(old);
            }
            encounter.transitToDischarged(clearedAt);
            encountersToSave.add(encounter);
        }
        if (!toSave.isEmpty()) {
            locationRepository.saveAll(toSave);
        }
        if (!participants.isEmpty()) {
            participantRepository.saveAll(participants);
        }
        if (!encountersToSave.isEmpty()) {
            encounterRepository.saveAll(encountersToSave);
        }
        for (ClearDischargedBedCommand.ClearDischargedBedItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            HospitalDischargedEvent dischargedEvent = new HospitalDischargedEvent(
                encounter.getId(), encounter.getPatientId(), encounter.getOutWayCode(), encounter.getEndTime());
            dischargedEvent.setStatusEnum(encounter.getStatusEnum());
            domainEventPublisher.publish(dischargedEvent);
            domainEventPublisher.publish(new TerminalCleaningCompletedEvent(
                item.getEncounterId(), item.getBedId(), this.defaultNow(item.getClearedAt())));
        }
    }

    /**
     * 住院护士站保存患者信息（AS-IP-NUR-001 IF-IP-NUR-04）：更新 {@code priority_enum}；
     * 对提交各 {@code roleCode} 活动参与者 COMPLETED 后 INSERT 新 ACTIVE（禁止 UPDATE）；禁止改床与 {@code status_enum}。
     *
     * @param command 保存命令
     * @return 首条就诊标识
     */
    @Transactional
    public Long saveNurseStationPatient(SaveNurseStationPatientCommand command) {
        List<SaveNurseStationPatientCommand.ItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, SaveNurseStationPatientCommand.ItemCommand::getEncounterId));
        List<Encounter> encountersToSave = new ArrayList<>();
        List<EncounterParticipant> participantsToSave = new ArrayList<>();
        Map<Long, LocalDateTime> savedAtByEncounterId = new HashMap<>();
        for (SaveNurseStationPatientCommand.ItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            this.assertNurseStationPatientSavable(encounter);
            LocalDateTime savedAt = LocalDateTime.now();
            savedAtByEncounterId.put(item.getEncounterId(), savedAt);
            boolean encounterDirty = false;
            if (StringUtils.isNotBlank(item.getPriorityEnum())
                && !StringUtils.equals(item.getPriorityEnum(), encounter.getPriorityEnum())) {
                encounter.setPriorityEnum(item.getPriorityEnum().trim());
                encounterDirty = true;
            }
            if (encounterDirty) {
                encountersToSave.add(encounter);
            }
            List<SaveNurseStationPatientCommand.ParticipantLine> participantItems = item.getParticipants();
            if (participantItems != null && !participantItems.isEmpty()) {
                Set<String> roleCodes = new HashSet<>();
                for (SaveNurseStationPatientCommand.ParticipantLine row : participantItems) {
                    if (row == null || StringUtils.isBlank(row.getRoleCode()) || row.getPractitionerId() == null) {
                        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                            "参与者角色与医务人员不能为空");
                    }
                    if (!roleCodes.add(row.getRoleCode().trim())) {
                        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                            "同一保存请求中参与者角色不能重复");
                    }
                }
                for (SaveNurseStationPatientCommand.ParticipantLine row : participantItems) {
                    String roleCode = row.getRoleCode().trim();
                    List<EncounterParticipant> activeForRole = participantRepository
                        .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                            encounter.getId(), roleCode, EncounterActivityStatus.ACTIVE.getCode());
                    for (EncounterParticipant old : activeForRole) {
                        old.markCompleted(savedAt);
                        participantsToSave.add(old);
                    }
                    EncounterParticipant participant = new EncounterParticipant();
                    participant.setEncounterId(encounter.getId());
                    participant.setRoleCode(roleCode);
                    participant.setPractitionerId(row.getPractitionerId());
                    participant.markActive(savedAt);
                    participantsToSave.add(participant);
                }
            }
        }
        if (!encountersToSave.isEmpty()) {
            encounterRepository.saveAll(encountersToSave);
        }
        if (!participantsToSave.isEmpty()) {
            participantRepository.saveAll(participantsToSave);
        }
        Long operatorId = command.getOperatorId();
        for (SaveNurseStationPatientCommand.ItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            NurseStationPatientSavedEvent event =
                new NurseStationPatientSavedEvent(encounter.getId(), encounter.getPatientId());
            event.setPriorityEnum(encounter.getPriorityEnum());
            event.setStatusEnum(encounter.getStatusEnum());
            event.setSavedAt(savedAtByEncounterId.get(encounter.getId()));
            event.setOperatorId(operatorId);
            domainEventPublisher.publish(event);
        }
        return items.get(0).getEncounterId();
    }

    /**
     * 取消住院登记（DM-ADM-002 §6.1.23）。
     *
     * <p>持久化取消态后发布 {@link InpatientAdmissionCancelledEvent}，事件中携带 {@code statusEnum} 与
     * {@code cancelledTime}，供住院信息读模型更新取消态并从登记待办列表消失。</p>
     *
     * @param command 取消命令
     */
    public void cancelInpatientAdmission(CancelInpatientAdmissionCommand command) {
        List<CancelInpatientAdmissionCommand.CancelInpatientAdmissionItemCommand> items = command.resolveItems();
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, CancelInpatientAdmissionCommand.CancelInpatientAdmissionItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        for (CancelInpatientAdmissionCommand.CancelInpatientAdmissionItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            encounter.assertClass(EncounterClass.IMP.getCode());
            boolean hasAssignedBed = this.hasActiveBed(item.getEncounterId());
            encounter.assertInpatientAdmissionCancelable(hasAssignedBed);
            // 预交金余额须为 0，由应用层传入快照；领域侧二次校验防绕过。
            if (item.getAccountBalance() != null && item.getAccountBalance() > 0L) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "预交金余额不为0，不能取消住院登记");
            }
            encounter.transitToCancelled();
            encounter.setAmbEncounterId(null);
            toSave.add(encounter);
        }
        encounterRepository.saveAll(toSave);
        for (CancelInpatientAdmissionCommand.CancelInpatientAdmissionItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            // 就诊已持久化为取消态后再发布事件，载荷须带状态码与取消时刻，供 InpatientInfo 投影。
            InpatientAdmissionCancelledEvent cancelledEvent = new InpatientAdmissionCancelledEvent(
                encounter.getId(), encounter.getPatientId(), item.getCancelReason());
            cancelledEvent.setStatusEnum(encounter.getStatusEnum());
            LocalDateTime cancelledTime = item.getCancelAt();
            if (cancelledTime == null) {
                cancelledTime = LocalDateTime.now();
            }
            cancelledEvent.setCancelledTime(cancelledTime);
            domainEventPublisher.publish(cancelledEvent);
        }
    }

    /**
     * 断言同日、同目标科室门诊防重：凡已产生有效就诊事实（含诊毕）均占名额，除非 {@link EncounterStatus#CANCELLED} 退号。
     *
     * <p>门诊类别限定为 {@link EncounterClass#AMB}/{@link EncounterClass#EMER}；{@link EncounterStatus#PLANNED} 未交费不占名额。</p>
     *
     * @param registrationDay 本次开立所属自然日（与 occurredAt 对齐）
     */
    private void assertNoOutpatientDuplicate(Long patientId, Long organizationId, LocalDate registrationDay) {
        LocalDate day;
        if (registrationDay != null) {
            day = registrationDay;
        } else {
            // 未传登记日时按服务器当日自然日防重。
            day = LocalDate.now();
        }
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEndExclusive = day.plusDays(1).atStartOfDay();
        // 已交费或诊毕等有效事实占名额；仅 CANCELLED 退号后释放。
        List<String> blockingStatuses = Arrays.asList(
            EncounterStatus.REGISTERED.getCode(),
            EncounterStatus.TRIAGED.getCode(),
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode(),
            EncounterStatus.DISCHARGED.getCode(),
            EncounterStatus.COMPLETED.getCode()
        );
        List<String> outpatientClasses = Arrays.asList(EncounterClass.AMB.getCode(), EncounterClass.EMER.getCode());
        // 读侧存在性校验：同日同患者同科室已存在阻塞态门诊/急诊就诊则拒绝再挂。
        if (encounterQueryService.existsPaidOutpatientEncounterForDepartmentOnCalendarDay(
            patientId,
            organizationId,
            outpatientClasses,
            blockingStatuses,
            dayStart,
            dayEndExclusive)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                "本科室当日已有门诊挂号记录（含诊毕），须退号后方可再挂");
        }
    }

    /**
     * 判断住院就诊是否已存在主诊断关联（入院/登记防重复写入用）。
     *
     * @param encounterId 住院就诊 ID
     * @return 已存在主诊断时为 true
     */
    private boolean encounterHasPrimaryAdmissionDiagnosis(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        return diagnosisRepository.findFirstByEncounterIdAndPrimaryFlagTrue(encounterId).isPresent();
    }

    /**
     * 入院/登记按目录提交时：若就诊已有主诊断且病情定义与本次目录一致，复用已有 conditionId。
     *
     * @param encounterId 住院就诊 ID
     * @param conditionDefinitionId 诊断目录 ID
     * @return 可复用的病情 ID；无匹配主诊时 null
     */
    private Long resolveExistingConditionIdForAdmissionDefinition(
        Long encounterId, Long conditionDefinitionId) {
        if (encounterId == null || conditionDefinitionId == null) {
            return null;
        }
        Optional<EncounterDiagnosis> primaryOpt =
            diagnosisRepository.findFirstByEncounterIdAndPrimaryFlagTrue(encounterId);
        if (primaryOpt.isEmpty()) {
            return null;
        }
        Long linkedConditionId = primaryOpt.get().getConditionId();
        if (linkedConditionId == null) {
            return null;
        }
        Optional<Condition> conditionOpt = conditionRepository.findById(linkedConditionId);
        if (conditionOpt.isEmpty()) {
            return null;
        }
        Condition condition = conditionOpt.get();
        if (Boolean.TRUE.equals(condition.getIsDeleted())) {
            return null;
        }
        if (!conditionDefinitionId.equals(condition.getDefinitionId())) {
            return null;
        }
        return condition.getId();
    }

    /**
     * 断言设置主诊断时不与已有其他主诊断冲突（BR-ENC-014，跨库检查）。
     * 若数据库已存在主诊断且 conditionId 与本次不同，则拒绝。
     *
     * @param encounterId 就诊 ID
     * @param conditionId 本次拟关联的病情 ID
     */
    private void assertNoPrimaryDiagnosisConflict(Long encounterId, Long conditionId) {
        Optional<EncounterDiagnosis> existingOpt =
            diagnosisRepository.findFirstByEncounterIdAndPrimaryFlagTrue(encounterId);
        if (existingOpt.isEmpty()) {
            return;
        }
        EncounterDiagnosis existing = existingOpt.get();
        if (!Objects.equals(existing.getConditionId(), conditionId)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                "该就诊已存在主诊断（conditionId=" + existing.getConditionId() + "），无法重复设置");
        }
    }

    /**
     * 有申请登记（{@code PLANNED→REGISTERED}）时的入院诊断策略：申请已落库则禁止登记侧再提交列表。
     *
     * @param encounter 住院就诊
     * @param admissionDxItems 登记命令携带的入院诊断（应为空）
     * @param operatorId 操作人
     */
    private void applyPlannedRegistrationAdmissionDiagnosisPolicy(
        Encounter encounter,
        List<AdmissionConditionItem> admissionDxItems,
        Long operatorId) {
        if (admissionDxItems == null || admissionDxItems.isEmpty()) {
            return;
        }
        if (this.encounterHasPrimaryAdmissionDiagnosis(encounter.getId())) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                "入院诊断已在住院申请时确定，登记不可修改");
        }
        // 边界：申请未落主诊但登记提交了诊断列表时，仍允许登记侧补落（与申请 Cmd 对称）。
        this.saveAdmissionDx(encounter, admissionDxItems, operatorId);
    }

    /**
     * 直登登记前置：入院诊断列表非空且至少一条主诊断。
     *
     * @param admissionDxItems 入院诊断明细
     */
    private void assertDirectRegistrationAdmissionDiagnosesPresent(List<AdmissionConditionItem> admissionDxItems) {
        if (admissionDxItems == null || admissionDxItems.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "直登登记须填写入院诊断");
        }
        boolean hasPrimary = false;
        for (AdmissionConditionItem row : admissionDxItems) {
            if (row == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "入院诊断行不能为空");
            }
            if (row.getPrimaryFlag() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "是否主诊断不能为空");
            }
            if (Boolean.TRUE.equals(row.getPrimaryFlag())) {
                hasPrimary = true;
            }
            if (row.getConditionId() == null && row.getConditionDefinitionId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "入院诊断须指定病情或诊断目录");
            }
        }
        if (!hasPrimary) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "入院诊断须至少一条主诊断");
        }
    }

    /**
     * 断言主诊断关联存在（BR-ENC-006）。
     */
    private void assertPrimaryDiagnosisExists(Long encounterId) {
        boolean exists = diagnosisRepository
            .findFirstByEncounterIdAndPrimaryFlagTrue(encounterId).isPresent();
        // 业务分支处理。
        if (!exists) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "完诊/出院前必须存在主诊断");
        }
    }

    /**
     * 断言负责医生参与者存在。
     */
    private void assertDoctorParticipantExists(Long encounterId) {
        List<EncounterParticipant> activeDoctors = participantRepository
            .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                encounterId, EncounterConstants.ROLE_ADMITTER, EncounterActivityStatus.ACTIVE.getCode());
        // 业务分支处理。
        if (activeDoctors.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "完诊前必须存在负责医生参与者");
        }
    }

    /**
     * 校验诊断集合级约束：主诊唯一、排序唯一。
     */
    private void assertDiagnosisCollectionRule(List<UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand> items) {
        Map<Long, Integer> primaryCountMap = new HashMap<>();
        Map<String, Boolean> rankMap = new HashMap<>();
        for (UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand item : items) {
            // 业务分支处理。
            if (Boolean.TRUE.equals(item.getPrimaryFlag())) {
                Integer count = primaryCountMap.getOrDefault(item.getEncounterId(), 0);
                primaryCountMap.put(item.getEncounterId(), count + 1);
            }
            // 业务分支处理。
            if (item.getRank() != null) {
                String key = item.getEncounterId() + "\u0001" + item.getRank();
                // 业务分支处理。
                if (rankMap.containsKey(key)) {
                    throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊诊断排序号不能重复");
                }
                rankMap.put(key, Boolean.TRUE);
            }
        }
        for (Map.Entry<Long, Integer> entry : primaryCountMap.entrySet()) {
            // 业务分支处理。
            if (entry.getValue() > 1) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊主诊断必须唯一");
            }
        }
    }

    /**
     * 校验批量诊断集合约束。
     */
    private void assertDiagnosisCollectionRuleByBatchItem(BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand item) {
        int primaryCount = 0;
        Set<Integer> ranks = new HashSet<>();
        for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
            // 业务分支处理。
            if (Boolean.TRUE.equals(diagnosisItem.getPrimaryFlag())) {
                primaryCount++;
            }
            // 业务分支处理。
            if (diagnosisItem.getRank() != null && !ranks.add(diagnosisItem.getRank())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊诊断排序号不能重复");
            }
        }
        // 业务分支处理。
        if (primaryCount > 1) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊主诊断必须唯一");
        }
    }

    /**
     * 为批量新开就诊分配 busNo：按业务日重置序号，前缀与 {@link AggregateTypeConstants#ENCOUNTER} 一致。
     *
     * @param count 条数，须与后续落库条数一致
     * @return 与条数等长的编号列表
     */
    private List<String> allocateEncounterBusNos(int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        return businessSequenceService.getSeqByDay(AggregateTypeConstants.ENCOUNTER, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, count);
    }

    /**
     * 分配住院就诊 busNo（前缀 {@link AggregateTypeConstants#INPATIENT_ENCOUNTER}，与门诊 EN 分离）。
     *
     * @param count 条数
     * @return 业务号列表
     */
    private List<String> allocateInpatientBusNos(int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        return businessSequenceService.getSeqByDay(
            AggregateTypeConstants.INPATIENT_ENCOUNTER, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, count);
    }

    /**
     * 写侧校验：源门急诊就诊可发起住院申请。
     *
     * @param ambEncounterId 源就诊标识
     */
    private void assertAmbForInpatientWrite(Long ambEncounterId) {
        Encounter amb = encounterRepository.findById(ambEncounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "源就诊不存在"));
        String classCode = amb.getClassEnum();
        if (!EncounterClass.AMB.getCode().equals(classCode) && !EncounterClass.EMER.getCode().equals(classCode)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅门诊或急诊就诊可发起住院申请");
        }
        EncounterStatus current = EnumUtil.fromCode(EncounterStatus.class, amb.getStatusEnum());
        if (current.isTerminal()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "源就诊已结束，无法办理住院");
        }
        List<String> eligible = Arrays.asList(
            EncounterStatus.REGISTERED.getCode(),
            EncounterStatus.TRIAGED.getCode(),
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode()
        );
        if (!eligible.contains(amb.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "源就诊当前状态不允许办理住院");
        }
    }

    /**
     * 写侧防重：同一 amb 锚点不得存在未取消住院线（BR-IP-REQ-002）。
     *
     * @param ambEncounterId 源就诊标识
     */
    private void assertNoOpenInpatientForAmb(Long ambEncounterId) {
        List<String> excluded = Collections.singletonList(EncounterStatus.CANCELLED.getCode());
        Optional<Encounter> existing = encounterRepository.findTopByAmbEncounterIdAndClassEnumAndStatusEnumNotInOrderByCreatedAtDesc(
            ambEncounterId, EncounterClass.IMP.getCode(), excluded);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "该门诊就诊已存在住院申请");
        }
    }

    /**
     * 为住院就诊落入院诊断：新开病情或复用 conditionId 后建就诊诊断关联。
     *
     * @param inpatient 已保存的住院就诊
     * @param dxItems 入院诊断明细（可为空）
     * @param operatorId 操作人
     */
    private void saveAdmissionDx(Encounter inpatient, List<AdmissionConditionItem> dxItems, Long operatorId) {
        if (dxItems == null || dxItems.isEmpty()) {
            return;
        }
        List<UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand> assocItems =
            new ArrayList<>();
        int rank = 1;
        for (AdmissionConditionItem dx : dxItems) {
            Long conditionId = dx.getConditionId();
            // 无病情事实：先复用同目录主诊，否则按目录新开病情事实。
            if (conditionId == null) {
                conditionId = this.resolveConditionIdForAdmissionDxItem(inpatient, dx, operatorId);
            }
            assocItems.add(this.newAdmissionDiagnosisAssocItem(inpatient, dx, conditionId, rank));
            rank++;
        }
        UpsertEncounterDiagnosisAssociationCommand assocCmd = new UpsertEncounterDiagnosisAssociationCommand();
        assocCmd.setOperatorId(operatorId);
        assocCmd.setItems(assocItems);
        this.upsertEncounterDiagnosisAssociation(assocCmd);
    }

    /**
     * 解析单行入院诊断的病情 ID：请求已带 conditionId 时由调用方传入；否则复用或新开。
     *
     * @param inpatient 住院就诊
     * @param dx 入院诊断行
     * @param operatorId 操作人
     * @return 病情事实 ID
     */
    private Long resolveConditionIdForAdmissionDxItem(
        Encounter inpatient,
        AdmissionConditionItem dx,
        Long operatorId) {
        if (dx.getConditionDefinitionId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "入院诊断须指定病情或诊断目录");
        }
        Long existingConditionId = this.resolveExistingConditionIdForAdmissionDefinition(
            inpatient.getId(), dx.getConditionDefinitionId());
        if (existingConditionId != null) {
            return existingConditionId;
        }
        SaveDoctorDiagnosisCommand saveCmd = new SaveDoctorDiagnosisCommand();
        saveCmd.setEncounterId(inpatient.getId());
        saveCmd.setPatientId(inpatient.getPatientId());
        saveCmd.setOperatorId(operatorId);
        saveCmd.setAdmissionDiagnosisFlow(Boolean.TRUE);
        SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand diagItem =
            new SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand();
        diagItem.setDefinitionId(dx.getConditionDefinitionId());
        diagItem.setDescription(dx.getDescription());
        diagItem.setVerificationStatusEnum(
            DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.CONFIRMED);
        saveCmd.setItems(Collections.singletonList(diagItem));
        List<Long> savedIds = conditionDomainService.saveDoctorDiagnosis(saveCmd);
        return savedIds.get(0);
    }

    /**
     * 组装单行就诊诊断关联 upsert 项（入院/登记落库用）。
     *
     * <p>诊断类型码固定为 {@link IptPatientDiseaseDiagType#ADMISSION}（ipt_dise_type_code 入院诊断），
     * 与医生站选诊断时由用户显式选择的住院疾病诊断类型口径区分——申请/登记入院诊断由领域自动落库。</p>
     *
     * @param inpatient 住院就诊
     * @param dx 入院诊断行
     * @param conditionId 病情事实 ID
     * @param rank 排序号
     * @return upsert 命令子项
     */
    private UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand newAdmissionDiagnosisAssocItem(
        Encounter inpatient,
        AdmissionConditionItem dx,
        Long conditionId,
        int rank) {
        UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand assoc =
            new UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand();
        assoc.setEncounterId(inpatient.getId());
        assoc.setConditionId(conditionId);
        assoc.setPrimaryFlag(dx.getPrimaryFlag());
        assoc.setRank(rank);
        assoc.setDescription(dx.getDescription());
        // 申请/登记入院诊断：就诊关联诊断类型一律为「入院诊断」（与 adm_encounter_diagnosis.diag_type_code 对齐）。
        assoc.setDiagTypeCode(IptPatientDiseaseDiagType.ADMISSION.getCode());
        return assoc;
    }

    /**
     * 私有方法：defaultNow。
     */
    private LocalDateTime defaultNow(LocalDateTime input) {
        // 业务分支处理。
        if (input == null) {
            return LocalDateTime.now();
        }
        return input;
    }

    /**
     * 私有方法：loadEncounterMap。
     */
    private Map<Long, Encounter> loadEncounterMap(List<Long> encounterIds) {
        List<Encounter> records = encounterRepository.findAllByIdIn(encounterIds);
        Map<Long, Encounter> map = new HashMap<>();
        for (Encounter encounter : records) {
            map.put(encounter.getId(), encounter);
        }
        return map;
    }

    /**
     * 私有方法：requireEncounter。
     */
    private Encounter requireEncounter(Map<Long, Encounter> map, Long encounterId) {
        Encounter encounter = map.get(encounterId);
        // 业务分支处理。
        if (encounter == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在");
        }
        return encounter;
    }

    /**
     * 判断就诊是否存在活动床位级位置（已分床，DM §6.1.23 取消门禁）。
     *
     * @param encounterId 住院就诊主键
     * @return 已分床返回 true
     */
    /**
     * 住院护士站保存患者信息门禁：仅 {@code IMP} 且状态为 {@code REGISTERED} 或 {@code IN_PROGRESS}。
     */
    private void assertNurseStationPatientSavable(Encounter encounter) {
        encounter.assertClass(EncounterClass.IMP.getCode());
        String status = encounter.getStatusEnum();
        if (!EncounterStatus.REGISTERED.getCode().equals(status)
            && !EncounterStatus.IN_PROGRESS.getCode().equals(status)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "当前就诊状态不允许护士站保存患者信息");
        }
    }

    /**
     * 判断就诊是否已存在活动床位级位置（用于取消登记等门禁）。
     *
     * @param encounterId 就诊主键
     * @return 存在活动 {@code BED} 形态位置时 true
     */
    private boolean hasActiveBed(Long encounterId) {
        List<EncounterLocation> activeLocations = locationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        for (EncounterLocation location : activeLocations) {
            if (LocationForm.BED.getCode().equals(location.getFormEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析就诊当前唯一活动床位 Location 主键。
     *
     * <p>在全部活动 {@code adm_encounter_location} 中筛选 {@code form_enum=BED}；
     * 多于一条活动床或不存在时抛业务异常。</p>
     *
     * @param encounterId 住院就诊主键
     * @return 活动床位 locationId
     * @throws BusinessException 无活动床或存在多条活动床时
     */
    private Long requireActiveBedId(Long encounterId) {
        return encounterQueryService.requireActiveLocationId(
            encounterId,
            LocationForm.BED.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "当前就诊无活动床位",
            ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
            "存在多条活动床位位置");
    }

    /**
     * 维护活动病区轨迹：与申请阶段已写 WARD 同 {@code locationId} 则跳过；否则收口活动 WARD 后 INSERT。
     */
    private void upsertActiveWard(
        Long encounterId, Long wardId, LocalDateTime effectiveAt, List<EncounterLocation> sink) {
        List<EncounterLocation> activeLocations = locationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        for (EncounterLocation row : activeLocations) {
            if (LocationForm.WARD.getCode().equals(row.getFormEnum())
                && wardId.equals(row.getLocationId())) {
                return;
            }
        }
        for (EncounterLocation row : activeLocations) {
            if (LocationForm.WARD.getCode().equals(row.getFormEnum())) {
                row.markCompleted(effectiveAt);
                sink.add(row);
            }
        }
        this.appendActiveLocation(encounterId, wardId, LocationForm.WARD.getCode(), effectiveAt, sink);
    }

    /**
     * 收口就诊下活动病房/病床轨迹（不含申请或登记已写入的病区 {@code WARD}）。
     */
    private void completeActiveHouseAndBedLocations(
        Long encounterId, LocalDateTime effectiveAt, List<EncounterLocation> sink) {
        List<EncounterLocation> activeLocations = locationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        for (EncounterLocation old : activeLocations) {
            String form = old.getFormEnum();
            if (LocationForm.HOUSE.getCode().equals(form) || LocationForm.BED.getCode().equals(form)) {
                old.markCompleted(effectiveAt);
                sink.add(old);
            }
        }
    }

    /**
     * 追加一条活动位置轨迹（INSERT 语义，经 {@code markActive} 落库）。
     */
    private void appendActiveLocation(
        Long encounterId, Long locationId, String formEnum, LocalDateTime activeAt, List<EncounterLocation> sink) {
        EncounterLocation row = new EncounterLocation();
        row.setEncounterId(encounterId);
        row.setLocationId(locationId);
        row.setFormEnum(formEnum);
        row.markActive(activeAt);
        sink.add(row);
    }

    /**
     * 收口就诊下全部活动参与者（换医护/分床换班：旧行 COMPLETED，禁止 UPDATE）。
     */
    private void completeAllActiveParticipants(
        Long encounterId, LocalDateTime effectiveAt, List<EncounterParticipant> sink) {
        List<EncounterParticipant> activeRows = participantRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        for (EncounterParticipant old : activeRows) {
            old.markCompleted(effectiveAt);
            sink.add(old);
        }
    }

    /**
     * 分床载荷：校验后批量新建活动参与者行。
     */
    private void appendParticipantsFromAssignLines(
        Long encounterId,
        LocalDateTime effectiveAt,
        List<AssignInpatientBedCommand.ParticipantLine> lines,
        List<EncounterParticipant> sink) {
        Set<String> roleCodes = new HashSet<>();
        for (AssignInpatientBedCommand.ParticipantLine row : lines) {
            if (row == null || StringUtils.isBlank(row.getRoleCode()) || row.getPractitionerId() == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "参与者角色与医务人员不能为空");
            }
            if (!roleCodes.add(row.getRoleCode().trim())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                    "同一分床请求中参与者角色不能重复");
            }
        }
        for (AssignInpatientBedCommand.ParticipantLine row : lines) {
            EncounterParticipant participant = new EncounterParticipant();
            participant.setEncounterId(encounterId);
            participant.setRoleCode(row.getRoleCode().trim());
            participant.setPractitionerId(row.getPractitionerId());
            participant.markActive(effectiveAt);
            sink.add(participant);
        }
    }

}
