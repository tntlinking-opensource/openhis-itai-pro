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

package com.openhis.health.domain.medication.medicationrequest.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.medication.medicationrequest.command.*;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.prescriptionsplit.OutpatientPrescriptionSplitPlanner;
import com.openhis.health.domain.medication.medicationrequest.repository.MedicationRequestRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.events.medication.medicationrequest.*;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 药品申请写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class MedicationRequestDomainService {

    private final MedicationRequestRepository medicationRequestRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 药品申请 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#MEDICATION_REQUEST}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 创建西药/中成药医嘱草稿。
     *
     * @param command 草稿创建命令
     * @return 新建申请标识
     */
    public Long createMedicationOrderDraft(CreateMedicationOrderDraftCommand command) {
        MedicationRequest request = new MedicationRequest();
        // Pre：业务号在聚合创建时生成，避免外部传入污染唯一性口径。
        request.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.setEncounterId(command.getEncounterId());
        request.setPatientId(command.getPatientId());
        request.setMedicationId(command.getMedicationId());
        // Pre：医生站开立路径写入生成来源，支撑列表按来源过滤（BR-ORD-010）；未传时回落医生开立。
        String generateSourceCode = command.getGenerateSourceEnum();
        if (generateSourceCode == null) {
            generateSourceCode = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        }
        request.setGenerateSourceEnum(generateSourceCode);
        if (command.getOrgId() != null) {
            request.setOrgId(command.getOrgId());
        }
        // Pre：开嘱人以后端当前操作人为兜底，避免调用方漏传 recorderId 导致列表无开嘱人。
        if (command.getRecorderId() != null) {
            request.setRecorderId(command.getRecorderId());
        } else {
            request.setRecorderId(command.getOperatorId());
        }
        // Pre：申请方医师（requesterId/requester_id）与 recorder 同源开立口径，供医嘱列表「开立人」统一展示。
        if (command.getRecorderId() != null) {
            request.setRequesterId(command.getRecorderId());
        } else {
            request.setRequesterId(command.getOperatorId());
        }
        request.setTherapyEnum(command.getTherapyEnum());
        request.setQuantity(command.getQuantity());
        request.setUnitCode(command.getUnitCode());
        request.setMethodCode(command.getMethodCode());
        request.setRateCode(command.getRateCode());
        request.setFirstDose(command.getFirstDose());
        request.setFirstDoseUnitCode(command.getFirstDoseUnitCode());
        if (command.getSortNumber() != null) {
            request.setSortNumber(command.getSortNumber());
        }
        // Pre：医生站暂存扩展（单次用量/天数/皮试/诊断绑定/成组号），与列表 IF-DOR-10 回显字段对齐。
        if (command.getGroupId() != null) {
            request.setGroupId(command.getGroupId());
        }
        if (command.getDose() != null) {
            request.setDose(command.getDose());
        }
        if (command.getDoseUnitCode() != null) {
            request.setDoseUnitCode(command.getDoseUnitCode());
        }
        if (command.getDispensePerDuration() != null) {
            request.setDispensePerDuration(command.getDispensePerDuration());
        }
        if (command.getSkinTestFlag() != null) {
            request.setSkinTestFlag(command.getSkinTestFlag());
        }
        if (command.getConditionDefinitionId() != null) {
            request.setConditionDefinitionId(command.getConditionDefinitionId());
        }
        // Pre：绑定就诊诊断行，供收费/医保按 encounter_diagnosis_id 解析医疗类别。
        if (command.getEncounterDiagnosisId() != null) {
            request.setEncounterDiagnosisId(command.getEncounterDiagnosisId());
        }
        // Pre：费用性质覆盖，与诊疗服务申请 contract_id 口径一致。
        if (command.getContractId() != null) {
            request.setContractId(command.getContractId());
        }
        request.validateDosageSemantics();
        // Pre：医生站列表「开立时间」与耗材草稿一致：首次暂存即落库 req_authored_time；正式签发时在 sign 路径覆盖为签发时刻。
        request.setReqAuthoredTime(LocalDateTime.now());
        request.initDraft();
        MedicationRequest saved = medicationRequestRepository.save(request);
        domainEventPublisher.publish(new MedicationOrderDraftCreatedEvent(saved.getId(), saved.getEncounterId(), saved.getPatientId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 修改药品医嘱草稿。
     *
     * <p>通过对比命令入参与现有实体字段，动态计算本次实际变更的字段集合后写入事件载荷，
     * 禁止以静态字符串替代（§D3.6.2 事件变更摘要禁止硬编码）。</p>
     *
     * @param command 草稿修改命令，包含需要变更的字段（null 表示不变更该字段）
     */
    public void changeMedicationOrderDraft(ChangeMedicationOrderDraftCommand command) {
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureDraftEditable();
        // Pre：对比入参与现有值，动态收集实际发生变更的字段名称。
        List<String> changedFields = new ArrayList<>();
        if (command.getTherapyEnum() != null && !command.getTherapyEnum().equals(request.getTherapyEnum())) {
            request.setTherapyEnum(command.getTherapyEnum());
            changedFields.add(FieldNameConstants.THERAPY_ENUM);
        }
        if (command.getQuantity() != null && !command.getQuantity().equals(request.getQuantity())) {
            request.setQuantity(command.getQuantity());
            changedFields.add(FieldNameConstants.QUANTITY);
        }
        if (command.getUnitCode() != null && !command.getUnitCode().equals(request.getUnitCode())) {
            request.setUnitCode(command.getUnitCode());
            changedFields.add(FieldNameConstants.UNIT_CODE);
        }
        if (command.getMethodCode() != null && !command.getMethodCode().equals(request.getMethodCode())) {
            request.setMethodCode(command.getMethodCode());
            changedFields.add(FieldNameConstants.METHOD_CODE);
        }
        if (command.getRateCode() != null && !command.getRateCode().equals(request.getRateCode())) {
            request.setRateCode(command.getRateCode());
            changedFields.add(FieldNameConstants.RATE_CODE);
        }
        if (command.getFirstDose() != null && !command.getFirstDose().equals(request.getFirstDose())) {
            request.setFirstDose(command.getFirstDose());
            changedFields.add(FieldNameConstants.FIRST_DOSE);
        }
        if (command.getFirstDoseUnitCode() != null && !command.getFirstDoseUnitCode().equals(request.getFirstDoseUnitCode())) {
            request.setFirstDoseUnitCode(command.getFirstDoseUnitCode());
            changedFields.add(FieldNameConstants.FIRST_DOSE_UNIT_CODE);
        }
        if (command.getSortNumber() != null && !Objects.equals(command.getSortNumber(), request.getSortNumber())) {
            request.setSortNumber(command.getSortNumber());
            changedFields.add(FieldNameConstants.SORT_NUMBER);
        }
        if (command.getGroupId() != null && !Objects.equals(command.getGroupId(), request.getGroupId())) {
            request.setGroupId(command.getGroupId());
            changedFields.add(FieldNameConstants.GROUP_ID);
        }
        if (command.getDose() != null) {
            if (request.getDose() == null || command.getDose().compareTo(request.getDose()) != 0) {
                request.setDose(command.getDose());
                changedFields.add(FieldNameConstants.DOSE);
            }
        }
        if (command.getDoseUnitCode() != null && !command.getDoseUnitCode().equals(request.getDoseUnitCode())) {
            request.setDoseUnitCode(command.getDoseUnitCode());
            changedFields.add(FieldNameConstants.DOSE_UNIT_CODE);
        }
        if (command.getDispensePerDuration() != null && !command.getDispensePerDuration().equals(request.getDispensePerDuration())) {
            request.setDispensePerDuration(command.getDispensePerDuration());
            changedFields.add(FieldNameConstants.DISPENSE_PER_DURATION);
        }
        if (command.getSkinTestFlag() != null && !command.getSkinTestFlag().equals(request.getSkinTestFlag())) {
            request.setSkinTestFlag(command.getSkinTestFlag());
            changedFields.add(FieldNameConstants.SKIN_TEST_FLAG);
        }
        if (command.getConditionDefinitionId() != null
            && !Objects.equals(command.getConditionDefinitionId(), request.getConditionDefinitionId())) {
            request.setConditionDefinitionId(command.getConditionDefinitionId());
            changedFields.add(FieldNameConstants.CONDITION_DEFINITION_ID);
        }
        if (command.getEncounterDiagnosisId() != null
            && !Objects.equals(command.getEncounterDiagnosisId(), request.getEncounterDiagnosisId())) {
            request.setEncounterDiagnosisId(command.getEncounterDiagnosisId());
            changedFields.add(FieldNameConstants.ENCOUNTER_DIAGNOSIS_ID);
        }
        if (command.getContractId() != null
            && !Objects.equals(command.getContractId(), request.getContractId())) {
            request.setContractId(command.getContractId());
            changedFields.add(FieldNameConstants.CONTRACT_ID);
        }
        request.validateDosageSemantics();
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new MedicationOrderDraftEditedEvent(
            request.getId(), String.join(",", changedFields), command.getOperatorId()));
    }

    /**
     * 移除药品医嘱草稿。
     *
     * @param command 草稿移除命令
     */
    public void removeMedicationOrderDraft(RemoveMedicationOrderDraftCommand command) {
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureDraftEditable();
        request.markDeleted(command.getOperatorId());
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new MedicationOrderDraftRemovedEvent(request.getId(), request.getEncounterId(), command.getOperatorId()));
    }

    /**
     * 签发医嘱并生成处方号。
     *
     * @param command 签发命令
     * @return 处方号
     */
    public String signMedicationOrder(SignMedicationOrderCommand command) {
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureSignable();
        // Pre：与诊疗服务申请签发写入 authored_time 口径一致：开立时间取签发时刻，供医生站统一列表排序与展示。
        request.setReqAuthoredTime(LocalDateTime.now());
        request.draftToActive(command.getPrescriptionNo());
        medicationRequestRepository.save(request);
        // Pub：载荷携带 save 后 status_enum，供 OrderInfo 投影 ACTIVE 供护士站待校对列表查询。
        domainEventPublisher.publish(new MedicationOrderSignedEvent(
            request.getId(),
            request.getPrescriptionNo(),
            request.getStatusEnum(),
            command.getOperatorId()));
        return request.getPrescriptionNo();
    }

    /**
     * 签退医嘱并回退草稿。
     *
     * @param command 签退命令
     */
    public void signOffMedicationOrder(SignOffMedicationOrderCommand command) {
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureSignOffAllowed();
        request.activeToDraft();
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new MedicationOrderSignOffEvent(request.getId(), command.getOperatorId()));
    }

    /**
     * 停止长期医嘱。
     *
     * @param command 停嘱命令
     */
    public void stopLongTermMedicationOrder(StopLongTermMedicationOrderCommand command) {
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureLongTermForStop();
        request.toStopped(command.getStopTime());
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new LongTermMedicationOrderStoppedEvent(
            request.getId(), request.getEffectiveDoseEnd(), command.getOperatorId(), request.getStatusEnum()));
    }

    /**
     * 取消长期医嘱停嘱：恢复为已校对或已发送，并清空结束时间（IF-IP-DOR-25 / BR-MRQ-006a）。
     *
     * @param command 取消停嘱命令，须含 id 与 operatorId
     * @throws BusinessException 命令为空、非长期或非 STOPPED 时
     */
    public void cancelStopLongTermMedicationOrder(CancelStopLongTermMedicationOrderCommand command) {
        if (command == null || command.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "取消停嘱时 id 不能为空");
        }
        // 加载：按主键取药品申请聚合根。
        MedicationRequest request = this.mustGet(command.getId());
        // 前置校验：须为长期且当前 STOPPED。
        request.ensureLongTermStoppedForCancelStop();
        // 状态迁移：performer_check_id 非空→VERIFIED，否则→ACTIVE，并清空 effective_dose_end。
        request.fromStopped();
        // 持久化：先落库再发事件，避免投影读到旧态。
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new LongTermMedicationOrderStopCancelledEvent(
            request.getId(), command.getOperatorId(), request.getStatusEnum()));
    }

    /**
     * 校对医嘱并推进到已校对状态。
     *
     * @param command 校对命令
     */
    public void verifyMedicationOrder(VerifyMedicationOrderCommand command) {
        if (command == null || command.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "校对药品医嘱时 id 不能为空");
        }
        if (command.getPerformerCheckId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "校对药品医嘱时 performerCheckId 不能为空");
        }
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureVerifiable();
        LocalDateTime checkTime = command.getCheckTime();
        if (checkTime == null) {
            // 校对事件时刻未传则取当前系统时间。
            checkTime = LocalDateTime.now();
        }
        request.activeToCompleted(command.getPerformerCheckId(), checkTime);
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new MedicationOrderVerifiedEvent(
            request.getId(), request.getPerformerCheckId(), request.getCheckTime(), command.getOperatorId(),
            request.getStatusEnum()));
    }

    /**
     * 门诊收费完成：已发送医嘱推进为已完成（COMPLETED），不进住院核对 VERIFIED 语义。
     *
     * @param medicationRequestId 药品申请主键
     * @param operatorId          收费操作人；可空则不写入事件载荷
     */
    public void completeMedicationRequestAfterOutpatientCharge(Long medicationRequestId, Long operatorId) {
        MedicationRequest request = this.mustGet(medicationRequestId);
        if (RequestStatus.COMPLETED.getCode().equals(request.getStatusEnum())) {
            return;
        }
        request.ensureCompletableAfterOutpatientCharge();
        request.activeToCompletedAfterOutpatientCharge();
        medicationRequestRepository.save(request);
        LocalDateTime completedTime = LocalDateTime.now();
        MedicationOrderCompletedAfterOutpatientChargeEvent event =
            new MedicationOrderCompletedAfterOutpatientChargeEvent(request.getId(), operatorId, completedTime);
        domainEventPublisher.publish(event);
    }

    /**
     * 退回医嘱到草稿态。
     *
     * @param command 退回命令
     */
    public void rejectMedicationOrder(RejectMedicationOrderCommand command) {
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureRejectable();
        if (command.getPerformerCheckId() != null) {
            request.setPerformerCheckId(command.getPerformerCheckId());
        }
        if (command.getCheckTime() != null) {
            request.setCheckTime(command.getCheckTime());
        } else {
            request.setCheckTime(LocalDateTime.now());
        }
        request.activeToDraft();
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new MedicationOrderRejectedEvent(
            request.getId(), command.getReason(), command.getOperatorId(), request.getStatusEnum()));
    }

    /**
     * 批量调整医嘱分组。
     *
     * @param command 分组调整命令
     */
    public void adjustMedicationOrderGroup(AdjustMedicationOrderGroupCommand command) {
        List<MedicationRequest> requests = medicationRequestRepository.findByIdIn(command.getIds());
        if (requests.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "待调整医嘱不存在");
        }
        for (MedicationRequest request : requests) {
            request.setGroupId(command.getGroupId());
        }
        medicationRequestRepository.saveAll(requests);
        domainEventPublisher.publish(new MedicationOrderGroupAdjustedEvent(requests.get(0).getId(), command.getIds(), command.getGroupId(), command.getOperatorId()));
    }

    /**
     * 取消医嘱。
     *
     * @param command 取消命令
     */
    public void cancelMedicationOrder(CancelMedicationOrderCommand command) {
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureCancellable();
        request.toCancelled();
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new MedicationOrderCancelledEvent(request.getId(), command.getCancelReason(), command.getOperatorId()));
    }

    /**
     * 创建负向退药请求单。
     *
     * @param command 退药请求创建命令
     * @return 新建负向单标识
     */
    public Long createRefundMedicationOrder(CreateRefundMedicationOrderCommand command) {
        MedicationRequest origin = this.mustGet(command.getOriginRequestId());
        MedicationRequest refund = new MedicationRequest();
        refund.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        refund.setRefundMedicineId(origin.getId());
        this.copyRefundMedicationOrderFieldsFromOrigin(refund, origin);
        refund.markAsRefundFromPrescription(origin.getPrescriptionNo());
        refund.initDraft();
        MedicationRequest saved = medicationRequestRepository.save(refund);
        domainEventPublisher.publish(new MedicationOrderRefundRequestedEvent(saved.getId(), origin.getId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 负向退药药嘱拷贝原正向药嘱临床/展示字段（新 busNo、refundMedicineId、处方号 T 前缀由调用方处理）。
     *
     * @param refund 待落库负向药嘱
     * @param origin 原正向药嘱
     */
    private void copyRefundMedicationOrderFieldsFromOrigin(MedicationRequest refund, MedicationRequest origin) {
        refund.setEncounterId(origin.getEncounterId());
        refund.setPatientId(origin.getPatientId());
        refund.setMedicationId(origin.getMedicationId());
        refund.setTherapyEnum(origin.getTherapyEnum());
        refund.setQuantity(origin.getQuantity());
        refund.setUnitCode(origin.getUnitCode());
        refund.setMethodCode(origin.getMethodCode());
        refund.setRateCode(origin.getRateCode());
        refund.setRequesterId(origin.getRequesterId());
        refund.setGroupId(origin.getGroupId());
        refund.setPackageId(origin.getPackageId());
        refund.setIntentEnum(origin.getIntentEnum());
        refund.setCategoryEnum(origin.getCategoryEnum());
        refund.setPriorityEnum(origin.getPriorityEnum());
        refund.setPerformFlag(origin.getPerformFlag());
        refund.setLocationId(origin.getLocationId());
        refund.setOrgId(origin.getOrgId());
        refund.setPerformLocation(origin.getPerformLocation());
        refund.setPerformOrg(origin.getPerformOrg());
        refund.setPerformerEnum(origin.getPerformerEnum());
        refund.setPerformerId(origin.getPerformerId());
        refund.setRecorderId(origin.getRecorderId());
        refund.setReportFlag(origin.getReportFlag());
        refund.setSupportInfo(origin.getSupportInfo());
        refund.setReasonJson(origin.getReasonJson());
        refund.setEffectiveDoseStart(origin.getEffectiveDoseStart());
        refund.setExecuteNum(origin.getExecuteNum());
        refund.setDose(origin.getDose());
        refund.setDoseUnitCode(origin.getDoseUnitCode());
        refund.setMaxDose(origin.getMaxDose());
        refund.setFirstDose(origin.getFirstDose());
        refund.setFirstDoseUnitCode(origin.getFirstDoseUnitCode());
        refund.setFirstDuration(origin.getFirstDuration());
        refund.setDispenseInterval(origin.getDispenseInterval());
        refund.setDispensePerQuantity(origin.getDispensePerQuantity());
        refund.setDispensePerDuration(origin.getDispensePerDuration());
        refund.setLotNumber(origin.getLotNumber());
        refund.setTraceNo(origin.getTraceNo());
        refund.setSkinTestFlag(origin.getSkinTestFlag());
        refund.setInfusionFlag(origin.getInfusionFlag());
        refund.setSpeedCode(origin.getSpeedCode());
        refund.setContractId(origin.getContractId());
        refund.setConditionDefinitionId(origin.getConditionDefinitionId());
        refund.setEncounterDiagnosisId(origin.getEncounterDiagnosisId());
        refund.setPerformerCheckId(origin.getPerformerCheckId());
        refund.setCheckTime(origin.getCheckTime());
        refund.setRxTypeCode(origin.getRxTypeCode());
        refund.setYbClassEnum(origin.getYbClassEnum());
        refund.setDosageInstruction(origin.getDosageInstruction());
        refund.setBasedOnTable(origin.getBasedOnTable());
        refund.setBasedOnId(origin.getBasedOnId());
        refund.setTcmFlag(origin.getTcmFlag());
        refund.setContentJson(origin.getContentJson());
        refund.setChineseHerbsDoseQuantity(origin.getChineseHerbsDoseQuantity());
        refund.setSufferingFlag(origin.getSufferingFlag());
        refund.setSortNumber(origin.getSortNumber());
        refund.setGenerateSourceEnum(origin.getGenerateSourceEnum());
        refund.setRemark(origin.getRemark());
    }

    /**
     * 门诊药房退药闭环：将负向药品申请推进为已完成。
     *
     * @param requestId  负向药品申请主键
     * @param operatorId 操作人
     */
    public void completeMedicationRequestAfterRefund(Long requestId, Long operatorId) {
        MedicationRequest request = this.mustGet(requestId);
        request.ensureCompletableAfterPharmacyRefund();
        request.toCompletedAfterPharmacyRefund();
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(
            new MedicationOrderCompletedAfterOutpatientChargeEvent(request.getId(), operatorId, LocalDateTime.now()));
    }

    /**
     * 基于原单重开医嘱。
     *
     * @param command 重开命令
     * @return 新建重开单标识
     */
    public Long reopenMedicationOrder(ReopenMedicationOrderCommand command) {
        MedicationRequest origin = this.mustGet(command.getOriginRequestId());
        MedicationRequest reopened = new MedicationRequest();
        reopened.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        reopened.setEncounterId(origin.getEncounterId());
        reopened.setPatientId(origin.getPatientId());
        reopened.setMedicationId(origin.getMedicationId());
        reopened.setTherapyEnum(origin.getTherapyEnum());
        reopened.setQuantity(origin.getQuantity());
        reopened.setUnitCode(origin.getUnitCode());
        reopened.setMethodCode(origin.getMethodCode());
        reopened.setRateCode(origin.getRateCode());
        reopened.initDraft();
        MedicationRequest saved = medicationRequestRepository.save(reopened);
        domainEventPublisher.publish(new MedicationOrderReopenedEvent(saved.getId(), origin.getId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 创建中药医嘱草稿（BR-MRQ-011：与西药 create 分轨，落库饮片行级字段与方头扩展）。
     *
     * @param command 中药草稿创建命令；{@code medicationId}、{@code quantity} 必填
     * @return 新建草稿标识
     * @throws BusinessException 入参非法、非中药语义或剂量不变式失败时
     */
    public Long createHerbalMedicationOrderDraft(CreateHerbalMedicationOrderDraftCommand command) {
        if (command == null || command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "创建中药草稿时操作人不能为空");
        }
        if (command.getMedicationId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "创建中药草稿时饮片定义标识不能为空");
        }
        if (command.getQuantity() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "创建中药草稿时请求数量不能为空");
        }
        MedicationRequest request = new MedicationRequest();
        // Pre：业务号在聚合创建时生成，避免外部传入污染唯一性口径。
        request.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.setEncounterId(command.getEncounterId());
        request.setPatientId(command.getPatientId());
        this.applyHerbalDraftFieldsFromCreateCommand(request, command);
        request.validateDosageSemantics();
        // Pre：中药草稿首次保存记录开立时刻；正式签发时由 sign 覆盖。
        request.setReqAuthoredTime(LocalDateTime.now());
        request.initDraft();
        // 持久化：先落库再发事件（B.14）。
        MedicationRequest saved = medicationRequestRepository.save(request);
        domainEventPublisher.publish(new HerbalMedicationOrderDraftCreatedEvent(saved.getId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 修改中药医嘱草稿（BR-MRQ-011：仅草稿且 tcm_flag=true 行可编辑）。
     *
     * @param command 中药草稿修改命令；null 字段表示不变更；须 {@code tcm_flag=true} 草稿行
     * @throws BusinessException 非草稿、非中药路径或剂量不变式失败时
     */
    public void changeHerbalMedicationOrderDraft(ChangeHerbalMedicationOrderDraftCommand command) {
        if (command == null || command.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "修改中药草稿时药品申请标识不能为空");
        }
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "修改中药草稿时操作人不能为空");
        }
        MedicationRequest request = this.mustGet(command.getId());
        request.ensureDraftEditable();
        this.assertHerbalMedicationRequestPath(request);
        // Cmd：按命令非空字段覆盖草稿；null 表示该字段不变更。
        this.applyHerbalDraftFieldChanges(request, command);
        request.validateDosageSemantics();
        // 持久化：先落库再发事件（B.14）。
        medicationRequestRepository.save(request);
        domainEventPublisher.publish(new HerbalMedicationOrderDraftEditedEvent(request.getId(), command.getOperatorId()));
    }

    /**
     * 门诊药品处方分方：划分处方单元并回写 {@code prescription_no}、{@code rx_type_code}（不改医嘱状态）。
     *
     * @param command 分方命令（行快照与金额上限由应用层装配）
     * @return 分方结果
     * @throws BusinessException 待分方行不存在或违反强制分方规则时
     */
    public SplitPrescriptionResult splitPrescription(SplitPrescriptionCommand command) {
        if (command == null || command.getLines() == null || command.getLines().isEmpty()) {
            return new SplitPrescriptionResult(0, 0, Map.of(), Map.of());
        }
        List<PrescriptionSplitLineContext> lineContexts = command.getLines();
        // Pre：纯内存装箱，按 med_type/特殊药品/库房/金额/品种上限划分处方单元
        List<OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan> unitPlans =
            OutpatientPrescriptionSplitPlanner.plan(lineContexts, command.getAmountLimitYuan());
        if (unitPlans.isEmpty()) {
            return new SplitPrescriptionResult(0, 0, Map.of(), Map.of());
        }
        LinkedHashSet<Long> allRequestIds = new LinkedHashSet<>();
        for (PrescriptionSplitLineContext line : lineContexts) {
            if (line != null && line.getRequestId() != null) {
                allRequestIds.add(line.getRequestId());
            }
        }
        List<MedicationRequest> entities = medicationRequestRepository.findByIdIn(new ArrayList<>(allRequestIds));
        if (entities.size() != allRequestIds.size()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "待分方医嘱不存在");
        }
        Map<Long, MedicationRequest> entityById = new HashMap<>();
        for (MedicationRequest entity : entities) {
            entityById.put(entity.getId(), entity);
        }
        Map<Long, String> prescriptionNoByRequestId = new HashMap<>();
        Map<Long, String> rxTypeCodeByRequestId = new HashMap<>();
        // Pre：按处方单元数批量取当日处方号（前缀 CP，与 bus_no 的 MR 分序列；B.10.9）
        List<String> prescriptionNos = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.COMPOSITE_PRESCRIPTION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, unitPlans.size());
        for (int unitIndex = 0; unitIndex < unitPlans.size(); unitIndex++) {
            OutpatientPrescriptionSplitPlanner.PrescriptionUnitPlan unit = unitPlans.get(unitIndex);
            String prescriptionNo = prescriptionNos.get(unitIndex);
            String rxTypeCode = unit.getRxTypeCode();
            for (Long requestId : unit.getRequestIds()) {
                MedicationRequest entity = entityById.get(requestId);
                if (entity == null) {
                    throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "待分方医嘱不存在");
                }
                entity.setPrescriptionNo(prescriptionNo);
                entity.setRxTypeCode(rxTypeCode);
                prescriptionNoByRequestId.put(requestId, prescriptionNo);
                rxTypeCodeByRequestId.put(requestId, rxTypeCode);
            }
        }
        // Pub：先落库再返回（B.14）；本方法不发布领域事件
        medicationRequestRepository.saveAll(entities);
        return new SplitPrescriptionResult(
            allRequestIds.size(),
            unitPlans.size(),
            prescriptionNoByRequestId,
            rxTypeCodeByRequestId);
    }

    /**
     * 半退保留链：按源药品申请事实在本聚合内正向克隆新医嘱（DM-MED-003 §6.1.16）。
     *
     * <p>复刻可对齐的用药事实与开立上下文字段；签发类字段清空；状态回落草稿；{@code based_on_table}/{@code based_on_id} 指向源申请；
     * 新 {@code bus_no} 由本服务按日取号；先 {@code save} 后发布 {@link MedicationOrderDraftCreatedEvent}（B.14）。</p>
     *
     * @param command 克隆入参，须含源主键与操作人
     * @return 新药品申请主键
     * @throws BusinessException 入参非法、源不可克隆或剂量不变式失败时
     */
    public Long cloneMedicationRequestForPreservedChargeRegeneration(CloneMedicationRequestForPreservedChargeRegenerationCommand command) {
        if (command == null || command.getSourceMedicationRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "半退保留链克隆入参不能为空");
        }
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "半退保留链克隆操作人不能为空");
        }
        MedicationRequest source = this.mustGet(command.getSourceMedicationRequestId());
        this.assertMedicationRequestCloneableForPreservedChargeRegeneration(source);
        MedicationRequest cloned = this.buildMedicationRequestCloneFromSourceForPreservedChargeRegeneration(source, command.getOperatorId());
        // Pre：复刻后仍须满足剂量与数量不变式，避免源数据异常污染新行。
        cloned.validateDosageSemantics();
        // B.14：先落库再发事件，避免投影侧按主键反查时出现幽灵行。
        MedicationRequest saved = medicationRequestRepository.save(cloned);
        domainEventPublisher.publish(new MedicationOrderDraftCreatedEvent(
            saved.getId(), saved.getEncounterId(), saved.getPatientId(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 私有方法：mustGet。
     */
    private MedicationRequest mustGet(Long id) {
        return medicationRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品申请不存在"));
    }

    /**
     * 校验药品申请属于中药专用写路径（tcm_flag=true）。
     *
     * @param request 药品申请聚合根
     * @throws BusinessException 非中药路径时拒绝
     */
    private void assertHerbalMedicationRequestPath(MedicationRequest request) {
        if (!Boolean.TRUE.equals(request.getTcmFlag())) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "非中药医嘱不得走中药专用修改路径");
        }
    }

    /**
     * 创建路径：将中药草稿命令字段写入聚合根（不含 busNo/状态/initDraft）。
     *
     * @param request 待写入实体
     * @param command   创建命令
     */
    private void applyHerbalDraftFieldsFromCreateCommand(MedicationRequest request, CreateHerbalMedicationOrderDraftCommand command) {
        request.setMedicationId(command.getMedicationId());
        Boolean tcmFlag = command.getTcmFlag();
        if (tcmFlag == null) {
            tcmFlag = Boolean.TRUE;
        }
        request.setTcmFlag(tcmFlag);
        request.setChineseHerbsDoseQuantity(command.getChineseHerbsDoseQuantity());
        request.setSufferingFlag(command.getSufferingFlag());
        request.setContentJson(command.getContentJson());
        request.setDosageInstruction(command.getDosageInstruction());
        request.setRemark(command.getRemark());
        request.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        request.setQuantity(command.getQuantity());
        if (command.getUnitCode() != null) {
            request.setUnitCode(command.getUnitCode());
        } else if (command.getDoseUnitCode() != null) {
            // 饮片开立数量单位与单剂用量单位一致（如 g），供 FIFO 与 quantity 配套
            request.setUnitCode(command.getDoseUnitCode());
        } else {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_MEDICATION_UNIT_CODE_REQUIRED);
        }
        if (command.getMethodCode() != null) {
            request.setMethodCode(command.getMethodCode());
        } else {
            request.setMethodCode(MedicationCommonConstants.HERBAL_METHOD_CODE);
        }
        if (command.getRateCode() != null) {
            request.setRateCode(command.getRateCode());
        } else {
            request.setRateCode(MedicationCommonConstants.HERBAL_RATE_CODE);
        }
        if (command.getDose() != null) {
            request.setDose(command.getDose());
        }
        if (command.getDoseUnitCode() != null) {
            request.setDoseUnitCode(command.getDoseUnitCode());
        }
        if (command.getDispensePerDuration() != null) {
            request.setDispensePerDuration(command.getDispensePerDuration());
        }
        if (command.getConditionDefinitionId() != null) {
            request.setConditionDefinitionId(command.getConditionDefinitionId());
        }
        if (command.getEncounterDiagnosisId() != null) {
            request.setEncounterDiagnosisId(command.getEncounterDiagnosisId());
        }
        if (command.getContractId() != null) {
            request.setContractId(command.getContractId());
        }
        if (command.getOrgId() != null) {
            request.setOrgId(command.getOrgId());
        }
        if (command.getPerformLocation() != null) {
            request.setPerformLocation(command.getPerformLocation());
        }
        if (command.getSortNumber() != null) {
            request.setSortNumber(command.getSortNumber());
        }
        if (command.getRxTypeCode() != null) {
            request.setRxTypeCode(command.getRxTypeCode());
        }
        if (command.getPrescriptionNo() != null) {
            request.setPrescriptionNo(command.getPrescriptionNo());
        }
        String generateSourceCode = command.getGenerateSourceEnum();
        if (generateSourceCode == null) {
            generateSourceCode = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        }
        request.setGenerateSourceEnum(generateSourceCode);
        if (command.getRecorderId() != null) {
            request.setRecorderId(command.getRecorderId());
            request.setRequesterId(command.getRecorderId());
        } else {
            request.setRecorderId(command.getOperatorId());
            request.setRequesterId(command.getOperatorId());
        }
    }

    /**
     * 修改路径：按命令非空字段覆盖中药草稿；返回变更字段名列表供事件载荷扩展。
     *
     * @param request 聚合根
     * @param command 修改命令
     * @return 实际变更的字段名列表（当前事件未携带，预留审计扩展）
     */
    private List<String> applyHerbalDraftFieldChanges(MedicationRequest request, ChangeHerbalMedicationOrderDraftCommand command) {
        List<String> changedFields = new ArrayList<>();
        if (command.getMedicationId() != null && !Objects.equals(command.getMedicationId(), request.getMedicationId())) {
            request.setMedicationId(command.getMedicationId());
            changedFields.add(FieldNameConstants.MEDICATION_ID);
        }
        if (command.getChineseHerbsDoseQuantity() != null
            && (request.getChineseHerbsDoseQuantity() == null
            || command.getChineseHerbsDoseQuantity().compareTo(request.getChineseHerbsDoseQuantity()) != 0)) {
            request.setChineseHerbsDoseQuantity(command.getChineseHerbsDoseQuantity());
            changedFields.add("chineseHerbsDoseQuantity");
        }
        if (command.getSufferingFlag() != null && !Objects.equals(command.getSufferingFlag(), request.getSufferingFlag())) {
            request.setSufferingFlag(command.getSufferingFlag());
            changedFields.add("sufferingFlag");
        }
        if (command.getQuantity() != null
            && (request.getQuantity() == null || command.getQuantity().compareTo(request.getQuantity()) != 0)) {
            request.setQuantity(command.getQuantity());
            changedFields.add(FieldNameConstants.QUANTITY);
        }
        if (command.getUnitCode() != null && !Objects.equals(command.getUnitCode(), request.getUnitCode())) {
            request.setUnitCode(command.getUnitCode());
            changedFields.add(FieldNameConstants.UNIT_CODE);
        }
        if (command.getDose() != null
            && (request.getDose() == null || command.getDose().compareTo(request.getDose()) != 0)) {
            request.setDose(command.getDose());
            changedFields.add(FieldNameConstants.DOSE);
        }
        if (command.getDoseUnitCode() != null && !Objects.equals(command.getDoseUnitCode(), request.getDoseUnitCode())) {
            request.setDoseUnitCode(command.getDoseUnitCode());
            changedFields.add(FieldNameConstants.DOSE_UNIT_CODE);
        }
        if (command.getMethodCode() != null && !Objects.equals(command.getMethodCode(), request.getMethodCode())) {
            request.setMethodCode(command.getMethodCode());
            changedFields.add(FieldNameConstants.METHOD_CODE);
        }
        if (command.getRateCode() != null && !Objects.equals(command.getRateCode(), request.getRateCode())) {
            request.setRateCode(command.getRateCode());
            changedFields.add(FieldNameConstants.RATE_CODE);
        }
        if (command.getDispensePerDuration() != null
            && !Objects.equals(command.getDispensePerDuration(), request.getDispensePerDuration())) {
            request.setDispensePerDuration(command.getDispensePerDuration());
            changedFields.add(FieldNameConstants.DISPENSE_PER_DURATION);
        }
        if (command.getContentJson() != null && !Objects.equals(command.getContentJson(), request.getContentJson())) {
            request.setContentJson(command.getContentJson());
            changedFields.add("contentJson");
        }
        if (command.getDosageInstruction() != null
            && !Objects.equals(command.getDosageInstruction(), request.getDosageInstruction())) {
            request.setDosageInstruction(command.getDosageInstruction());
            changedFields.add(FieldNameConstants.DOSAGE_INSTRUCTION);
        }
        if (command.getRemark() != null && !Objects.equals(command.getRemark(), request.getRemark())) {
            request.setRemark(command.getRemark());
            changedFields.add("remark");
        }
        if (command.getConditionDefinitionId() != null
            && !Objects.equals(command.getConditionDefinitionId(), request.getConditionDefinitionId())) {
            request.setConditionDefinitionId(command.getConditionDefinitionId());
            changedFields.add(FieldNameConstants.CONDITION_DEFINITION_ID);
        }
        if (command.getEncounterDiagnosisId() != null
            && !Objects.equals(command.getEncounterDiagnosisId(), request.getEncounterDiagnosisId())) {
            request.setEncounterDiagnosisId(command.getEncounterDiagnosisId());
            changedFields.add(FieldNameConstants.ENCOUNTER_DIAGNOSIS_ID);
        }
        if (command.getContractId() != null && !Objects.equals(command.getContractId(), request.getContractId())) {
            request.setContractId(command.getContractId());
            changedFields.add(FieldNameConstants.CONTRACT_ID);
        }
        if (command.getOrgId() != null && !Objects.equals(command.getOrgId(), request.getOrgId())) {
            request.setOrgId(command.getOrgId());
            changedFields.add(FieldNameConstants.ORG_ID);
        }
        if (command.getPerformLocation() != null
            && !Objects.equals(command.getPerformLocation(), request.getPerformLocation())) {
            request.setPerformLocation(command.getPerformLocation());
            changedFields.add("performLocation");
        }
        if (command.getSortNumber() != null && !Objects.equals(command.getSortNumber(), request.getSortNumber())) {
            request.setSortNumber(command.getSortNumber());
            changedFields.add(FieldNameConstants.SORT_NUMBER);
        }
        if (command.getRxTypeCode() != null && !Objects.equals(command.getRxTypeCode(), request.getRxTypeCode())) {
            request.setRxTypeCode(command.getRxTypeCode());
            changedFields.add("rxTypeCode");
        }
        if (command.getPrescriptionNo() != null
            && !Objects.equals(command.getPrescriptionNo(), request.getPrescriptionNo())) {
            request.setPrescriptionNo(command.getPrescriptionNo());
            changedFields.add("prescriptionNo");
        }
        return changedFields;
    }

    /**
     * 校验源药品申请是否允许作为半退保留链正向克隆源。
     *
     * @param source 源药品申请聚合根
     * @throws BusinessException 软删或业务上不稳定状态（取消/暂挂）时拒绝
     */
    private void assertMedicationRequestCloneableForPreservedChargeRegeneration(MedicationRequest source) {
        if (Boolean.TRUE.equals(source.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "已删除药品申请不允许半退保留链克隆");
        }
        String statusEnum = source.getStatusEnum();
        if (statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "源药品申请状态为空，不允许半退保留链克隆");
        }
        if (RequestStatus.CANCELLED.getCode().equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "已取消药品申请不允许半退保留链克隆");
        }
        if (RequestStatus.HOLD.getCode().equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "暂挂药品申请不允许半退保留链克隆");
        }
    }

    /**
     * 由源药品申请组装半退保留链用的新聚合根：业务字段复刻、技术字段重置、草稿初始化与追溯锚点。
     *
     * @param source    源药品申请
     * @param operatorId 当前操作人，写入 recorder/requester
     * @return 未持久化的新实体（已含 {@code bus_no}）
     */
    private MedicationRequest buildMedicationRequestCloneFromSourceForPreservedChargeRegeneration(MedicationRequest source, Long operatorId) {
        MedicationRequest out = new MedicationRequest();
        // Pre：业务号与创建路径一致，按日序列生成，禁止调用方外灌污染唯一性。
        out.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        out.setPatientId(source.getPatientId());
        out.setEncounterId(source.getEncounterId());
        out.setMedicationId(source.getMedicationId());
        out.setOrgId(source.getOrgId());
        out.setLocationId(source.getLocationId());
        out.setPerformLocation(source.getPerformLocation());
        out.setPerformOrg(source.getPerformOrg());
        out.setPerformerEnum(source.getPerformerEnum());
        out.setPerformerId(source.getPerformerId());
        out.setDeviceDefId(source.getDeviceDefId());
        out.setGroupId(source.getGroupId());
        out.setPackageId(source.getPackageId());
        out.setIntentEnum(source.getIntentEnum());
        out.setCategoryEnum(source.getCategoryEnum());
        out.setPriorityEnum(source.getPriorityEnum());
        out.setPerformFlag(source.getPerformFlag());
        out.setGenerateSourceEnum(source.getGenerateSourceEnum());
        out.setTherapyEnum(source.getTherapyEnum());
        out.setQuantity(source.getQuantity());
        out.setExecuteNum(source.getExecuteNum());
        out.setUnitCode(source.getUnitCode());
        out.setMethodCode(source.getMethodCode());
        out.setRateCode(source.getRateCode());
        out.setDose(source.getDose());
        out.setDoseUnitCode(source.getDoseUnitCode());
        out.setMaxDose(source.getMaxDose());
        out.setFirstDose(source.getFirstDose());
        out.setFirstDoseUnitCode(source.getFirstDoseUnitCode());
        out.setFirstDuration(source.getFirstDuration());
        out.setDispenseInterval(source.getDispenseInterval());
        out.setDispensePerQuantity(source.getDispensePerQuantity());
        out.setDispensePerDuration(source.getDispensePerDuration());
        out.setLotNumber(source.getLotNumber());
        out.setTraceNo(source.getTraceNo());
        out.setSkinTestFlag(source.getSkinTestFlag());
        out.setInfusionFlag(source.getInfusionFlag());
        out.setSpeedCode(source.getSpeedCode());
        out.setContractId(source.getContractId());
        out.setConditionDefinitionId(source.getConditionDefinitionId());
        out.setEncounterDiagnosisId(source.getEncounterDiagnosisId());
        out.setEffectiveDoseStart(source.getEffectiveDoseStart());
        out.setEffectiveDoseEnd(source.getEffectiveDoseEnd());
        out.setRxTypeCode(source.getRxTypeCode());
        out.setYbClassEnum(source.getYbClassEnum());
        out.setDosageInstruction(source.getDosageInstruction());
        out.setTcmFlag(source.getTcmFlag());
        out.setContentJson(source.getContentJson());
        out.setChineseHerbsDoseQuantity(source.getChineseHerbsDoseQuantity());
        out.setSufferingFlag(source.getSufferingFlag());
        out.setSortNumber(source.getSortNumber());
        out.setRemark(source.getRemark());
        out.setReportFlag(source.getReportFlag());
        out.setSupportInfo(source.getSupportInfo());
        out.setReasonJson(source.getReasonJson());
        out.setPrescriptionNo(null);
        out.setRefundMedicineId(null);
        out.setPerformerCheckId(null);
        out.setCheckTime(null);
        out.setStatusReason(null);
        out.setBasedOnTable(TableNameConstants.MED_MEDICATION_REQUEST);
        out.setBasedOnId(source.getId());
        out.setRecorderId(operatorId);
        out.setRequesterId(operatorId);
        out.setReqAuthoredTime(LocalDateTime.now());
        out.setIsDeleted(Boolean.FALSE);
        out.initDraft();
        return out;
    }

}
