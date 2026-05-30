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

package com.openhis.health.domain.medication.medicationdispense.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.medication.medicationdispense.command.*;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.repository.MedicationDispenseRepository;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.repository.MedicationRequestRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.enums.MedicationSummaryStatus;
import com.openhis.health.domain.share.events.medication.medicationdispense.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * 药品发放写侧领域服务。
 *
 * <p>事件发布须严格晚于同批次 {@code save}/{@code saveAll}（《代码生成通用规范》B.14），避免幽灵事件。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicationDispenseDomainService {

    private final MedicationDispenseRepository medicationDispenseRepository;
    private final MedicationRequestRepository medicationRequestRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 药品发放 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#MEDICATION_DISPENSE}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 执行配药并推进到已配药状态。
     *
     * @param command 配药命令
     */
    public void prepareMedicationDispense(PrepareMedicationDispenseCommand command) {
        List<MedicationDispense> toSave = new ArrayList<>();
        for (PrepareMedicationDispenseCommand.Item item : command.resolveItems()) {
            MedicationDispense dispense = this.mustGet(item.getId());
            dispense.ensureStatusForPrepare();
            // Pre：回写追溯与批号；追溯码在门诊路径可为空，发药扣库前再按监管口径补强。
            dispense.setTraceNo(item.getTraceNo());
            dispense.setLotNumber(item.getLotNumber());
            if (item.getLocationId() != null) {
                dispense.setLocationId(item.getLocationId());
            }
            if (item.getQuantity() != null) {
                dispense.setQuantity(item.getQuantity());
            }
            dispense.toPrepared(item.getPreparerId());
            toSave.add(dispense);
        }
        medicationDispenseRepository.saveAll(toSave);
        for (MedicationDispense dispense : toSave) {
            MedicationDispensePreparedEvent preparedEvent = new MedicationDispensePreparedEvent(
                dispense.getId(), dispense.getPreparerId(), dispense.getPrepareTime(), command.getOperatorId());
            preparedEvent.setStatusEnumCode(dispense.getStatusEnum().getCode());
            preparedEvent.setLotNumber(dispense.getLotNumber());
            preparedEvent.setTraceNo(dispense.getTraceNo());
            domainEventPublisher.publish(preparedEvent);
        }
    }

    /**
     * 执行发药并推进到已发放状态。
     *
     * @param command 发药命令
     */
    public void dispenseMedication(DispenseMedicationCommand command) {
        List<MedicationDispense> toSave = new ArrayList<>();
        for (DispenseMedicationCommand.Item item : command.resolveItems()) {
            MedicationDispense dispense = this.mustGet(item.getId());
            dispense.ensureStatusForDispense();
            dispense.validateInventoryTuple();
            dispense.toCompleted(item.getPractitionerId(), item.getDispenseQuantity());
            toSave.add(dispense);
        }
        medicationDispenseRepository.saveAll(toSave);
        for (MedicationDispense dispense : toSave) {
            MedicationDispensedEvent dispensedEvent = new MedicationDispensedEvent(
                dispense.getId(), dispense.getMedicationId(), dispense.getLotNumber(), dispense.getDispenseQuantity(), command.getOperatorId());
            dispensedEvent.setStatusEnumCode(dispense.getStatusEnum().getCode());
            dispensedEvent.setDispenserId(dispense.getPractitionerId());
            dispensedEvent.setDispenseTime(dispense.getDispenseTime());
            domainEventPublisher.publish(dispensedEvent);
        }
    }

    /**
     * 作废未发发放单。
     *
     * @param command 作废命令
     */
    public void cancelMedicationDispense(CancelMedicationDispenseCommand command) {
        List<MedicationDispense> toSave = new ArrayList<>();
        for (CancelMedicationDispenseCommand.Item item : command.resolveItems()) {
            // Pre：作废未发时记录拒绝原因，便于后续审计。
            MedicationDispense dispense = this.mustGet(item.getId());
            dispense.ensureCancellable();
            dispense.toDeclined(item.getNotPerformedReasonEnum());
            toSave.add(dispense);
        }
        medicationDispenseRepository.saveAll(toSave);
        for (MedicationDispense dispense : toSave) {
            MedicationDispenseDeclinedEvent declinedEvent = new MedicationDispenseDeclinedEvent(
                dispense.getId(), dispense.getNotPerformedReasonEnum(), command.getOperatorId());
            declinedEvent.setStatusEnumCode(dispense.getStatusEnum().getCode());
            domainEventPublisher.publish(declinedEvent);
        }
    }

    /**
     * 执行退药并推进到已退药状态。
     *
     * @param command 退药命令
     */
    public void refundDispensedMedication(RefundDispensedMedicationCommand command) {
        Map<Long, MedicationDispense> toSaveById = new LinkedHashMap<>();
        for (RefundDispensedMedicationCommand.Item item : command.resolveItems()) {
            MedicationDispense dispense = this.mustGet(item.getId());
            dispense.ensureRefundable();
            // Pre：待退药确认路径须回写药师与可选原因，已发药直退路径保持原 toRefunded 语义。
            if (DispenseStatus.PENDING_REFUND.equals(dispense.getStatusEnum())) {
                Long practitionerId = item.getPractitionerId();
                if (practitionerId == null) {
                    practitionerId = item.getOperatorId();
                }
                dispense.pendingRefundToRefundedWithAudit(item.getRefundQuantity(), practitionerId, item.getReasonText());
                // Pre：门诊退药申请会新建 REFUND 类型待退行，原正向 COMPLETED 行须同步收口，否则已发药列表仍命中。
                MedicationDispense originForward = this.findOriginForwardDispenseForRefundLine(dispense);
                if (originForward != null) {
                    originForward.toRefunded();
                    toSaveById.put(originForward.getId(), originForward);
                }
            } else {
                // Pre：已发药态退库仍沿用原数量回写与状态推进。
                dispense.toRefunded(item.getRefundQuantity());
            }
            toSaveById.put(dispense.getId(), dispense);
        }
        List<MedicationDispense> toSave = new ArrayList<>(toSaveById.values());
        medicationDispenseRepository.saveAll(toSave);
        for (MedicationDispense dispense : toSave) {
            MedicationDispenseRefundedEvent refundedEvent =
                new MedicationDispenseRefundedEvent(dispense.getId(), dispense.getDispenseQuantity(), command.getOperatorId());
            refundedEvent.setStatusEnumCode(dispense.getStatusEnum().getCode());
            domainEventPublisher.publish(refundedEvent);
        }
    }

    /**
     * 门诊药房拒绝退药：待退药记录推进为已拒绝终态。
     *
     * @param command 拒绝命令
     */
    public void declinePendingRefundMedicationDispense(DeclinePendingRefundMedicationDispenseCommand command) {
        List<MedicationDispense> toSave = new ArrayList<>();
        for (DeclinePendingRefundMedicationDispenseCommand.Item item : command.resolveItems()) {
            MedicationDispense dispense = this.mustGet(item.getId());
            dispense.pendingRefundToDeclined(item.getDeclineReasonText(), item.getPractitionerId());
            toSave.add(dispense);
        }
        medicationDispenseRepository.saveAll(toSave);
        for (MedicationDispense dispense : toSave) {
            MedicationDispenseDeclinedEvent declinedEvent =
                new MedicationDispenseDeclinedEvent(dispense.getId(), dispense.getNotPerformedReasonEnum(), command.getOperatorId());
            declinedEvent.setStatusEnumCode(dispense.getStatusEnum().getCode());
            domainEventPublisher.publish(declinedEvent);
        }
    }

    /**
     * 由临时医嘱生成发放单草稿。
     *
     * @param command 临时路径生成命令
     * @return 新建发放单标识
     */
    public Long generateDispenseFromTemporaryOrder(GenerateDispenseFromTemporaryOrderCommand command) {
        if (command.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "临时医嘱发放预占 locationId 不能为空");
        }
        if (medicationDispenseRepository.existsByMedReqIdAndStatusEnumIn(
            command.getMedReqId(), List.of(DispenseStatus.DRAFT, DispenseStatus.PREPARATION))) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT, "同申请存在未完成发放单");
        }
        MedicationDispense dispense = this.buildFromGenerateInput(
            command.getMedReqId(), command.getMedicationId(), command.getEncounterId(), command.getPatientId(),
            command.getLocationId(), command.getQuantity(), command.getUnitCode(), command.getDispenseEnum());
        dispense.initDraft();
        MedicationDispense saved = medicationDispenseRepository.save(dispense);
        MedicationDispenseCreatedEvent event = new MedicationDispenseCreatedEvent(
            saved.getId(), saved.getMedReqId(), saved.getMedicationId(), saved.getEncounterId(), saved.getCreatedBy());
        event.setBusNo(saved.getBusNo());
        event.setStatusEnum(saved.getStatusEnum());
        event.setDispenseEnum(saved.getDispenseEnum());
        event.setPatientId(saved.getPatientId());
        event.setLocationId(saved.getLocationId());
        event.setQuantity(saved.getQuantity());
        event.setUnitCode(saved.getUnitCode());
        event.setLotNumber(saved.getLotNumber());
        domainEventPublisher.publish(event);
        return saved.getId();
    }

    /**
     * 门诊医生站签发后：按 FIFO 分段<strong>替换</strong>本医嘱下草稿/待配药发放事实（软删旧行后批量插入新草稿）。
     *
     * <p>若同医嘱已存在进入配药或发药闭环的发放行，拒绝覆盖以防药房事实与收费锚点不一致。</p>
     *
     * @param command 分段清单与医嘱上下文
     * @return 新建发放主键列表（与分段顺序一致）
     * @throws BusinessException 参数不完整、分段为空、存在不可覆盖状态的发放行或聚合持久化失败时
     */
    public List<Long> replaceFifoDraftDispensesAfterDoctorStationSign(ReplaceFifoMedicationDispenseDraftsCommand command) {
        List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments = command.resolveSegments();
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        if (command.getMedReqId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "medReqId 不能为空");
        }
        if (command.getMedicationId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "medicationId 不能为空");
        }
        if (command.getEncounterId() == null || command.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "encounterId/patientId 不能为空");
        }
        if (command.getUnitCode() == null || command.getUnitCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "unitCode 不能为空");
        }
        if (command.getDispenseEnum() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "dispenseEnum 不能为空");
        }
        if (segments.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "FIFO 分段不能为空");
        }
        List<MedicationDispense> siblings = medicationDispenseRepository.findByMedReqIdIn(List.of(command.getMedReqId()));
        List<MedicationDispense> released = new ArrayList<>();
        for (MedicationDispense existing : siblings) {
            if (!existing.isDraftOrPreparation()) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT, "医嘱已存在执行中的发放记录，禁止签发重置");
            }
            if (existing.isDraftOrPreparation()) {
                existing.markDeleted(command.getOperatorId());
                released.add(existing);
            }
        }
        medicationDispenseRepository.saveAll(released);
        if (!released.isEmpty()) {
            List<Long> releasedIds = released.stream().map(MedicationDispense::getId).collect(Collectors.toList());
            publishMedicationDispenseReserveReleased(command.getMedReqId(), releasedIds, command.getOperatorId());
        }
        List<String> busSeqList = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.MEDICATION_DISPENSE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, segments.size());
        List<MedicationDispense> created = new ArrayList<>();
        int seqIndex = 0;
        for (ReplaceFifoMedicationDispenseDraftsCommand.Segment segment : segments) {
            if (segment.getLocationId() == null) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "FIFO 分段 locationId 不能为空");
            }
            if (segment.getQuantity() == null || segment.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "FIFO 分段 quantity 必须大于 0");
            }
            MedicationDispense dispense = this.buildFromGenerateInput(
                command.getMedReqId(), command.getMedicationId(), command.getEncounterId(), command.getPatientId(),
                segment.getLocationId(), segment.getQuantity(), command.getUnitCode(), command.getDispenseEnum());
            dispense.setBusNo(busSeqList.get(seqIndex));
            seqIndex = seqIndex + 1;
            dispense.setLotNumber(segment.getLotNumber());
            dispense.initDraft();
            created.add(dispense);
        }
        List<MedicationDispense> savedRows = medicationDispenseRepository.saveAll(created);
        List<Long> createdIds = new ArrayList<>();
        for (MedicationDispense saved : savedRows) {
            createdIds.add(saved.getId());
            MedicationDispenseCreatedEvent event = new MedicationDispenseCreatedEvent(
                saved.getId(), saved.getMedReqId(), saved.getMedicationId(), saved.getEncounterId(), command.getOperatorId());
            event.setBusNo(saved.getBusNo());
            event.setStatusEnum(saved.getStatusEnum());
            event.setDispenseEnum(saved.getDispenseEnum());
            event.setPatientId(saved.getPatientId());
            event.setLocationId(saved.getLocationId());
            event.setQuantity(saved.getQuantity());
            event.setUnitCode(saved.getUnitCode());
            event.setLotNumber(saved.getLotNumber());
            domainEventPublisher.publish(event);
        }
        return createdIds;
    }

    /**
     * 批量把草稿推进为待配药。
     *
     * @param command 推进命令
     */
    public void promoteToPreparation(PromoteDispenseToPreparationCommand command) {
        if (command.getMedReqIds() == null || command.getMedReqIds().isEmpty()) {
            return;
        }
        List<MedicationDispense> dispenses = medicationDispenseRepository.findByMedReqIdIn(command.getMedReqIds());
        List<MedicationDispense> toSave = new ArrayList<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense.isDraftStatus()) {
                dispense.initPreparation();
                toSave.add(dispense);
            }
        }
        medicationDispenseRepository.saveAll(toSave);
        for (MedicationDispense dispense : toSave) {
            domainEventPublisher.publish(new MedicationDispensePromotedToPreparationEvent(
                dispense.getId(), dispense.getMedReqId(), command.getOperatorId(), dispense.getStatusEnum().getCode()));
        }
    }

    /**
     * 由长期执行记录生成发放单。
     *
     * @param command 长期路径生成命令
     * @return 新建发放单标识
     */
    public Long generateDispenseFromExecution(GenerateDispenseFromExecutionCommand command) {
        MedicationDispense dispense = this.buildFromGenerateInput(
            command.getMedReqId(), command.getMedicationId(), command.getEncounterId(), command.getPatientId(),
            command.getLocationId(), command.getQuantity(), command.getUnitCode(), MedicationDispenseType.INPATIENT);
        dispense.setProcedureId(command.getProcedureId());
        dispense.setPlannedDispenseTime(command.getPlannedDispenseTime());
        dispense.setLotNumber(command.getLotNumber());
        dispense.initPreparation();
        MedicationDispense saved = medicationDispenseRepository.save(dispense);
        MedicationDispenseCreatedEvent event = new MedicationDispenseCreatedEvent(
            saved.getId(), saved.getMedReqId(), saved.getMedicationId(), saved.getEncounterId(), saved.getCreatedBy());
        event.setBusNo(saved.getBusNo());
        event.setStatusEnum(saved.getStatusEnum());
        event.setDispenseEnum(saved.getDispenseEnum());
        event.setPatientId(saved.getPatientId());
        event.setLocationId(saved.getLocationId());
        event.setQuantity(saved.getQuantity());
        event.setUnitCode(saved.getUnitCode());
        event.setLotNumber(saved.getLotNumber());
        domainEventPublisher.publish(event);
        return saved.getId();
    }

    /**
     * 临时医嘱执行后挂接已有发放单（写 procedureId、plannedDispenseTime，推进 PREPARATION）。
     *
     * @param command 挂接命令
     */
    public void linkDispenseProcedure(LinkDispenseProcedureCommand command) {
        if (command.getDispenseId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "发放单标识不能为空");
        }
        if (command.getProcedureId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "执行记录标识不能为空");
        }
        MedicationDispense dispense = this.mustGet(command.getDispenseId());
        if (!dispense.isDraftStatus()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS, "仅草稿发放单可挂接执行记录");
        }
        dispense.setProcedureId(command.getProcedureId());
        dispense.setPlannedDispenseTime(command.getPlannedDispenseTime());
        dispense.initPreparation();
        MedicationDispense saved = medicationDispenseRepository.save(dispense);
        DispenseProcedureLinkedEvent event = new DispenseProcedureLinkedEvent(
            saved.getId(), saved.getMedReqId(), saved.getProcedureId(), command.getOperatorId(), saved.getStatusEnum().getCode());
        event.setPlannedDispenseTime(saved.getPlannedDispenseTime());
        domainEventPublisher.publish(event);
    }

    /**
     * 基于原发放单创建待退药记录。
     *
     * @param command 待退药创建命令
     * @return 新建待退药记录标识
     */
    /**
     * 解析退药发放行对应的原正向发放单（非 REFUND 类型、仍处可收口为已退药的状态）。
     *
     * @param refundLine 已确认或待确认的退药类型发放行
     * @return 原正向发放单；无法解析时 {@code null}
     */
    private MedicationDispense findOriginForwardDispenseForRefundLine(MedicationDispense refundLine) {
        if (refundLine == null || !MedicationDispenseType.REFUND.equals(refundLine.getDispenseEnum())) {
            return null;
        }
        Long refundReqId = refundLine.getMedReqId();
        if (refundReqId == null) {
            return null;
        }
        MedicationRequest refundReq = this.medicationRequestRepository.findById(refundReqId).orElse(null);
        if (refundReq == null || refundReq.getRefundMedicineId() == null) {
            return null;
        }
        List<MedicationDispense> candidates =
            this.medicationDispenseRepository.findByMedReqIdIn(List.of(refundReq.getRefundMedicineId()));
        for (MedicationDispense candidate : candidates) {
            if (candidate == null || Boolean.TRUE.equals(candidate.getIsDeleted())) {
                continue;
            }
            if (MedicationDispenseType.REFUND.equals(candidate.getDispenseEnum())) {
                continue;
            }
            DispenseStatus status = candidate.getStatusEnum();
            if (DispenseStatus.COMPLETED.equals(status)
                || DispenseStatus.PART_COMPLETED.equals(status)
                || DispenseStatus.SUMMARIZED.equals(status)) {
                return candidate;
            }
        }
        return null;
    }

    public Long createPendingRefundDispense(CreatePendingRefundDispenseCommand command) {
        MedicationDispense origin = this.mustGet(command.getOriginDispenseId());
        origin.ensureRefundable();
        if (command.getRefundRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "退药请求标识不能为空");
        }
        MedicationDispense pending = this.buildFromGenerateInput(
            command.getRefundRequestId(), origin.getMedicationId(), origin.getEncounterId(), origin.getPatientId(),
            origin.getLocationId(), command.getRefundQuantity(), origin.getUnitCode(), MedicationDispenseType.REFUND);
        pending.toPendingRefund();
        MedicationDispense saved = medicationDispenseRepository.save(pending);
        // Event（B.14）：持久化成功后再发布，载荷 status 使用 getCode() 字符串身份。
        MedicationDispenseCreatedEvent createdEvent = new MedicationDispenseCreatedEvent(
            saved.getId(), saved.getMedReqId(), saved.getMedicationId(), saved.getEncounterId(), command.getOperatorId());
        createdEvent.setBusNo(saved.getBusNo());
        createdEvent.setStatusEnum(saved.getStatusEnum());
        createdEvent.setDispenseEnum(saved.getDispenseEnum());
        createdEvent.setPatientId(saved.getPatientId());
        createdEvent.setLocationId(saved.getLocationId());
        createdEvent.setQuantity(saved.getQuantity());
        createdEvent.setUnitCode(saved.getUnitCode());
        createdEvent.setLotNumber(saved.getLotNumber());
        domainEventPublisher.publish(createdEvent);
        MedicationDispensePendingRefundCreatedEvent pendingRefundEvent = new MedicationDispensePendingRefundCreatedEvent(
            saved.getId(), origin.getId(), command.getRefundQuantity(), command.getOperatorId());
        pendingRefundEvent.setStatusEnumCode(saved.getStatusEnum().getCode());
        domainEventPublisher.publish(pendingRefundEvent);
        return saved.getId();
    }

    /**
     * 释放医嘱关联的发放预减占用。
     *
     * @param command 释放占用命令
     */
    public void releaseDispenseReserve(ReleaseDispenseReserveCommand command) {
        List<MedicationDispense> dispenses = medicationDispenseRepository.findByMedReqIdIn(List.of(command.getMedReqId()));
        List<MedicationDispense> released = new ArrayList<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense.isDraftOrPreparation()) {
                dispense.markDeleted(command.getOperatorId());
                released.add(dispense);
            }
        }
        medicationDispenseRepository.saveAll(released);
        // Event：存在释占行时再发释放事件，下游读模型与处方视图依赖该通知对齐暂停态。
        if (!released.isEmpty()) {
            List<Long> releasedIds = released.stream().map(MedicationDispense::getId).collect(Collectors.toList());
            publishMedicationDispenseReserveReleased(command.getMedReqId(), releasedIds, command.getOperatorId());
        }
    }

    /**
     * 汇总多条发放单并生成汇总号。
     *
     * @param command 汇总命令
     * @return 汇总单号
     */
    public String summarizeMedicationDispenses(SummarizeMedicationDispensesCommand command) {
        String agg = AggregateTypeConstants.MEDICATION_DISPENSE;
        String seqFull = businessSequenceService.getSeq(agg, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        String summaryNo = MedicationCommonConstants.SUMMARY_NO_PREFIX + seqFull.substring(agg.length());
        List<MedicationDispense> dispenses = medicationDispenseRepository.findByIdIn(command.getDispenseIds());
        List<Long> summarizedIds = new ArrayList<>();
        for (MedicationDispense dispense : dispenses) {
            dispense.applySummaryState(summaryNo);
            summarizedIds.add(dispense.getId());
        }
        medicationDispenseRepository.saveAll(dispenses);
        // Event：汇总事实落库后再发布绑定事件，头表状态码由载荷显式携带（B.14/B.15）。
        if (!summarizedIds.isEmpty()) {
            MedicationDispenseSummarizedEvent summarizedEvent =
                new MedicationDispenseSummarizedEvent(summaryNo, summarizedIds, command.getOperatorId());
            summarizedEvent.setSummaryHeaderStatusEnumCode(MedicationSummaryStatus.SUMMARIZED.getCode());
            domainEventPublisher.publish(summarizedEvent);
        }
        return summaryNo;
    }

    /**
     * 取消汇总并回退到待配药。
     *
     * @param command 取消汇总命令
     */
    public void cancelMedicationDispenseSummary(CancelMedicationDispenseSummaryCommand command) {
        List<MedicationDispense> dispenses = medicationDispenseRepository.findBySummaryNo(command.getSummaryNo());
        for (MedicationDispense dispense : dispenses) {
            dispense.ensureSummaryCancellable();
            dispense.setSummaryNo(null);
            dispense.initPreparation();
        }
        medicationDispenseRepository.saveAll(dispenses);
        for (MedicationDispense dispense : dispenses) {
            MedicationDispenseSummaryCancelledEvent cancelledEvent =
                new MedicationDispenseSummaryCancelledEvent(dispense.getId(), command.getSummaryNo(), command.getOperatorId());
            cancelledEvent.setSlipStatusEnumCode(dispense.getStatusEnum().getCode());
            cancelledEvent.setSummaryHeaderStatusEnumCode(MedicationSummaryStatus.CANCELLED.getCode());
            domainEventPublisher.publish(cancelledEvent);
        }
    }

    /**
     * 完成汇总发药。
     *
     * @param command 汇总发药命令
     */
    public void completeSummaryDispense(CompleteSummaryDispenseCommand command) {
        List<MedicationDispense> dispenses = medicationDispenseRepository.findBySummaryNo(command.getSummaryNo());
        List<Long> completedIds = new ArrayList<>();
        for (MedicationDispense dispense : dispenses) {
            dispense.ensureStatusForDispense();
            dispense.toCompleted(command.getPractitionerId(), dispense.getQuantity());
            completedIds.add(dispense.getId());
        }
        medicationDispenseRepository.saveAll(dispenses);
        // Event：汇总闭环完成后发布携带明细态与头态载荷的事件，供 slip 与汇总头投影一致推进。
        if (!completedIds.isEmpty()) {
            MedicationDispenseSummaryCompletedEvent completedEvent =
                new MedicationDispenseSummaryCompletedEvent(command.getSummaryNo(), completedIds, command.getOperatorId());
            completedEvent.setSlipStatusEnumCode(dispenses.get(0).getStatusEnum().getCode());
            completedEvent.setSummaryHeaderStatusEnumCode(MedicationSummaryStatus.COMPLETED.getCode());
            domainEventPublisher.publish(completedEvent);
        }
    }

    /**
     * 回退未发药单据到草稿态。
     *
     * @param command 回退命令
     */
    public void rollbackUndispensedDispense(RollbackUndispensedDispenseCommand command) {
        List<MedicationDispense> dispenses = medicationDispenseRepository.findByIdIn(command.getIds());
        for (MedicationDispense dispense : dispenses) {
            // Pre：回退仅恢复草稿并清理汇总标识，不覆盖其它业务字段。
            dispense.ensureRollbackable();
            dispense.initDraft();
            dispense.setSummaryNo(null);
        }
        medicationDispenseRepository.saveAll(dispenses);
    }

    /**
     * 软删未发药记录并释放占用。
     *
     * @param command 删除命令
     */
    public void removeUndispensedDispense(RemoveUndispensedDispenseCommand command) {
        List<MedicationDispense> dispenses = medicationDispenseRepository.findByIdIn(command.getIds());
        List<MedicationDispense> released = new ArrayList<>();
        for (MedicationDispense dispense : dispenses) {
            dispense.ensureCancellable();
            dispense.markDeleted(command.getOperatorId());
            released.add(dispense);
        }
        medicationDispenseRepository.saveAll(released);
        // Event：释占事件按医嘱维度合并发布，避免同一批次跨医嘱误捆；单医嘱下一事件列出全部释放行。
        if (!released.isEmpty()) {
            Long medReqId = released.get(0).getMedReqId();
            if (released.stream().map(MedicationDispense::getMedReqId).collect(Collectors.toSet()).size() == 1) {
                List<Long> releasedIds = released.stream().map(MedicationDispense::getId).collect(Collectors.toList());
                publishMedicationDispenseReserveReleased(medReqId, releasedIds, command.getOperatorId());
            } else {
                for (MedicationDispense releasedDispense : released) {
                    publishMedicationDispenseReserveReleased(
                        releasedDispense.getMedReqId(), List.of(releasedDispense.getId()), command.getOperatorId());
                }
            }
        }
    }

    /**
     * 发布预减释放事件：写侧行已由 {@code markDeleted} 软删（底座 B.13）；读侧 slip 以 {@link DispenseStatus#ON_HOLD}
     * 表达释占/暂停可视态，避免在读模型上重复逻辑删。
     *
     * @param medReqId 药品申请标识
     * @param releasedDispenseIds 被释放的发放单主键列表
     * @param operatorId 操作人
     */
    private void publishMedicationDispenseReserveReleased(Long medReqId, List<Long> releasedDispenseIds, Long operatorId) {
        if (releasedDispenseIds == null || releasedDispenseIds.isEmpty()) {
            return;
        }
        MedicationDispenseReserveReleasedEvent releasedEvent =
            new MedicationDispenseReserveReleasedEvent(medReqId, releasedDispenseIds, operatorId);
        releasedEvent.setStatusEnumCode(DispenseStatus.ON_HOLD.getCode());
        domainEventPublisher.publish(releasedEvent);
    }

    /**
     * 按主键加载发放单聚合，不存在时阻断后续写路径。
     *
     * @param id 发放单技术主键
     * @return 非空聚合根
     * @throws BusinessException 记录不存在
     */
    private MedicationDispense mustGet(Long id) {
        return medicationDispenseRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_NOT_FOUND, "药品发放记录不存在"));
    }

    /**
     * 由医嘱上下文字段组装发放单聚合草稿（未赋状态与批次字段）。
     *
     * @param medReqId 药品申请主键
     * @param medicationId 药品主键
     * @param encounterId 就诊主键
     * @param patientId 患者主键
     * @param locationId 发药库位
     * @param quantity 数量
     * @param unitCode 单位编码
     * @param dispenseEnum 发药途径/场景枚举
     * @return 待持久化的新实例
     */
    private MedicationDispense buildFromGenerateInput(
        Long medReqId, Long medicationId, Long encounterId, Long patientId, Long locationId,
        java.math.BigDecimal quantity, String unitCode, MedicationDispenseType dispenseEnum) {
        MedicationDispense dispense = new MedicationDispense();
        dispense.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_DISPENSE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        dispense.setMedReqId(medReqId);
        dispense.setMedicationId(medicationId);
        dispense.setEncounterId(encounterId);
        dispense.setPatientId(patientId);
        dispense.setLocationId(locationId);
        dispense.setQuantity(quantity);
        dispense.setUnitCode(unitCode);
        dispense.setDispenseEnum(dispenseEnum);
        dispense.setStatusChangedTime(LocalDateTime.now());
        return dispense;
    }
}
