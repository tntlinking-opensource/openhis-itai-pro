/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.service;

import java.util.Objects;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.share.constants.EncounterConstants;
import com.zxplat.domain.share.constants.RegistrationSlipConstants;
import com.zxplat.domain.administration.encounter.command.*;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.entity.EncounterDiagnosis;
import com.zxplat.domain.administration.encounter.entity.EncounterLocation;
import com.zxplat.domain.administration.encounter.entity.EncounterParticipant;
import com.zxplat.domain.administration.encounter.repository.EncounterDiagnosisRepository;
import com.zxplat.domain.administration.encounter.repository.EncounterLocationRepository;
import com.zxplat.domain.administration.encounter.repository.EncounterParticipantRepository;
import com.zxplat.domain.administration.encounter.repository.EncounterRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.enums.EncounterActivityStatus;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.EncounterParticipantRole;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.events.administration.encounter.*;
import com.zxplat.domain.share.utils.CommandBatchUtil;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 就诊（{@code adm_encounter}）聚合写侧领域服务。
 *
 * <p>职责：门诊/住院开立、状态迁移、参与者与位置子表、诊断关联及领域事件发布。</p>
 * <p>不变量：状态迁移门禁（BR-ENC-004/006 等）、主诊断唯一、持久化先于事件（B.14）。</p>
 * <p>仅注入本聚合 {@link EncounterRepository} 及参与者/位置/诊断子表仓储。</p>
 */
@Service
@RequiredArgsConstructor
public class EncounterDomainService {

    private final EncounterRepository encounterRepository;
    private final EncounterParticipantRepository participantRepository;
    private final EncounterLocationRepository locationRepository;
    private final EncounterDiagnosisRepository diagnosisRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 就诊等业务流水号，按日递增，前缀对齐 {@link AggregateTypeConstants#ENCOUNTER}。 */
    private final BusinessSequenceService businessSequenceService;
    /**
     * 开立门诊就诊。
     *
     * <p>默认执行同日同科室占名额防重（BR-ENC-011）；命令
     * {@code skipSameDaySameDeptDuplicateCheck=true} 时跳过该断言（院点策略放宽）。</p>
     *
     * @param command 命令
     * @return 首条就诊标识
     */
    public Long registerOutpatientEncounter(RegisterOutpatientEncounterCommand command) {
        List<RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand> items = command.resolveItems();
        List<Encounter> toSave = new ArrayList<>();
        LocalDateTime occurredAt = command.getOccurredAt();
        // 命令未带发生时刻时取服务器当前时间，保证事件与落库时间轴一致
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
        List<String> encounterBusNos = this.allocateEncounterBusNos(items.size());
        // 院点策略显式放宽时跳过同日同科防重；否则仍按 BR-ENC-011 占名额断言。
        boolean skipSameDaySameDeptDuplicateCheck =
            Boolean.TRUE.equals(command.getSkipSameDaySameDeptDuplicateCheck());
        for (int index = 0; index < items.size(); index++) {
            RegisterOutpatientEncounterCommand.RegisterOutpatientEncounterItemCommand item = items.get(index);
            // 先执行门诊防重：同日、同患者、同目标科室已存在占名额就诊（含待挂号）时不允许再开立。
            if (!skipSameDaySameDeptDuplicateCheck) {
                this.assertNoOutpatientDuplicate(item.getPatientId(), item.getOrganizationId(), occurredAt.toLocalDate());
            }
            Encounter encounter = new Encounter();
            encounter.setPatientId(item.getPatientId());
            encounter.setServiceTypeId(item.getServiceTypeId());
            encounter.setOrganizationId(item.getOrganizationId());
            // 就诊 busNo 为业务流水：按自然日分段递增，前缀取 AggregateType.ENCOUNTER。
            encounter.setBusNo(encounterBusNos.get(index));
            encounter.initOutpatientPlanned();
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
            // 挂号指定医师时写入 REGISTRATION_DOCTOR；接诊 ADMITTER 仅在 startOutpatientReception 落库。
            if (item.getPractitionerId() != null) {
                EncounterParticipant participant = new EncounterParticipant();
                participant.setEncounterId(encounter.getId());
                participant.setRoleCode(EncounterParticipantRole.REGISTRATION_DOCTOR.getCode());
                participant.setPractitionerId(item.getPractitionerId());
                participant.markActive(occurredAt);
                initParticipants.add(participant);
            }
        }
        // 持久化：挂号指定医师时同步写入参与者子表
        if (!initParticipants.isEmpty()) {
            participantRepository.saveAll(initParticipants);
        }
        return saved.get(0).getId();
    }

    /**
     * 创建体检就诊（class=PE，status=IN_PROGRESS）。
     *
     * @param command 命令
     * @return 就诊主键
     */
    public Long registerPhysicalExamEncounter(RegisterPhysicalExamEncounterCommand command) {
        if (command == null || command.getPatientId() == null || command.getOrganizationId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "患者与体检中心机构不能为空");
        }
        List<String> busNos = this.allocateEncounterBusNos(1);
        Encounter encounter = new Encounter();
        encounter.setPatientId(command.getPatientId());
        encounter.setOrganizationId(command.getOrganizationId());
        encounter.setRegistrarId(command.getRegistrarId());
        encounter.setBusNo(busNos.get(0));
        encounter.setStartTime(LocalDateTime.now());
        encounter.initPhysicalExamInProgress();
        encounter.assertPhysicalExamRegistrationRequiredFields();
        Encounter saved = encounterRepository.save(encounter);
        return saved.getId();
    }

    /**
     * 撤销体检在检就诊：{@code PE + IN_PROGRESS} → {@code ENTERED_IN_ERROR}（团检筹备撤销建单）。
     *
     * @param encounterId 就诊主键
     */
    public void voidPhysicalExamEncounter(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
        encounter.assertClass(EncounterClass.PE.getCode());
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅在检中的体检就诊可撤销建单");
        }
        encounter.transitToEnteredInError();
        encounterRepository.save(encounter);
    }

    /**
     * 体检总检完成后结束就诊（AS-PEIS-MAS-001 MAS-04 / BR-MAS-05）。
     *
     * @param command 结束命令
     */
    public void finishEncounter(FinishEncounterCommand command) {
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Encounter encounter = encounterRepository.findById(command.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
        if (Boolean.TRUE.equals(encounter.getIsDeleted())) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在");
        }
        encounter.finishPhysicalExam();
        encounter.setUpdatedAt(LocalDateTime.now());
        if (command.getOperatorId() != null) {
            encounter.setUpdatedBy(command.getOperatorId());
        }
        encounterRepository.save(encounter);
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
            LocalDateTime receptionTime;
            if (item.getReceptionTime() == null) {
                receptionTime = LocalDateTime.now();
            } else {
                receptionTime = item.getReceptionTime();
            }
            // 诊毕/暂离重新接诊与常规待诊接诊分流，避免终止态被 assertNotTerminal 拦截。
            if (EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())) {
                encounter.resumeOutpatientReceptionFromDischarged(receptionTime);
            } else if (EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                encounter.resumeOutpatientFromOnHold();
            } else {
                encounter.assertReceptionAllowed();
                encounter.transitToInProgress(receptionTime);
            }
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
        // 持久化：接诊医生参与者收口与新建须先于事件发布
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
     * 门诊取消接诊：在诊/暂离/完诊回退为已挂号，收口活动接诊医生，供窗口退号前由医生明示同意。
     *
     * <p>与账单门禁无关；本聚合仅迁移就诊状态与 ADMITTER。本诊病历/诊断/医嘱须已删净的门禁在
     * 应用层 IF-DOR-06 Pre（BR-DOR-011）完成，不在此跨聚合校验。
     * 持久化后发布 {@link OutpatientReceptionCancelledEvent}。</p>
     *
     * @param command 取消接诊命令
     */
    public void cancelOutpatientReception(CancelOutpatientReceptionCommand command) {
        List<CancelOutpatientReceptionCommand.CancelOutpatientReceptionItemCommand> items = command.resolveItems();
        // 前置校验/加载：按命令项批量装载就诊，避免循环内单条查库。
        Map<Long, Encounter> encounterMap = this.loadEncounterMap(
            CommandBatchUtil.extractIds(items, CancelOutpatientReceptionCommand.CancelOutpatientReceptionItemCommand::getEncounterId));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterParticipant> participantUpserts = new ArrayList<>();
        for (CancelOutpatientReceptionCommand.CancelOutpatientReceptionItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            LocalDateTime cancelledAt;
            if (item.getCancelledAt() == null) {
                cancelledAt = LocalDateTime.now();
                // 回填命令项，保证后续事件发布时间与参与者收口时刻一致。
                item.setCancelledAt(cancelledAt);
            } else {
                cancelledAt = item.getCancelledAt();
            }
            // 状态迁移：在诊/暂离/完诊 → 已挂号，清空接诊与完诊时刻。
            encounter.cancelOutpatientReception();
            toSave.add(encounter);
            // 同角色只能有一个活动参与者：取消接诊时收口全部活动 ADMITTER。
            List<EncounterParticipant> activeDoctors = participantRepository
                .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                    encounter.getId(), EncounterConstants.ROLE_ADMITTER, EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterParticipant old : activeDoctors) {
                old.markCompleted(cancelledAt);
                participantUpserts.add(old);
            }
        }
        // 持久化：就诊主档先于参与者与事件。
        encounterRepository.saveAll(toSave);
        if (!participantUpserts.isEmpty()) {
            participantRepository.saveAll(participantUpserts);
        }
        // 发布事件：载荷带取消后状态码，供医生站队列从在诊/完诊回到待诊。
        for (CancelOutpatientReceptionCommand.CancelOutpatientReceptionItemCommand item : items) {
            Encounter encounter = this.requireEncounter(encounterMap, item.getEncounterId());
            OutpatientReceptionCancelledEvent cancelledEvent =
                new OutpatientReceptionCancelledEvent(
                    encounter.getId(), encounter.getPatientId(), item.getCancelledAt());
            cancelledEvent.setStatusEnum(encounter.getStatusEnum());
            domainEventPublisher.publish(cancelledEvent);
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
            LocalDateTime completedAt;
            if (item.getCompletedAt() == null) {
                completedAt = LocalDateTime.now();
            } else {
                completedAt = item.getCompletedAt();
            }
            encounter.transitToDischarged(completedAt);
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
     * 作废待挂号（PLANNED）就诊：预结算未缴费场景，迁入 {@link EncounterStatus#ENTERED_IN_ERROR}，
     * 不走 {@link #cancelOutpatientRegistration} 退号门禁（退号落 {@link EncounterStatus#CANCELLED}）。
     *
     * @param encounterId  就诊主键
     * @param cancelReason 撤销原因
     */
    public void cancelPlannedOutpatientRegistration(Long encounterId, String cancelReason) {
        // 前置校验：须为门诊 AMB 且仍为待挂号
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
        encounter.assertClass(EncounterClass.AMB.getCode());
        if (!EncounterStatus.PLANNED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅待挂号状态可撤销");
        }
        // 持久化：未收费取消 → 错误录入，与退号 CANCELLED 分流
        encounter.transitToEnteredInError();
        encounterRepository.save(encounter);
        // 发布事件：载荷带终止态码，供读模型/占号释放侧投影
        OutpatientRegistrationCancelledEvent cancelledEvent = new OutpatientRegistrationCancelledEvent(
            encounter.getId(), encounter.getPatientId(), cancelReason);
        cancelledEvent.setStatusEnum(encounter.getStatusEnum());
        cancelledEvent.setCancelledAt(LocalDateTime.now());
        domainEventPublisher.publish(cancelledEvent);
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
     * 维护就诊诊断关联（单条或少量 upsert，非医生站全量批次路径）。
     *
     * <p>步骤：集合规则校验 → 按行主键或目录定义匹配存量行 → 组装/更新 {@link EncounterDiagnosis} →
     * {@code saveAll} → 逐条发布 {@link EncounterDiagnosisAssociationUpsertedEvent}（B.14：先落库后发事件）。</p>
     *
     * @param command 含就诊、诊断目录定义及主诊/排序等字段
     * @throws BusinessException 诊断集合规则或主诊唯一性校验失败
     */
    public void upsertEncounterDiagnosisAssociation(UpsertEncounterDiagnosisAssociationCommand command) {
        List<UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand> items = command.resolveItems();
        // 前置校验：同批次主诊与 rank 唯一
        this.assertDiagnosisCollectionRule(items);
        List<EncounterDiagnosis> toSave = new ArrayList<>();
        for (UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand item : items) {
            // 主诊断跨库唯一性：校验数据库已有记录不与本次冲突（BR-ENC-014）。
            if (Boolean.TRUE.equals(item.getPrimaryFlag())) {
                this.assertNoPrimaryDiagnosisConflict(item.getEncounterId(), item.getEncounterDiagnosisId());
            }
            // 已有关联则覆盖写；否则新建行（不在此处软删未出现项——非全量语义）。
            EncounterDiagnosis diagnosis = this.resolveUpsertEncounterDiagnosis(item);
            if (diagnosis == null) {
                diagnosis = new EncounterDiagnosis();
            } else {
                this.reactivateEncounterDiagnosis(diagnosis);
            }
            this.applyUpsertItemToEncounterDiagnosis(diagnosis, item);
            toSave.add(diagnosis);
        }
        // 持久化：先落库再发事件
        List<EncounterDiagnosis> saved = diagnosisRepository.saveAll(toSave);
        // 发布事件：载荷带就诊诊断行主键与目录定义，供读模型投影
        for (EncounterDiagnosis diagnosis : saved) {
            EncounterDiagnosisAssociationUpsertedEvent event = new EncounterDiagnosisAssociationUpsertedEvent(
                diagnosis.getEncounterId(),
                diagnosis.getId(),
                diagnosis.getConditionDefinitionId(),
                diagnosis.getPrimaryFlag(),
                diagnosis.getRank());
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 批量维护就诊诊断关联（医生站保存**西医**诊断后的主路径：全量批次 + 软删未再出现的旧关联）。
     *
     * <p>对每个批次子项：校验规则 → 将存量关联装入 Map → 合并本批活动病情 id → 未再出现的存量行软删 →
     * {@code saveAll} → 构建一次就诊快照 → 对本批每条活动诊断各发一条 upsert 事件（事件内嵌同一快照 JSON）。</p>
     *
     * <p>与 {@link #batchUpsertTcmEncounterDiagnosisAssociation} 对称：本方法仅对齐 {@code tcm_flag=false}
     * 的西医关联，**不**覆盖、**不**软删 {@code tcm_flag=true} 的中医病证组行（AS-OP-DOR-002 / BR-TCM-DIA-006）。</p>
     *
     * @param command 含多条就诊子批次，每子批次带全量西医诊断项列表
     * @throws BusinessException 集合规则或主诊约束失败
     */
    public void batchUpsertEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand command) {
        List<BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand> items = command.resolveItems();
        for (BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand item : items) {
            // 前置校验：西医批次主诊与 rank 唯一
            this.assertDiagnosisCollectionRuleByBatchItem(item);
            List<EncounterDiagnosis> exists = diagnosisRepository.findAllByEncounterId(item.getEncounterId());
            EncounterDiagnosisIndex index = EncounterDiagnosisIndex.fromRows(exists);
            List<EncounterDiagnosis> toSave = new ArrayList<>();
            Set<Long> activeEncounterDiagnosisIds = new HashSet<>();
            Set<Long> activeWesternDefinitionIds = new HashSet<>();
            for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
                EncounterDiagnosis diagnosis = this.resolveWesternBatchDiagnosis(index, diagnosisItem);
                // 中医病证组由 IF-DOR-32 专管：西医批次不得覆盖存量中医关联（保留 tcm_syndrome_group 与 tcm_flag）。
                if (diagnosis != null && Boolean.TRUE.equals(diagnosis.getTcmFlag())) {
                    // 住院 IF-IP-DOR-05：批次若带入中医行，仅同步 diag_type_code，不拆病证组。
                    String diagTypeCode = trimToNull(diagnosisItem.getDiagTypeCode());
                    if (diagTypeCode != null) {
                        diagnosis.setDiagTypeCode(diagTypeCode);
                        toSave.add(diagnosis);
                        activeEncounterDiagnosisIds.add(diagnosis.getId());
                    }
                    continue;
                }
                // 本批次首见该目录定义：新建关联；否则在存量行上覆盖写展示与主诊等字段。
                if (diagnosis == null) {
                    diagnosis = new EncounterDiagnosis();
                } else {
                    this.reactivateEncounterDiagnosis(diagnosis);
                    activeEncounterDiagnosisIds.add(diagnosis.getId());
                }
                this.applyWesternBatchItemToEncounterDiagnosis(diagnosis, item.getEncounterId(), diagnosisItem);
                toSave.add(diagnosis);
                if (diagnosisItem.getConditionDefinitionId() != null) {
                    activeWesternDefinitionIds.add(diagnosisItem.getConditionDefinitionId());
                }
            }
            for (EncounterDiagnosis old : exists) {
                // 全量批次未再出现的西医关联：软删；中医病证组行保持不变。
                if (Boolean.TRUE.equals(old.getTcmFlag())) {
                    continue;
                }
                if (this.isWesternDiagnosisStillActive(old, activeEncounterDiagnosisIds, activeWesternDefinitionIds)) {
                    continue;
                }
                // 软删同时卸主诊，避免同事务重插主诊时 BR-ENC-014 仍命中已删行
                old.setIsDeleted(Boolean.TRUE);
                old.setDeletedAt(LocalDateTime.now());
                old.setPrimaryFlag(Boolean.FALSE);
                toSave.add(old);
            }
            // 持久化：先落库再发事件
            List<EncounterDiagnosis> saved = diagnosisRepository.saveAll(toSave);
            Map<Long, EncounterDiagnosis> savedById = new HashMap<>();
            for (EncounterDiagnosis row : saved) {
                if (row.getId() != null) {
                    savedById.put(row.getId(), row);
                }
            }
            // 发布事件：对本批活动西医诊断逐条 upsert（B.14 先落库后发事件）
            for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
                EncounterDiagnosis published = this.resolvePublishedWesternDiagnosis(
                    index, savedById, diagnosisItem, item.getEncounterId());
                if (published == null || Boolean.TRUE.equals(published.getTcmFlag())) {
                    continue;
                }
                EncounterDiagnosisAssociationUpsertedEvent event = new EncounterDiagnosisAssociationUpsertedEvent(
                    item.getEncounterId(),
                    published.getId(),
                    published.getConditionDefinitionId(),
                    published.getPrimaryFlag(),
                    published.getRank());
                domainEventPublisher.publish(event);
            }
        }
    }

    /**
     * 批量维护就诊**中医**诊断关联（中医 Tab 全量批次：仅软删 {@code tcm_flag=true} 且未再出现的关联，不影响西医诊断行）。
     *
     * <p>与 {@link #batchUpsertEncounterDiagnosisAssociation} 的区别：全量软删范围收窄为中医子集（AS-OP-DOR-010 · G-TCM-010-02）。</p>
     *
     * @param command 与西医批次相同结构；本批次每条须 {@code tcmFlag=true}
     * @throws BusinessException 集合规则失败或批次含非中医项时
     */
    public void batchUpsertTcmEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand command) {
        List<BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand> items = command.resolveItems();
        for (BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand item : items) {
            // Pre：本批次须全部为中医项，避免误写西医关联。
            this.assertTcmBatchUpsertItem(item);
            this.assertTcmDiagnosisCollectionRuleByBatchItem(item);
            List<EncounterDiagnosis> exists = diagnosisRepository.findAllByEncounterId(item.getEncounterId());
            EncounterDiagnosisIndex index = EncounterDiagnosisIndex.fromRows(exists);
            List<EncounterDiagnosis> toSave = new ArrayList<>();
            Set<Long> activeEncounterDiagnosisIds = new HashSet<>();
            Set<String> activeTcmMatchKeys = new HashSet<>();
            for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
                EncounterDiagnosis diagnosis = this.resolveTcmBatchDiagnosis(index, diagnosisItem);
                if (diagnosis == null) {
                    diagnosis = new EncounterDiagnosis();
                } else {
                    this.reactivateEncounterDiagnosis(diagnosis);
                    activeEncounterDiagnosisIds.add(diagnosis.getId());
                }
                this.applyTcmBatchItemToEncounterDiagnosis(diagnosis, item.getEncounterId(), diagnosisItem);
                toSave.add(diagnosis);
                activeTcmMatchKeys.add(this.buildTcmMatchKey(
                    diagnosisItem.getConditionDefinitionId(), diagnosisItem.getTcmSyndromeGroup()));
            }
            for (EncounterDiagnosis old : exists) {
                // Cmd：全量批次未再出现的中医关联软删；西医关联行不受影响。
                if (!Boolean.TRUE.equals(old.getTcmFlag())) {
                    continue;
                }
                if (this.isTcmDiagnosisStillActive(old, activeEncounterDiagnosisIds, activeTcmMatchKeys)) {
                    continue;
                }
                // 软删同时卸主诊，与西医全量批次一致，保证主诊唯一仅约束活动行
                old.setIsDeleted(Boolean.TRUE);
                old.setDeletedAt(LocalDateTime.now());
                old.setPrimaryFlag(Boolean.FALSE);
                toSave.add(old);
            }
            // 持久化：先落库再发事件
            List<EncounterDiagnosis> saved = diagnosisRepository.saveAll(toSave);
            Map<Long, EncounterDiagnosis> savedById = new HashMap<>();
            for (EncounterDiagnosis row : saved) {
                if (row.getId() != null) {
                    savedById.put(row.getId(), row);
                }
            }
            // Pub：对本批活动关联逐条发布 upsert 事件（B.14 先落库后发事件）。
            for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
                EncounterDiagnosis published = this.resolvePublishedTcmDiagnosis(
                    index, savedById, diagnosisItem, item.getEncounterId());
                if (published == null) {
                    continue;
                }
                EncounterDiagnosisAssociationUpsertedEvent event = new EncounterDiagnosisAssociationUpsertedEvent(
                    item.getEncounterId(),
                    published.getId(),
                    published.getConditionDefinitionId(),
                    published.getPrimaryFlag(),
                    published.getRank());
                domainEventPublisher.publish(event);
            }
        }
    }

    /**
     * 校验中医批次 upsert 子项：批次内每条诊断须标记为中医。
     *
     * @param item 单就诊批次
     * @throws BusinessException 存在非 tcm_flag 项时
     */
    private void assertTcmBatchUpsertItem(BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand item) {
        if (item == null || item.getDiagnosisItems() == null) {
            return;
        }
        for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
            if (!Boolean.TRUE.equals(diagnosisItem.getTcmFlag())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "中医病证组批次仅允许 tcm_flag=true 的诊断项");
            }
        }
    }

    /**
     * 移除就诊与指定病情事实之间的诊断关联（软删关联行并发布移除事件）。
     *
     * <p>顺序：加载关联 → 标记删除并卸主诊 → {@code saveAll} → 按就诊构建快照 →
     * 逐条 {@link EncounterDiagnosisAssociationRemovedEvent}（B.14）。</p>
     *
     * <p>软删时同步 {@code primaryFlag=false}，使同事务内「先删后插主诊」
     * （如住院医生站医保→国临展开）不再触发 BR-ENC-014 假冲突。</p>
     *
     * @param command 可含多条移除子项
     * @throws BusinessException 关联不存在
     */
    public void removeEncounterDiagnosisAssociation(RemoveEncounterDiagnosisAssociationCommand command) {
        List<RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand> items = command.resolveItems();
        List<EncounterDiagnosis> toSave = new ArrayList<>();
        for (RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item : items) {
            // 必须存在未删关联且归属本次就诊；幂等软删不在此路径表达，由应用层 del 接口吞并 NOT_FOUND。
            EncounterDiagnosis diagnosis = this.mustGetActiveEncounterDiagnosisForRemove(item);
            // 软删同时卸主诊，避免已删行仍占主诊名额
            diagnosis.setIsDeleted(Boolean.TRUE);
            diagnosis.setDeletedAt(LocalDateTime.now());
            diagnosis.setPrimaryFlag(Boolean.FALSE);
            toSave.add(diagnosis);
        }
        // 持久化：先落库再发事件
        diagnosisRepository.saveAll(toSave);
        for (RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item : items) {
            EncounterDiagnosisAssociationRemovedEvent event = new EncounterDiagnosisAssociationRemovedEvent(
                item.getEncounterId(), item.getEncounterDiagnosisId());
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 切换就诊主诊断：目标行（或其所在中医病证组）标为主诊，同就诊其余行取消主诊。
     *
     * <p>西医按单行切换；中医按 {@code tcm_syndrome_group} 整组切换，保证组内病/证主诊标记一致。
     * 切换后同一就诊仅保留一组/一条主诊（BR-ENC-014）。</p>
     *
     * @param command 就诊、目标就诊诊断行与操作人
     * @throws BusinessException 参数缺失、行不存在或不属于该就诊
     */
    public void switchPrimaryEncounterDiagnosis(SwitchPrimaryEncounterDiagnosisCommand command) {
        // 前置校验：切换主诊必须指定就诊与行主键
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (command.getEncounterDiagnosisId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "诊断标识不能为空");
        }
        Long encounterId = command.getEncounterId();
        Long targetId = command.getEncounterDiagnosisId();

        // 加载/组装：本就诊未删诊断行；定位目标并解析主诊落点（单行或中医病证组）
        List<EncounterDiagnosis> exists = diagnosisRepository.findAllByEncounterId(encounterId);
        EncounterDiagnosis target = null;
        for (EncounterDiagnosis row : exists) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (Objects.equals(row.getId(), targetId)) {
                target = row;
                break;
            }
        }
        if (target == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "诊断关联不存在");
        }
        if (!Objects.equals(target.getEncounterId(), encounterId)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊诊断行与就诊不匹配");
        }

        Set<Long> primaryIds = new HashSet<>();
        String groupKey = trimToNull(target.getTcmSyndromeGroup());
        if (groupKey != null && Boolean.TRUE.equals(target.getTcmFlag())) {
            // 中医：同病证组全部行一并标主诊，组级唯一
            for (EncounterDiagnosis row : exists) {
                if (Boolean.TRUE.equals(row.getIsDeleted())) {
                    continue;
                }
                if (groupKey.equals(trimToNull(row.getTcmSyndromeGroup()))) {
                    primaryIds.add(row.getId());
                }
            }
        } else {
            primaryIds.add(target.getId());
        }

        List<EncounterDiagnosis> toSave = new ArrayList<>();
        for (EncounterDiagnosis row : exists) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            boolean shouldPrimary = primaryIds.contains(row.getId());
            boolean currentPrimary = Boolean.TRUE.equals(row.getPrimaryFlag());
            // 仅写变更行，减少无意义更新
            if (shouldPrimary == currentPrimary) {
                continue;
            }
            row.setPrimaryFlag(shouldPrimary);
            toSave.add(row);
        }
        if (toSave.isEmpty()) {
            return;
        }

        // 持久化：先落库再发事件
        List<EncounterDiagnosis> saved = diagnosisRepository.saveAll(toSave);
        // 发布事件：主诊变更后投影读模型快照
        for (EncounterDiagnosis diagnosis : saved) {
            EncounterDiagnosisAssociationUpsertedEvent event = new EncounterDiagnosisAssociationUpsertedEvent(
                diagnosis.getEncounterId(),
                diagnosis.getId(),
                diagnosis.getConditionDefinitionId(),
                diagnosis.getPrimaryFlag(),
                diagnosis.getRank());
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
        List<Encounter> toSave = new ArrayList<>();
        List<String> inpatientBusNos = this.allocateInpatientBusNos(items.size());
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < items.size(); index++) {
            RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand item = items.get(index);
            // Pre：源门诊就诊须存在且可转住院；同一 amb 锚点不得重复未取消住院线。
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
            // 医生办理意向：仅提示款台医保办/自费办，不建账户、不触发医保入院
            encounter.setChargeIntent(item.getChargeIntent());
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
            // 申请医生落住院医生（ADMITTER）；分床回显默认带到住院医生（必填），主治/主任仍可选。
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
     * 住院登记（有申请路径：{@code PLANNED→REGISTERED}）。
     *
     * <p>按就诊 id 升序悲观行锁，锁内断言 {@code PLANNED} 后迁移，防双窗重复登记（GAP-ATD-004）。</p>
     *
     * @param command 命令
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerInpatientEncounter(RegisterInpatientEncounterCommand command) {
        List<RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand> items = command.resolveItems();
        List<RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand> sortedItems =
            new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        Map<Long, Encounter> encounterMap = new LinkedHashMap<>();
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterLocation> locationToSave = new ArrayList<>();
        for (RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item : sortedItems) {
            Encounter encounter = this.requireEncounterForUpdate(item.getEncounterId(), EncounterClass.IMP.getCode());
            encounterMap.put(encounter.getId(), encounter);
            encounter.assertClass(EncounterClass.IMP.getCode());
            // 委托聚合根 assertInpatientRegistrationAllowed() 校验住院登记前置状态，遵守 §D3.2 硬门禁。
            encounter.assertInpatientRegistrationAllowed();
            LocalDateTime admitTime;
            if (item.getRegisterTime() == null) {
                admitTime = LocalDateTime.now();
            } else {
                admitTime = item.getRegisterTime();
            }
            encounter.transitToInpatientRegistered(admitTime);
            encounter.setRegistrarId(item.getRegistrarId());
            encounter.setOrganizationId(item.getDepartmentId());
            if (StringUtils.isNotEmpty(item.getAdmitSourceCode())) {
                encounter.setAdmitSourceCode(item.getAdmitSourceCode());
            }
            if (StringUtils.isNotEmpty(item.getInWayCode())) {
                encounter.setInWayCode(item.getInWayCode());
            }
            if (StringUtils.isNotEmpty(item.getPriorityCode())) {
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
            this.publishInpatientAdmissionCompleted(encounter, item);
        }
    }

    /**
     * 无申请直登登记：INSERT {@code IMP+REGISTERED}，不经过 {@code PLANNED}。
     *
     * @param command 命令（{@link RegisterInpatientEncounterCommand#getPatientId()} 必填）
     * @return 新建住院就诊标识
     */
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
        LocalDateTime admitTime;
        if (item.getRegisterTime() == null) {
            admitTime = LocalDateTime.now();
        } else {
            admitTime = item.getRegisterTime();
        }
        Encounter encounter = new Encounter();
        encounter.setPatientId(command.getPatientId());
        encounter.setOrganizationId(item.getDepartmentId());
        encounter.setBusNo(this.allocateInpatientBusNos(1).get(0));
        encounter.initInpatientRegistered();
        encounter.setStartTime(admitTime);
        encounter.setRegistrarId(item.getRegistrarId());
        if (StringUtils.isNotEmpty(item.getAdmitSourceCode())) {
            encounter.setAdmitSourceCode(item.getAdmitSourceCode());
        }
        if (StringUtils.isNotEmpty(item.getInWayCode())) {
            encounter.setInWayCode(item.getInWayCode());
        }
        if (StringUtils.isNotEmpty(item.getPriorityCode())) {
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
            if (StringUtils.isNotEmpty(item.getPriorityEnum())) {
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
            if (StringUtils.isEmpty(bedFormEnum)) {
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
            if (StringUtils.isEmpty(toBedFormEnum)) {
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
     * 更正入院科室（DM-ADM-002 §6.1.15b）：待入科或已入科患者改入院科室与病区。
     *
     * <p>不迁移就诊状态，不写转科里程碑，不收口参与者。已入科且目标床与当前活动床不同时，
     * 收口活动病房/床后插入新轨迹，并发布 {@link BedTransferredEvent}。</p>
     *
     * @param command 目标科室、病区；已入科时含目标病房与床位
     */
    public void correctInpatientAdmissionDepartment(CorrectInpatientAdmissionDepartmentCommand command) {
        if (command == null || command.getEncounterId() == null || command.getDepartmentId() == null
            || command.getWardId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "更正入院科室缺少就诊、科室或病区");
        }
        Encounter encounter = this.requireEncounterForUpdate(command.getEncounterId(), EncounterClass.IMP.getCode());
        encounter.assertClass(EncounterClass.IMP.getCode());
        boolean inProgress = EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum());
        boolean registered = EncounterStatus.REGISTERED.getCode().equals(encounter.getStatusEnum());
        // 出院、转科中、待登记都不能在登记窗口改入院科室
        if (!inProgress && !registered) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅待入科或已入科的住院就诊可更正入院科室");
        }
        // 待入科还没有活动床，分床仍走护士站
        if (!inProgress && command.getBedId() != null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "待入科不能在更正入院科室时分床");
        }
        if (inProgress && command.getBedId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "已入科更正入院科室须指定床位");
        }
        LocalDateTime effectiveAt = LocalDateTime.now();
        encounter.setOrganizationId(command.getDepartmentId());
        List<EncounterLocation> locationToSave = new ArrayList<>();
        this.upsertActiveWard(encounter.getId(), command.getWardId(), effectiveAt, locationToSave);
        Long fromBedId = null;
        boolean bedMoved = false;
        if (inProgress) {
            fromBedId = this.requireActiveBedId(encounter.getId());
            // 仍住原床：只改科室/病区，床轨迹不动
            if (!fromBedId.equals(command.getBedId())) {
                if (command.getHouseId() == null) {
                    throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "更换床位须指定病房");
                }
                this.completeActiveHouseAndBedLocations(encounter.getId(), effectiveAt, locationToSave);
                this.appendActiveLocation(encounter.getId(), command.getHouseId(), LocationForm.HOUSE.getCode(),
                    effectiveAt, locationToSave);
                this.appendActiveLocation(encounter.getId(), command.getBedId(), LocationForm.BED.getCode(),
                    effectiveAt, locationToSave);
                bedMoved = true;
            }
        }
        encounterRepository.save(encounter);
        if (!locationToSave.isEmpty()) {
            locationRepository.saveAll(locationToSave);
        }
        if (bedMoved) {
            domainEventPublisher.publish(new BedTransferredEvent(
                encounter.getId(), fromBedId, command.getBedId(), effectiveAt));
        }
    }

    /**
     * 更正门诊挂号科室：只改就诊 {@code organization_id}。
     *
     * <p>不迁移就诊状态，不改参与者，不重占号源。院点未放宽时，目标科室当日已有占名额就诊则拒绝。</p>
     *
     * @param command 就诊与目标门诊科室；可带同日同科防重跳过标志
     */
    public void correctOutpatientRegistrationDepartment(CorrectOutpatientRegistrationDepartmentCommand command) {
        if (command == null || command.getEncounterId() == null || command.getDepartmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "更正挂号科室缺少就诊或科室");
        }
        Encounter encounter = this.requireEncounterForUpdate(command.getEncounterId(), EncounterClass.AMB.getCode());
        encounter.assertClass(EncounterClass.AMB.getCode());
        String status = encounter.getStatusEnum();
        boolean allowed = EncounterStatus.PLANNED.getCode().equals(status)
            || EncounterStatus.REGISTERED.getCode().equals(status)
            || EncounterStatus.TRIAGED.getCode().equals(status)
            || EncounterStatus.IN_PROGRESS.getCode().equals(status)
            || EncounterStatus.ON_HOLD.getCode().equals(status);
        if (!allowed) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅待挂号、已挂号、已分诊、就诊中或暂停的门诊就诊可修改挂号科室");
        }
        if (command.getDepartmentId().equals(encounter.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "挂号科室未变化");
        }
        if (!Boolean.TRUE.equals(command.getSkipSameDaySameDeptDuplicateCheck())) {
            LocalDate registrationDay;
            if (encounter.getStartTime() != null) {
                registrationDay = encounter.getStartTime().toLocalDate();
            } else {
                registrationDay = LocalDate.now();
            }
            this.assertNoOutpatientDuplicate(
                encounter.getPatientId(), command.getDepartmentId(), registrationDay);
        }
        encounter.setOrganizationId(command.getDepartmentId());
        encounterRepository.save(encounter);
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
     * 发起转科（GAP-ATD-006：悲观行锁 + 锁内 {@code IN_PROGRESS} 断言）。
     *
     * @param command 命令
     */
    public void initiateDepartmentTransfer(InitiateDepartmentTransferCommand command) {
        List<InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand> items = command.resolveItems();
        List<InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand> sortedItems =
            new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterParticipant> participantUpdates = new ArrayList<>();
        for (InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand item : sortedItems) {
            Encounter encounter = this.requireEncounterForUpdate(
                item.getEncounterId(), EncounterClass.IMP.getCode());
            encounter.assertClass(EncounterClass.IMP.getCode());
            if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "仅已入科在院状态的住院就诊可发起转科");
            }
            encounter.transitToOnHold();
            // organizationId 保持为转出科室，待 confirmDepartmentTransfer 时再更新为目标科室。
            toSave.add(encounter);
            // 关闭旧责任关系：收口当前全部活动参与者。
            LocalDateTime initiatedAt;
            if (item.getInitiatedAt() == null) {
                initiatedAt = LocalDateTime.now();
            } else {
                initiatedAt = item.getInitiatedAt();
            }
            List<EncounterParticipant> active = participantRepository
                .findAllByEncounterIdAndStatusEnum(encounter.getId(), EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterParticipant old : active) {
                old.markCompleted(initiatedAt);
                participantUpdates.add(old);
            }
        }
        if (!toSave.isEmpty()) {
            encounterRepository.saveAll(toSave);
        }
        // 持久化：转科收口的活动参与者须与主档同事务落库
        if (!participantUpdates.isEmpty()) {
            participantRepository.saveAll(participantUpdates);
        }
        for (InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand item : sortedItems) {
            domainEventPublisher.publish(new DepartmentTransferInitiatedEvent(
                item.getEncounterId(), item.getFromDepartmentId(), item.getToDepartmentId()));
        }
    }

    /**
     * 确认转科（GAP-ATD-006：悲观行锁 + 锁内 {@code ON_HOLD} 断言）。
     *
     * @param command 命令
     */
    public void confirmDepartmentTransfer(ConfirmDepartmentTransferCommand command) {
        List<ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand> items = command.resolveItems();
        List<ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand> sortedItems =
            new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterLocation> locationUpdates = new ArrayList<>();
        for (ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand item : sortedItems) {
            Encounter encounter = this.requireEncounterForUpdate(
                item.getEncounterId(), EncounterClass.IMP.getCode());
            encounter.assertClass(EncounterClass.IMP.getCode());
            if (!EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "仅待转科过渡状态的住院就诊可确认转科");
            }
            LocalDateTime effectiveAt;
            if (item.getEffectiveAt() == null) {
                effectiveAt = LocalDateTime.now();
            } else {
                effectiveAt = item.getEffectiveAt();
            }
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
        if (!toSave.isEmpty()) {
            encounterRepository.saveAll(toSave);
        }
        // 持久化：转科位置轨迹变更须与主档同事务落库
        if (!locationUpdates.isEmpty()) {
            locationRepository.saveAll(locationUpdates);
        }
        for (ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand item : sortedItems) {
            LocalDateTime effectiveAt;
            if (item.getEffectiveAt() == null) {
                effectiveAt = LocalDateTime.now();
            } else {
                effectiveAt = item.getEffectiveAt();
            }
            domainEventPublisher.publish(new DepartmentTransferConfirmedEvent(
                item.getEncounterId(), item.getTargetDepartmentId(), effectiveAt));
        }
    }

    /**
     * 住院出院医嘱校对后进入待出院（在科 → 待出院，{@code IN_PROGRESS} → {@code ON_HOLD}，GAP-ATD-006）。
     *
     * <p>仅迁移 Encounter 主档 {@code status_enum} 与 {@code out_way_code}；<strong>禁止</strong>写 {@code end_time}、
     * 禁止收口 {@code adm_encounter_location}/{@code adm_encounter_participant}、禁止 {@code releaseBed}（释床仅清床
     * {@link #clearDischargedBed}，AS-IP-NUR-002 IF-IP-NUR-ATD-06）。</p>
     *
     * @param command 命令
     */
    public void dischargeInpatientEncounter(DischargeInpatientEncounterCommand command) {
        List<DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand> items = command.resolveItems();
        List<DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand> sortedItems =
            new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Encounter> toSave = new ArrayList<>();
        for (DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand item : sortedItems) {
            Encounter encounter = this.requireEncounterForUpdate(
                item.getEncounterId(), EncounterClass.IMP.getCode());
            encounter.assertClass(EncounterClass.IMP.getCode());
            if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "仅已入科在院状态的住院就诊可开立出院医嘱");
            }
            // BR-ENC-004：出院前必须存在负责医生参与者。
            this.assertDoctorParticipantExists(encounter.getId());
            // BR-ENC-006：出院前必须存在主诊断。
            this.assertPrimaryDiagnosisExists(encounter.getId());
            encounter.transitToOnHold();
            encounter.setOutWayCode(item.getOutWayCode());
            // Cmd：仅主档待出院态；占床轨迹与床运营态保持不变，直至护士清床。
            toSave.add(encounter);
        }
        if (!toSave.isEmpty()) {
            encounterRepository.saveAll(toSave);
            for (Encounter encounter : toSave) {
                HospitalDischargedEvent event = new HospitalDischargedEvent(
                    encounter.getId(), encounter.getPatientId(), encounter.getOutWayCode(), null);
                event.setStatusEnum(encounter.getStatusEnum());
                event.setDischargeTypeEnum(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
                domainEventPublisher.publish(event);
            }
        }
    }

    /**
     * 撤销转科/出院过渡态（GAP-ATD-008：悲观行锁 + {@code ON_HOLD} → {@code IN_PROGRESS}）。
     *
     * <p>对称于校对转科/出院进入待转科/待出院。调用方（护士取消校对、医生签退/停嘱/删草稿）须已确认
     * 无其它已校对转科/出院残留；本方法仅校验 {@code IMP+ON_HOLD} 后回在科。
     * 已办理出院的 {@code PENDING} 不在本方法范围。</p>
     *
     * @param command 含操作人及按就诊升序的撤销项（转科轨另恢复参与者）
     */
    public void revertTransferOrDischargeHold(RevertTransferOrDischargeHoldCommand command) {
        List<RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand> items =
            command.resolveItems();
        List<RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand> sortedItems =
            new ArrayList<>(items);
        // 前置校验：按就诊 id 升序加锁，降低多就诊并发死锁概率
        sortedItems.sort(Comparator.comparing(
            RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Encounter> toSave = new ArrayList<>();
        List<EncounterParticipant> participantsToSave = new ArrayList<>();
        for (RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand item : sortedItems) {
            // 加载/组装：悲观行锁取就诊，断言住院类
            Encounter encounter = this.requireEncounterForUpdate(
                item.getEncounterId(), EncounterClass.IMP.getCode());
            encounter.assertClass(EncounterClass.IMP.getCode());
            boolean transferHold = Boolean.TRUE.equals(item.getTransferHold());
            LocalDateTime revertedAt;
            // 撤销时刻未传则取当前，供转科参与者恢复批次对齐
            if (item.getRevertedAt() == null) {
                revertedAt = LocalDateTime.now();
            } else {
                revertedAt = item.getRevertedAt();
            }
            // 状态迁移：ON_HOLD → IN_PROGRESS；出院轨清 outWayCode
            encounter.revertHoldToInProgress(!transferHold);
            toSave.add(encounter);
            // 转科轨：按最近收口批次恢复活动参与者
            if (transferHold) {
                this.restoreParticipantsFromLatestCompletedBatch(
                    item.getEncounterId(), revertedAt, participantsToSave);
            }
        }
        // 持久化：先参与者后主档，同事务提交
        if (!participantsToSave.isEmpty()) {
            participantRepository.saveAll(participantsToSave);
        }
        if (!toSave.isEmpty()) {
            encounterRepository.saveAll(toSave);
        }
    }

    /**
     * 护士站办理出院：待出院 → 待出院结算（{@code ON_HOLD} → {@code PENDING}，GAP-ATD-005）。
     *
     * <p>前置由应用层保证为「待出院」分流（非待转科）；本方法仅校验 {@code IMP+ON_HOLD}。
     * 已为 {@code PENDING} 时幂等跳过。按就诊 id 升序悲观行锁后迁移。
     * {@code dischargedAt} 须为出院医嘱计划时间（应用层自 {@code occurrence_start_time} 解析）；
     * 写入 {@code end_time} 作为业务出院时间真源，后续清床/结算不得覆盖。</p>
     *
     * @param command 命令
     */
    public void executeInpatientNurseDischarge(ExecuteInpatientNurseDischargeCommand command) {
        List<ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand> items =
            command.resolveItems();
        List<ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand> sortedItems =
            new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Encounter> toSave = new ArrayList<>();
        for (ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand item : sortedItems) {
            Encounter encounter = this.requireEncounterForUpdate(
                item.getEncounterId(), EncounterClass.IMP.getCode());
            encounter.assertClass(EncounterClass.IMP.getCode());
            if (EncounterStatus.PENDING.getCode().equals(encounter.getStatusEnum())) {
                continue;
            }
            if (!EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "仅待出院状态的住院就诊可办理出院");
            }
            LocalDateTime dischargedAt;
            if (item.getDischargedAt() == null) {
                dischargedAt = LocalDateTime.now();
            } else {
                dischargedAt = item.getDischargedAt();
            }
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
     * 取消护士办理出院：待结算 → 待出院或已入科（{@code PENDING} → {@code ON_HOLD}/{@code IN_PROGRESS}）。
     *
     * <p>对称 {@link #executeInpatientNurseDischarge}；清空 {@code end_time}；有有效出院嘱回待出院，
     * 否则回已入科并清 {@code outWayCode}。已为目标态时幂等跳过。按就诊 id 升序悲观行锁。</p>
     *
     * @param command 命令（子项含 {@code restoreToInProgress}）
     */
    public void cancelInpatientNurseDischarge(CancelInpatientNurseDischargeCommand command) {
        List<CancelInpatientNurseDischargeCommand.CancelInpatientNurseDischargeItemCommand> items =
            command.resolveItems();
        List<CancelInpatientNurseDischargeCommand.CancelInpatientNurseDischargeItemCommand> sortedItems =
            new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            CancelInpatientNurseDischargeCommand.CancelInpatientNurseDischargeItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        List<Encounter> toSave = new ArrayList<>();
        for (CancelInpatientNurseDischargeCommand.CancelInpatientNurseDischargeItemCommand item : sortedItems) {
            Encounter encounter = this.requireEncounterForUpdate(
                item.getEncounterId(), EncounterClass.IMP.getCode());
            encounter.assertClass(EncounterClass.IMP.getCode());
            boolean restoreToInProgress = Boolean.TRUE.equals(item.getRestoreToInProgress());
            String statusCode = encounter.getStatusEnum();
            // 已回待出院且本请求亦回待出院：幂等跳过，避免重复写库
            if (EncounterStatus.ON_HOLD.getCode().equals(statusCode) && !restoreToInProgress) {
                continue;
            }
            // 已回已入科且本请求亦回已入科：幂等跳过
            if (EncounterStatus.IN_PROGRESS.getCode().equals(statusCode) && restoreToInProgress) {
                continue;
            }
            // 非待结算不可取消办理出院（已结算须反结）
            if (!EncounterStatus.PENDING.getCode().equals(statusCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "仅待结算状态的住院就诊可取消办理出院");
            }
            encounter.cancelNurseDischarge(restoreToInProgress);
            toSave.add(encounter);
        }
        if (!toSave.isEmpty()) {
            encounterRepository.saveAll(toSave);
            // 发布对称出院链路事件，载荷为取消后的状态（ON_HOLD / IN_PROGRESS）
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
     * 住院出院财务全结：待结算 → 已结算或直接离院（DM-ADM-002 §6.1.23）。
     *
     * <p>仅适用于 {@code IMP}；前置态须为 {@code PENDING}。中途结算不得调用本动作。
     * 有活动床时落到 {@code COMPLETED}（仍须清床）；无活动床（召回未占床轨）直接
     * {@code DISCHARGED} 并收口活动位置，不再走清床。
     * 无床直接离院时 {@code transitToDischarged} <strong>不覆盖</strong>已有 {@code end_time}
     *（业务出院时间仍为办理出院写入的计划出院时间；仅 end 为空时才回落结算时刻）。</p>
     *
     * @param command 命令
     */
    public void completeInpatientFinancialSettlement(CompleteInpatientFinancialSettlementCommand command) {
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "住院就诊主键不能为空");
        }
        Encounter encounter = this.requireEncounterForUpdate(
            command.getEncounterId(), EncounterClass.IMP.getCode());
        if (!EncounterStatus.PENDING.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅待结算状态的住院就诊可办理出院财务全结");
        }
        LocalDateTime settlementTime = command.getSettlementTime();
        if (settlementTime == null) {
            settlementTime = LocalDateTime.now();
        }
        List<EncounterLocation> locationsToSave = new ArrayList<>();
        // 有活动床：财务终态 COMPLETED，人仍占床，须护士清床后离院。
        if (this.hasActiveBed(command.getEncounterId())) {
            encounter.transitToCompleted();
        } else {
            // 无活动床：召回未占床患者二次结算后直接离院，收口活动 WARD 等位置。
            List<EncounterLocation> activeLocations = locationRepository
                .findAllByEncounterIdAndStatusEnum(
                    command.getEncounterId(), EncounterActivityStatus.ACTIVE.getCode());
            for (EncounterLocation old : activeLocations) {
                old.markCompleted(settlementTime);
                locationsToSave.add(old);
            }
            encounter.transitToDischarged(settlementTime);
        }
        if (!locationsToSave.isEmpty()) {
            locationRepository.saveAll(locationsToSave);
        }
        encounterRepository.save(encounter);
        InpatientFinancialSettlementCompletedEvent event = new InpatientFinancialSettlementCompletedEvent(
            encounter.getId(), encounter.getPatientId());
        event.setStatusEnum(encounter.getStatusEnum());
        event.setSettlementTime(settlementTime);
        domainEventPublisher.publish(event);
    }

    /**
     * 住院取消出院全结时回退就诊临床态：COMPLETED → IN_PROGRESS（AS-IP-FIN-004 反结；DM-ADM-002 §6.1.25 未清床轨）。
     *
     * <p>出院嘱退回草稿后须重新校对/办理出院，不得停留在 {@code PENDING} 待结算窗口。</p>
     *
     * @param encounterId 住院就诊主键
     */
    public void revertInpatientFinancialSettlement(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "住院就诊主键不能为空");
        }
        Encounter encounter = this.requireEncounterForUpdate(encounterId, EncounterClass.IMP.getCode());
        if (!EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())) {
            return;
        }
        // Cmd：反结后恢复已入科，清空出院时间/离院方式；活动床/参与者轨迹保持不变。
        encounter.recallFromCompletedToInProgress();
        encounterRepository.save(encounter);
    }

    /**
     * 出院召回（DM-ADM-002 §6.1.25，AS-IP-FIN-004 召回写）。
     *
     * <p>仅 {@code DISCHARGED→IN_PROGRESS} + INSERT 活动 {@code WARD}（不 INSERT 活动床、不占床）。
     * 反结与撤销出院嘱由应用层收银台召回编排先行完成；本方法悲观行锁后迁移就诊主档与病区轨迹。</p>
     *
     * @param command 召回命令
     */
    public void recallInpatientDischarge(RecallInpatientDischargeCommand command) {
        List<RecallInpatientDischargeCommand.RecallInpatientDischargeItemCommand> items = command.resolveItems();
        List<RecallInpatientDischargeCommand.RecallInpatientDischargeItemCommand> sortedItems =
            new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            RecallInpatientDischargeCommand.RecallInpatientDischargeItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        Map<Long, Encounter> encounterMap = new LinkedHashMap<>();
        List<Encounter> encountersToSave = new ArrayList<>();
        List<EncounterLocation> locationsToSave = new ArrayList<>();
        List<InpatientDischargeRecalledEvent> events = new ArrayList<>();
        for (RecallInpatientDischargeCommand.RecallInpatientDischargeItemCommand item : sortedItems) {
            Encounter encounter = this.requireEncounterForUpdate(
                item.getEncounterId(), EncounterClass.IMP.getCode());
            encounterMap.put(encounter.getId(), encounter);
            encounter.assertClass(EncounterClass.IMP.getCode());
            String previousStatus = encounter.getStatusEnum();
            LocalDateTime recalledAt;
            if (item.getRecalledAt() == null) {
                recalledAt = LocalDateTime.now();
            } else {
                recalledAt = item.getRecalledAt();
            }
            InpatientDischargeRecalledEvent event = new InpatientDischargeRecalledEvent(
                encounter.getId(), encounter.getPatientId());
            event.setPreviousStatusEnum(previousStatus);
            if (EncounterStatus.DISCHARGED.getCode().equals(previousStatus)) {
                if (item.getWardLocationId() == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                        "已清床出院召回须指定末次已完成病区");
                }
                // Cmd：离院 → 已入科；INSERT 活动病区，不新增活动床。
                encounter.recallFromDischargedToInProgress();
                this.upsertActiveWard(item.getEncounterId(), item.getWardLocationId(), recalledAt, locationsToSave);
                event.setStatusEnum(encounter.getStatusEnum());
                event.setRequiresBedAssignment(Boolean.FALSE);
                event.setWardLocationId(item.getWardLocationId());
            } else {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "仅已清床离院状态允许出院召回");
            }
            encountersToSave.add(encounter);
            events.add(event);
        }
        // 持久化：活动病区轨迹须先于就诊主档落库。
        if (!locationsToSave.isEmpty()) {
            locationRepository.saveAll(locationsToSave);
        }
        // 持久化：就诊状态回退与出院时间/离院方式清空。
        if (!encountersToSave.isEmpty()) {
            encounterRepository.saveAll(encountersToSave);
        }
        // 发布事件：投影刷新护士站列表与医嘱校对态。
        for (InpatientDischargeRecalledEvent event : events) {
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 清床离院（{@code COMPLETED} → {@code DISCHARGED}，GAP-ATD-005）。
     *
     * <p>收口活动位置/参与者并迁移主档终态；{@code end_time} 若已由护士办理出院写入则保留
     *（业务出院时间=出院医嘱计划时间），清床时刻仅作空 end 时的回落。</p>
     *
     * @param command 命令
     */
    public void clearDischargedBed(ClearDischargedBedCommand command) {
        List<ClearDischargedBedCommand.ClearDischargedBedItemCommand> items = command.resolveItems();
        List<ClearDischargedBedCommand.ClearDischargedBedItemCommand> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(
            ClearDischargedBedCommand.ClearDischargedBedItemCommand::getEncounterId,
            Comparator.nullsLast(Comparator.naturalOrder())));
        Map<Long, Encounter> encounterMap = new LinkedHashMap<>();
        List<EncounterLocation> toSave = new ArrayList<>();
        List<EncounterParticipant> participants = new ArrayList<>();
        List<Encounter> encountersToSave = new ArrayList<>();
        for (ClearDischargedBedCommand.ClearDischargedBedItemCommand item : sortedItems) {
            Encounter encounter = this.requireEncounterForUpdate(
                item.getEncounterId(), EncounterClass.IMP.getCode());
            encounterMap.put(encounter.getId(), encounter);
            encounter.assertClearBedAllowed();
            LocalDateTime clearedAt;
            if (item.getClearedAt() == null) {
                clearedAt = LocalDateTime.now();
            } else {
                clearedAt = item.getClearedAt();
            }
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
            LocalDateTime clearedAt;
            if (item.getClearedAt() == null) {
                clearedAt = LocalDateTime.now();
            } else {
                clearedAt = item.getClearedAt();
            }
            domainEventPublisher.publish(new TerminalCleaningCompletedEvent(
                item.getEncounterId(), item.getBedId(), clearedAt));
        }
    }

    /**
     * 住院护士站保存患者信息（AS-IP-NUR-001 IF-IP-NUR-04）：更新 {@code priority_enum}；
     * 对提交各 {@code roleCode} 活动参与者 COMPLETED 后 INSERT 新 ACTIVE（禁止 UPDATE）；禁止改床与 {@code status_enum}。
     *
     * @param command 保存命令
     * @return 首条就诊标识
     */
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
            if (StringUtils.isNotEmpty(item.getPriorityEnum())
                && !Objects.equals(item.getPriorityEnum(), encounter.getPriorityEnum())) {
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
                    if (row == null || StringUtils.isEmpty(row.getRoleCode()) || row.getPractitionerId() == null) {
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
     * 医生站撤回住院申请：直接软删待登记（{@code IMP+PLANNED}）住院线，不迁取消态。
     *
     * <p>清空 {@code ambEncounterId} 后软删头表，款台登记队列与医生站防重均不再命中；
     * 医生可再次「办理住院」提交新申请。</p>
     *
     * @param encounterId 待撤回的住院就诊主键
     * @param operatorId  操作人主键；可空
     * @throws BusinessException 就诊不存在、非住院或非待登记态
     */
    public void softDeleteWithdrawnInpatientAdmission(Long encounterId, Long operatorId) {
        // Gate：撤回软删必须指定住院就诊
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊不存在"));
        // Gate：仅待登记住院线可撤回软删，避免误删已登记/在院线
        encounter.assertClass(EncounterClass.IMP.getCode());
        if (!EncounterStatus.PLANNED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅待登记的住院申请可撤回");
        }
        // 已软删：幂等返回，避免重复写
        if (Boolean.TRUE.equals(encounter.getIsDeleted())) {
            return;
        }
        // Cmd：解除门诊锚点并软删；不走 CANCELLED，款台列表靠 deletedFilter 消失
        encounter.setAmbEncounterId(null);
        encounter.setIsDeleted(Boolean.TRUE);
        encounter.setDeletedAt(LocalDateTime.now());
        if (operatorId != null) {
            encounter.setDeletedBy(operatorId);
        }
        encounterRepository.save(encounter);
    }

    /**
     * 断言同日、同目标科室门诊防重：凡已产生占名额就诊事实（含待挂号 {@link EncounterStatus#PLANNED} 及诊毕）均阻断，除非 {@link EncounterStatus#CANCELLED} 退号或撤销。
     *
     * <p>门诊类别限定为 {@link EncounterClass#AMB}。</p>
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
        // 待挂号 PLANNED 与已挂号/诊毕等同样占名额，仅 CANCELLED 退号或撤销后释放。
        List<String> blockingStatuses = EncounterStatus.outpatientDuplicateBlockingStatusCodes();
        List<String> outpatientClasses = Collections.singletonList(EncounterClass.AMB.getCode());
        Specification<Encounter> specification = buildPaidOutpatientDaySpec(
            patientId,
            organizationId,
            outpatientClasses,
            blockingStatuses,
            dayStart,
            dayEndExclusive);
        if (encounterRepository.count(specification) > 0) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                EncounterConstants.OUTPATIENT_DUPLICATE_REGISTRATION_MESSAGE);
        }
    }

    /**
     * 构造「同日同科室已占用门诊号源」的 JPA 规约（供 count 防重，B.2 禁仓储 @Query）。
     */
    private static Specification<Encounter> buildPaidOutpatientDaySpec(
        Long patientId,
        Long organizationId,
        Collection<String> outpatientClassEnums,
        Collection<String> paidStatusEnums,
        LocalDateTime dayStartInclusive,
        LocalDateTime dayEndExclusive) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId),
            root.get(FieldNameConstants.CLASS_ENUM).in(outpatientClassEnums),
            root.get(FieldNameConstants.STATUS_ENUM).in(paidStatusEnums),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.greaterThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), dayStartInclusive),
            cb.lessThan(root.get(FieldNameConstants.CREATED_AT), dayEndExclusive));
    }

    /**
     * 断言设置主诊断时不与已有其他活动主诊断冲突（BR-ENC-014）。
     *
     * <p>仅认未软删行；已软删主诊不占名额（医保→国临展开等同事务先删后插）。</p>
     *
     * @param encounterId 就诊 ID
     * @param encounterDiagnosisId 本次拟设为主诊的就诊诊断行主键；新建时为 null
     */
    private void assertNoPrimaryDiagnosisConflict(Long encounterId, Long encounterDiagnosisId) {
        Optional<EncounterDiagnosis> existingOpt =
            diagnosisRepository.findFirstByEncounterIdAndPrimaryFlagTrueAndIsDeletedFalse(encounterId);
        // 无活动主诊则可设
        if (existingOpt.isEmpty()) {
            return;
        }
        EncounterDiagnosis existing = existingOpt.get();
        // 防御：仓储已排软删，若会话仍带回已删实体则不计冲突
        if (Boolean.TRUE.equals(existing.getIsDeleted())) {
            return;
        }
        // 更新同一主诊行时不构成冲突
        if (encounterDiagnosisId != null && Objects.equals(existing.getId(), encounterDiagnosisId)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
            "该就诊已存在主诊断（encounterDiagnosisId=" + existing.getId() + "），无法重复设置");
    }

    /**
     * 断言存在未软删的主诊断关联（BR-ENC-006）。
     *
     * @param encounterId 就诊 ID
     */
    private void assertPrimaryDiagnosisExists(Long encounterId) {
        boolean exists = diagnosisRepository
            .findFirstByEncounterIdAndPrimaryFlagTrueAndIsDeletedFalse(encounterId).isPresent();
        // 就诊尚无活动主诊断时拒绝完诊/出院，防止无诊断结算
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
        // 无活动接诊医生时拒绝完诊，与 BR-ENC-004 一致
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
            // 主诊断计数：同批次仅允许一个 primary=true
            if (Boolean.TRUE.equals(item.getPrimaryFlag())) {
                Integer count = primaryCountMap.getOrDefault(item.getEncounterId(), 0);
                primaryCountMap.put(item.getEncounterId(), count + 1);
            }
            // 排序号在同就诊内须唯一，供列表展示顺序稳定
            if (item.getRank() != null) {
                String key = item.getEncounterId() + "\u0001" + item.getRank();
                // 重复排序号拒绝写入，避免诊断列表顺序歧义
                if (rankMap.containsKey(key)) {
                    throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊诊断排序号不能重复");
                }
                rankMap.put(key, Boolean.TRUE);
            }
        }
        for (Map.Entry<Long, Integer> entry : primaryCountMap.entrySet()) {
            // 同就诊出现多个主诊断时拒绝，满足 BR-ENC-014
            if (entry.getValue() > 1) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊主诊断必须唯一");
            }
        }
    }

    /**
     * 校验批量诊断集合约束（西医等：一行一 rank）。
     */
    private void assertDiagnosisCollectionRuleByBatchItem(BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand item) {
        int primaryCount = 0;
        Set<Integer> ranks = new HashSet<>();
        for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
            // 统计本批次主诊断个数，用于唯一性校验
            if (Boolean.TRUE.equals(diagnosisItem.getPrimaryFlag())) {
                primaryCount++;
            }
            // 西医批次排序号不得重复
            if (diagnosisItem.getRank() != null && !ranks.add(diagnosisItem.getRank())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊诊断排序号不能重复");
            }
        }
        // 批次内主诊断超过一个时拒绝
        if (primaryCount > 1) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊主诊断必须唯一");
        }
    }

    /**
     * 校验中医病证组批次：主诊与排序按 {@code tcm_syndrome_group} 组级唯一。
     * <p>同组内病名+证候两行共用组级 rank/primary，不得按关联行重复计数。</p>
     */
    private void assertTcmDiagnosisCollectionRuleByBatchItem(
        BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand item) {
        int primaryGroupCount = 0;
        Set<Integer> groupRanks = new HashSet<>();
        Set<String> processedGroups = new HashSet<>();
        for (BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand diagnosisItem : item.getDiagnosisItems()) {
            // 组键：有 tcm_syndrome_group 则按组计数；无组号退化为单行目录定义维度
            String groupKey = trimToNull(diagnosisItem.getTcmSyndromeGroup());
            if (groupKey == null) {
                groupKey = "def:" + diagnosisItem.getConditionDefinitionId();
            }
            // 同组第二行（证候）不再重复计数主诊与 rank
            if (!processedGroups.add(groupKey)) {
                continue;
            }
            if (Boolean.TRUE.equals(diagnosisItem.getPrimaryFlag())) {
                primaryGroupCount++;
            }
            if (diagnosisItem.getRank() != null && !groupRanks.add(diagnosisItem.getRank())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊诊断排序号不能重复");
            }
        }
        // 主诊在组级唯一：多组同时标主诊则拒绝
        if (primaryGroupCount > 1) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "同一就诊主诊断必须唯一");
        }
    }

    /**
     * 本批次重新纳入的就诊诊断关联：清除软删标记，避免仍按已删行参与 rank 冲突或列表过滤。
     */
    private void reactivateEncounterDiagnosis(EncounterDiagnosis diagnosis) {
        if (diagnosis == null) {
            return;
        }
        // 本批次重新纳入已软删关联：清除删标记，避免列表过滤与 rank 冲突误判
        diagnosis.setIsDeleted(Boolean.FALSE);
        diagnosis.setDeletedAt(null);
        diagnosis.setDeletedBy(null);
    }

    /**
     * 就诊下存量诊断行索引：按行主键、西医目录定义键、中医病证组复合键三类映射。
     */
    private static final class EncounterDiagnosisIndex {
        private final Map<Long, EncounterDiagnosis> byId = new HashMap<>();
        private final Map<String, EncounterDiagnosis> westernByDefinitionKey = new HashMap<>();
        private final Map<String, EncounterDiagnosis> tcmByMatchKey = new HashMap<>();

        private static EncounterDiagnosisIndex fromRows(List<EncounterDiagnosis> rows) {
            EncounterDiagnosisIndex index = new EncounterDiagnosisIndex();
            if (rows == null) {
                return index;
            }
            // Loop：为匹配 upsert 项建立多键索引
            for (EncounterDiagnosis row : rows) {
                if (row == null || row.getId() == null) {
                    continue;
                }
                index.byId.put(row.getId(), row);
                if (Boolean.TRUE.equals(row.getTcmFlag())) {
                    String tcmKey = buildTcmMatchKey(row.getConditionDefinitionId(), row.getTcmSyndromeGroup());
                    index.tcmByMatchKey.put(tcmKey, row);
                } else if (row.getConditionDefinitionId() != null) {
                    index.westernByDefinitionKey.put(buildWesternDefinitionKey(row.getConditionDefinitionId()), row);
                }
            }
            return index;
        }
    }

    private static String buildWesternDefinitionKey(Long conditionDefinitionId) {
        return "def:" + conditionDefinitionId;
    }

    private static String buildTcmMatchKey(Long conditionDefinitionId, String tcmSyndromeGroup) {
        String group = trimToNull(tcmSyndromeGroup);
        if (group == null) {
            return "tcm-def:" + conditionDefinitionId;
        }
        return "tcm:" + conditionDefinitionId + "\u0001" + group;
    }

    /**
     * 单条 upsert 命令匹配存量就诊诊断行：优先行主键，否则按就诊+目录定义。
     *
     * @param item upsert 命令项
     * @return 存量行；不存在时 null
     */
    private EncounterDiagnosis resolveUpsertEncounterDiagnosis(
        UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand item) {
        if (item.getEncounterDiagnosisId() != null) {
            Optional<EncounterDiagnosis> byId = diagnosisRepository.findById(item.getEncounterDiagnosisId());
            if (byId.isPresent()) {
                EncounterDiagnosis row = byId.get();
                // 行主键须归属本次就诊，防止跨就诊误写
                if (!Objects.equals(row.getEncounterId(), item.getEncounterId())) {
                    throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊诊断行与就诊不匹配");
                }
                return row;
            }
        }
        if (item.getConditionDefinitionId() != null) {
            return diagnosisRepository
                .findByEncounterIdAndConditionDefinitionId(item.getEncounterId(), item.getConditionDefinitionId())
                .orElse(null);
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "维护就诊诊断须指定行主键或诊断目录");
    }

    /**
     * 将单条 upsert 命令字段写入就诊诊断实体。
     */
    private void applyUpsertItemToEncounterDiagnosis(
        EncounterDiagnosis diagnosis,
        UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand item) {
        diagnosis.setEncounterId(item.getEncounterId());
        if (item.getConditionDefinitionId() != null) {
            diagnosis.setConditionDefinitionId(item.getConditionDefinitionId());
        }
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
    }

    /**
     * 西医全量批次匹配存量行：优先 encounterDiagnosisId，否则按目录定义键。
     */
    private EncounterDiagnosis resolveWesternBatchDiagnosis(
        EncounterDiagnosisIndex index,
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand item) {
        if (item.getEncounterDiagnosisId() != null) {
            return index.byId.get(item.getEncounterDiagnosisId());
        }
        if (item.getConditionDefinitionId() != null) {
            return index.westernByDefinitionKey.get(buildWesternDefinitionKey(item.getConditionDefinitionId()));
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "西医诊断批次项须指定行主键或诊断目录");
    }

    /**
     * 中医全量批次匹配存量行：优先 encounterDiagnosisId，否则按目录定义+病证组键。
     */
    private EncounterDiagnosis resolveTcmBatchDiagnosis(
        EncounterDiagnosisIndex index,
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand item) {
        if (item.getEncounterDiagnosisId() != null) {
            return index.byId.get(item.getEncounterDiagnosisId());
        }
        if (item.getConditionDefinitionId() != null) {
            return index.tcmByMatchKey.get(this.buildTcmMatchKey(item.getConditionDefinitionId(), item.getTcmSyndromeGroup()));
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "中医诊断批次项须指定行主键或诊断目录");
    }

    /**
     * 将西医批次项写入就诊诊断实体（强制非中医行）。
     */
    private void applyWesternBatchItemToEncounterDiagnosis(
        EncounterDiagnosis diagnosis,
        Long encounterId,
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand item) {
        diagnosis.setEncounterId(encounterId);
        diagnosis.setConditionDefinitionId(item.getConditionDefinitionId());
        diagnosis.setPrimaryFlag(item.getPrimaryFlag());
        diagnosis.setRank(item.getRank());
        diagnosis.setDescription(item.getDescription());
        diagnosis.setDiagTypeCode(item.getDiagTypeCode());
        diagnosis.setDiagCode(item.getDiagCode());
        diagnosis.setConditionCode(item.getConditionCode());
        diagnosis.setMedType(item.getMedType());
        diagnosis.setHighestDiagFlag(item.getHighestDiagFlag());
        // 西医批次写入行强制非中医，避免误清空后又被当作西医软删。
        diagnosis.setTcmSyndromeGroup(null);
        diagnosis.setTcmFlag(Boolean.FALSE);
    }

    /**
     * 将中医批次项写入就诊诊断实体（强制 tcm_flag=true）。
     */
    private void applyTcmBatchItemToEncounterDiagnosis(
        EncounterDiagnosis diagnosis,
        Long encounterId,
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand item) {
        diagnosis.setEncounterId(encounterId);
        diagnosis.setConditionDefinitionId(item.getConditionDefinitionId());
        diagnosis.setPrimaryFlag(item.getPrimaryFlag());
        diagnosis.setRank(item.getRank());
        diagnosis.setDescription(item.getDescription());
        diagnosis.setDiagTypeCode(item.getDiagTypeCode());
        diagnosis.setDiagCode(item.getDiagCode());
        diagnosis.setConditionCode(item.getConditionCode());
        diagnosis.setMedType(item.getMedType());
        diagnosis.setHighestDiagFlag(item.getHighestDiagFlag());
        diagnosis.setTcmSyndromeGroup(item.getTcmSyndromeGroup());
        // Cmd：中医批次强制 tcm_flag=true，与 AS-OP-DOR-010 口径一致。
        diagnosis.setTcmFlag(Boolean.TRUE);
    }

    /**
     * 判断西医存量行是否仍在本批次活动集合内（按行主键或目录定义键）。
     */
    private boolean isWesternDiagnosisStillActive(
        EncounterDiagnosis old,
        Set<Long> activeEncounterDiagnosisIds,
        Set<Long> activeWesternDefinitionIds) {
        if (old.getId() != null && activeEncounterDiagnosisIds.contains(old.getId())) {
            return true;
        }
        return old.getConditionDefinitionId() != null
            && activeWesternDefinitionIds.contains(old.getConditionDefinitionId());
    }

    /**
     * 判断中医存量行是否仍在本批次活动集合内（按行主键或病证组复合键）。
     */
    private boolean isTcmDiagnosisStillActive(
        EncounterDiagnosis old,
        Set<Long> activeEncounterDiagnosisIds,
        Set<String> activeTcmMatchKeys) {
        if (old.getId() != null && activeEncounterDiagnosisIds.contains(old.getId())) {
            return true;
        }
        return activeTcmMatchKeys.contains(this.buildTcmMatchKey(old.getConditionDefinitionId(), old.getTcmSyndromeGroup()));
    }

    /**
     * 移除命令加载未软删且归属一致的就诊诊断行。
     */
    private EncounterDiagnosis mustGetActiveEncounterDiagnosisForRemove(
        RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item) {
        if (item.getEncounterDiagnosisId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "移除就诊诊断须指定行主键");
        }
        EncounterDiagnosis diagnosis = diagnosisRepository.findById(item.getEncounterDiagnosisId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "诊断关联不存在"));
        // 行须归属本次就诊且未软删
        if (!Objects.equals(diagnosis.getEncounterId(), item.getEncounterId())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊诊断行与就诊不匹配");
        }
        if (Boolean.TRUE.equals(diagnosis.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "诊断关联不存在");
        }
        return diagnosis;
    }

    /**
     * 西医批次落库后解析待发布事件的实体：优先命令行主键，否则目录定义键在 savedById/index 中反查。
     */
    private EncounterDiagnosis resolvePublishedWesternDiagnosis(
        EncounterDiagnosisIndex index,
        Map<Long, EncounterDiagnosis> savedById,
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand item,
        Long encounterId) {
        if (item.getEncounterDiagnosisId() != null) {
            EncounterDiagnosis byCommandId = savedById.get(item.getEncounterDiagnosisId());
            if (byCommandId != null) {
                return byCommandId;
            }
            return index.byId.get(item.getEncounterDiagnosisId());
        }
        if (item.getConditionDefinitionId() == null) {
            return null;
        }
        EncounterDiagnosis matched = index.westernByDefinitionKey.get(buildWesternDefinitionKey(item.getConditionDefinitionId()));
        if (matched != null && matched.getId() != null) {
            EncounterDiagnosis fromSave = savedById.get(matched.getId());
            if (fromSave != null) {
                return fromSave;
            }
            return matched;
        }
        for (EncounterDiagnosis row : savedById.values()) {
            if (Objects.equals(row.getEncounterId(), encounterId)
                && Objects.equals(row.getConditionDefinitionId(), item.getConditionDefinitionId())
                && !Boolean.TRUE.equals(row.getTcmFlag())) {
                return row;
            }
        }
        return null;
    }

    /**
     * 中医批次落库后解析待发布事件的实体。
     */
    private EncounterDiagnosis resolvePublishedTcmDiagnosis(
        EncounterDiagnosisIndex index,
        Map<Long, EncounterDiagnosis> savedById,
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand item,
        Long encounterId) {
        if (item.getEncounterDiagnosisId() != null) {
            EncounterDiagnosis byCommandId = savedById.get(item.getEncounterDiagnosisId());
            if (byCommandId != null) {
                return byCommandId;
            }
            return index.byId.get(item.getEncounterDiagnosisId());
        }
        if (item.getConditionDefinitionId() == null) {
            return null;
        }
        String matchKey = this.buildTcmMatchKey(item.getConditionDefinitionId(), item.getTcmSyndromeGroup());
        EncounterDiagnosis matched = index.tcmByMatchKey.get(matchKey);
        if (matched != null && matched.getId() != null) {
            EncounterDiagnosis fromSave = savedById.get(matched.getId());
            if (fromSave != null) {
                return fromSave;
            }
            return matched;
        }
        for (EncounterDiagnosis row : savedById.values()) {
            if (!Objects.equals(row.getEncounterId(), encounterId) || !Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            if (Objects.equals(row.getConditionDefinitionId(), item.getConditionDefinitionId())
                && Objects.equals(trimToNull(row.getTcmSyndromeGroup()), trimToNull(item.getTcmSyndromeGroup()))) {
                return row;
            }
        }
        return null;
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
     * 写侧校验：源门诊就诊可发起住院申请。
     *
     * @param ambEncounterId 源就诊标识
     */
    private void assertAmbForInpatientWrite(Long ambEncounterId) {
        Encounter amb = encounterRepository.findById(ambEncounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "源就诊不存在"));
        String classCode = amb.getClassEnum();
        if (!EncounterClass.AMB.getCode().equals(classCode)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅门诊就诊可发起住院申请");
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
        // 批量映射中无此就诊，说明主键无效或已删除
        if (encounter == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在");
        }
        return encounter;
    }

    /**
     * 按主键与类别悲观写锁加载就诊（住院登记、出院/清床/召回等写路径硬门禁）。
     *
     * @param encounterId 就诊主键
     * @param classEnum   就诊类别 code
     * @return 就诊聚合根
     */
    private Encounter requireEncounterForUpdate(Long encounterId, String classEnum) {
        Encounter encounter = encounterRepository
            .findByIdAndClassEnumAndIsDeletedFalse(encounterId, classEnum)
            .orElse(null);
        if (encounter == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在");
        }
        return encounter;
    }

    /**
     * 判断就诊是否已存在活动床位级位置（用于取消登记等门禁）。
     *
     * @param encounterId 就诊主键
     * @return 存在活动 {@code BED} 形态位置时 true
     */
    /**
     * 住院护士站保存患者信息门禁：仅 {@code IMP} 且状态为 {@code REGISTERED} 或 {@code IN_PROGRESS}。
     *
     * @param encounter 住院就诊聚合根
     * @throws BusinessException 类别或状态不允许保存时
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
        List<EncounterLocation> activeLocations = locationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        Long matchedLocationId = null;
        for (EncounterLocation location : activeLocations) {
            if (location == null || !LocationForm.BED.getCode().equals(location.getFormEnum())) {
                continue;
            }
            if (matchedLocationId != null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                    "存在多条活动床位位置");
            }
            matchedLocationId = location.getLocationId();
        }
        if (matchedLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊无活动床位");
        }
        return matchedLocationId;
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
            if (row == null || StringUtils.isEmpty(row.getRoleCode()) || row.getPractitionerId() == null) {
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

    /**
     * 转科撤销：按最近一次收口批次 INSERT 活动参与者（对齐 {@code initiateDepartmentTransfer} 收口逆操作）。
     */
    private void restoreParticipantsFromLatestCompletedBatch(
        Long encounterId,
        LocalDateTime revertedAt,
        List<EncounterParticipant> sink) {
        List<EncounterParticipant> completed = participantRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.COMPLETED.getCode());
        if (completed.isEmpty()) {
            return;
        }
        LocalDateTime latestEnd = null;
        for (EncounterParticipant row : completed) {
            if (row.getEndTime() == null) {
                continue;
            }
            if (latestEnd == null || row.getEndTime().isAfter(latestEnd)) {
                latestEnd = row.getEndTime();
            }
        }
        if (latestEnd == null) {
            return;
        }
        for (EncounterParticipant old : completed) {
            if (!latestEnd.equals(old.getEndTime())) {
                continue;
            }
            EncounterParticipant participant = new EncounterParticipant();
            participant.setEncounterId(encounterId);
            participant.setRoleCode(old.getRoleCode());
            participant.setPractitionerId(old.getPractitionerId());
            participant.markActive(revertedAt);
            sink.add(participant);
        }
    }

}
