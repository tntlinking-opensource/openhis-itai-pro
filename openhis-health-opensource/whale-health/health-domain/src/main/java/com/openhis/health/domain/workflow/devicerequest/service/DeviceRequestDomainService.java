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

package com.openhis.health.domain.workflow.devicerequest.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.events.workflow.devicerequest.DeviceOrderDraftCreatedEvent;
import com.openhis.health.domain.share.events.workflow.devicerequest.DeviceOrderDraftEditedEvent;
import com.openhis.health.domain.share.events.workflow.devicerequest.DeviceOrderDraftRemovedEvent;
import com.openhis.health.domain.share.events.workflow.devicerequest.DeviceOrderRefundRequestedEvent;
import com.openhis.health.domain.share.events.workflow.devicerequest.DeviceOrderSignOffEvent;
import com.openhis.health.domain.share.events.workflow.devicerequest.DeviceOrderSignedEvent;
import com.openhis.health.domain.share.events.workflow.devicerequest.NursePricingDeviceRequestCreatedEvent;
import com.openhis.health.domain.share.events.workflow.devicerequest.OutpatientDeviceDispenseConfirmationCancelledEvent;
import com.openhis.health.domain.share.events.workflow.devicerequest.OutpatientDeviceDispenseConfirmedEvent;
import com.openhis.health.domain.workflow.devicerequest.command.ChangeDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CloneDeviceRequestForPreservedChargeRegenerationCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateDeviceOrderRefundCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.command.ExecuteDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.command.RemoveDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.RevokeDeviceOrderExecutionCommand;
import com.openhis.health.domain.workflow.devicerequest.command.RevokeExecutedDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.command.SignDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.command.SignOffDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import com.openhis.health.domain.workflow.devicerequest.repository.DeviceRequestRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 耗材请求写侧领域服务。
 *
 * <p>边界：库存/发放/收费等跨域一致性由应用服务编排；本服务只写本聚合并发布领域事件。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceRequestDomainService {

    private final DeviceRequestRepository deviceRequestRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 耗材请求 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#DEVICE_REQUEST}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 创建耗材草稿。
     */
    public Long createDeviceOrderDraft(CreateDeviceOrderDraftCommand command) {
        List<CreateDeviceOrderDraftCommand.CreateDeviceOrderDraftItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "创建耗材草稿一次仅允许提交一条记录");
        }

        CreateDeviceOrderDraftCommand.CreateDeviceOrderDraftItemCommand item = items.get(0);
        DeviceRequest request = new DeviceRequest();
        request.setDeviceDefId(item.getDeviceDefId());
        request.setQuantity(item.getQuantity());
        request.setUnitCode(item.getUnitCode());
        request.setEncounterId(item.getEncounterId());
        request.setPatientId(item.getPatientId());
        // 开嘱人统一以后端当前操作人为准，前端无需传递。
        request.setRequesterId(command.getOperatorId());
        request.setPerformLocation(item.getPerformLocation());
        // Pre：耗材草稿须落库生成来源；医生站未传时回落医生开立。
        String generateSourceCode = item.getGenerateSourceEnum();
        if (generateSourceCode == null) {
            generateSourceCode = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        }
        request.setGenerateSourceEnum(generateSourceCode);
        if (item.getSortNumber() != null) {
            request.setSortNumber(item.getSortNumber());
        }
        // 医生站：可选绑定诊断定义，与药品医嘱口径一致（单列 condition_definition_id）。
        if (item.getConditionDefinitionId() != null) {
            request.setConditionDefinitionId(item.getConditionDefinitionId());
        }
        // Pre：绑定就诊诊断行，供收费/医保按 encounter_diagnosis_id 解析医疗类别。
        if (item.getEncounterDiagnosisId() != null) {
            request.setEncounterDiagnosisId(item.getEncounterDiagnosisId());
        }
        request.initDraft();
        request.setIsDeleted(Boolean.FALSE);
        request.setReqAuthoredTime(LocalDateTime.now());
        request.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.DEVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        request.assertRequiredForCreate();

        // DOC-CONFLICT: BR-DR-001 库存相关前置校验需跨域读取库存，不在领域层直接实现；由应用服务在调用前完成。

        DeviceRequest saved = deviceRequestRepository.save(request);
        domainEventPublisher.publish(new DeviceOrderDraftCreatedEvent(
            saved.getId(), saved.getDeviceDefId(), saved.getEncounterId(), saved.getQuantity()));
        return saved.getId();
    }

    /**
     * 修改耗材草稿。
     */
    public void changeDeviceOrderDraft(ChangeDeviceOrderDraftCommand command) {
        List<ChangeDeviceOrderDraftCommand.ChangeDeviceOrderDraftItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "修改耗材草稿一次仅允许提交一条记录");
        }

        ChangeDeviceOrderDraftCommand.ChangeDeviceOrderDraftItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "修改耗材草稿时 id 不能为空");
        }

        DeviceRequest request = this.requireDeviceRequest(item.getId());
        request.assertDraftEditable();

        List<String> changedFields = new ArrayList<>();

        // 仅草稿可编辑字段集合：逐字段比较新旧值并累积变更摘要。
        if (item.getQuantity() != null) {
            if (request.getQuantity() == null || !item.getQuantity().equals(request.getQuantity())) {
                changedFields.add(FieldNameConstants.QUANTITY);
            }
            request.setQuantity(item.getQuantity());
        }
        // 业务分支处理。
        if (item.getUnitCode() != null) {
            if (!item.getUnitCode().equals(request.getUnitCode())) {
                changedFields.add(FieldNameConstants.UNIT_CODE);
            }
            request.setUnitCode(item.getUnitCode());
        }
        // 业务分支处理。
        if (item.getPerformLocation() != null) {
            if (!item.getPerformLocation().equals(request.getPerformLocation())) {
                changedFields.add(FieldNameConstants.PERFORM_LOCATION);
            }
            request.setPerformLocation(item.getPerformLocation());
        }
        // 业务分支处理。
        if (item.getLotNumber() != null) {
            if (!item.getLotNumber().equals(request.getLotNumber())) {
                changedFields.add(FieldNameConstants.LOT_NUMBER);
            }
            request.setLotNumber(item.getLotNumber());
        }
        // 业务分支处理。
        if (item.getTraceNo() != null) {
            if (!item.getTraceNo().equals(request.getTraceNo())) {
                changedFields.add(FieldNameConstants.TRACE_NO);
            }
            request.setTraceNo(item.getTraceNo());
        }
        // 业务分支处理。
        if (item.getRemark() != null) {
            if (!item.getRemark().equals(request.getRemark())) {
                changedFields.add(FieldNameConstants.REMARK);
            }
            request.setRemark(item.getRemark());
        }
        // 业务分支处理。
        if (item.getSortNumber() != null && !Objects.equals(item.getSortNumber(), request.getSortNumber())) {
            changedFields.add(FieldNameConstants.SORT_NUMBER);
            request.setSortNumber(item.getSortNumber());
        }
        // 业务分支处理。
        if (item.getUseTime() != null) {
            if (!item.getUseTime().equals(request.getUseTime())) {
                changedFields.add(FieldNameConstants.USE_TIME);
            }
            request.setUseTime(item.getUseTime());
        }
        // 业务分支处理。
        if (item.getUseStartTime() != null) {
            if (!item.getUseStartTime().equals(request.getUseStartTime())) {
                changedFields.add(FieldNameConstants.USE_START_TIME);
            }
            request.setUseStartTime(item.getUseStartTime());
        }
        // 业务分支处理。
        if (item.getUseEndTime() != null) {
            if (!item.getUseEndTime().equals(request.getUseEndTime())) {
                changedFields.add(FieldNameConstants.USE_END_TIME);
            }
            request.setUseEndTime(item.getUseEndTime());
        }
        // 业务分支处理。
        if (item.getUseTiming() != null) {
            if (!item.getUseTiming().equals(request.getUseTiming())) {
                changedFields.add(FieldNameConstants.USE_TIMING);
            }
            request.setUseTiming(item.getUseTiming());
        }
        // 业务分支处理：诊断定义绑定与药品医嘱一致，仅当命令显式传入且值变化时落库并记入变更摘要。
        if (item.getConditionDefinitionId() != null
            && !Objects.equals(item.getConditionDefinitionId(), request.getConditionDefinitionId())) {
            request.setConditionDefinitionId(item.getConditionDefinitionId());
            changedFields.add(FieldNameConstants.CONDITION_DEFINITION_ID);
        }
        // 业务分支处理：就诊诊断绑定与药品医嘱一致，仅当命令显式传入且值变化时落库并记入变更摘要。
        if (item.getEncounterDiagnosisId() != null
            && !Objects.equals(item.getEncounterDiagnosisId(), request.getEncounterDiagnosisId())) {
            request.setEncounterDiagnosisId(item.getEncounterDiagnosisId());
            changedFields.add(FieldNameConstants.ENCOUNTER_DIAGNOSIS_ID);
        }

        request.assertRequiredForCreate();

        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new DeviceOrderDraftEditedEvent(
            request.getId(), String.join(",", changedFields)));
    }

    /**
     * 删除耗材草稿（逻辑删除）。
     */
    public void removeDeviceOrderDraft(RemoveDeviceOrderDraftCommand command) {
        List<RemoveDeviceOrderDraftCommand.RemoveDeviceOrderDraftItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "删除耗材草稿一次仅允许提交一条记录");
        }

        RemoveDeviceOrderDraftCommand.RemoveDeviceOrderDraftItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "删除耗材草稿时 id 不能为空");
        }

        DeviceRequest request = this.requireDeviceRequest(item.getId());
        request.assertDraftEditable();

        // DOC-CONFLICT: BR-DR-002 已收费不可删需要费用域校验；由应用服务在调用前完成。

        request.setIsDeleted(Boolean.TRUE);
        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new DeviceOrderDraftRemovedEvent(request.getId(), request.getEncounterId()));
    }

    /**
     * 签发耗材请求。
     */
    public void signDeviceOrder(SignDeviceOrderCommand command) {
        List<SignDeviceOrderCommand.SignDeviceOrderItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "签发耗材请求一次仅允许提交一条记录");
        }

        SignDeviceOrderCommand.SignDeviceOrderItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "签发耗材请求时 id 不能为空");
        }

        DeviceRequest request = this.requireDeviceRequest(item.getId());
        request.assertSignable();

        // DOC-CONFLICT: 库存与费用门控由应用服务编排完成，领域层不跨域读取。

        // 与诊疗服务申请签发写入 authored_time 对齐：正式开立时刻在签发时落库，覆盖草稿首次保存时间。
        request.setReqAuthoredTime(LocalDateTime.now());
        request.draftToActive();
        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new DeviceOrderSignedEvent(request.getId(), request.getStatusEnum()));
    }

    /**
     * 签退耗材请求。
     */
    public void signOffDeviceOrder(SignOffDeviceOrderCommand command) {
        List<SignOffDeviceOrderCommand.SignOffDeviceOrderItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "签退耗材请求一次仅允许提交一条记录");
        }

        SignOffDeviceOrderCommand.SignOffDeviceOrderItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "签退耗材请求时 id 不能为空");
        }

        DeviceRequest request = this.requireDeviceRequest(item.getId());
        request.assertSignOffAllowed();

        // DOC-CONFLICT: BR-DR-003 已收费不可签退需费用域校验；由应用服务在调用前完成。

        request.activeToDraft();
        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new DeviceOrderSignOffEvent(request.getId(), request.getStatusEnum()));
    }

    /**
     * 执行确认。
     */
    /**
     * 门诊收费完成：已发送耗材请求推进为已完成，并发布门诊发放确认事件（执行机构可空）。
     *
     * @param deviceRequestId 耗材请求主键
     * @param operatorId      收费操作人，写入事件 performerId
     */
    public void completeDeviceRequestAfterOutpatientCharge(Long deviceRequestId, Long operatorId) {
        DeviceRequest request = this.requireDeviceRequest(deviceRequestId);
        if (RequestStatus.COMPLETED.getCode().equals(request.getStatusEnum())) {
            return;
        }
        request.assertCompletableAfterOutpatientCharge();
        request.toCompleted();
        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new OutpatientDeviceDispenseConfirmedEvent(deviceRequestId, operatorId, null));
    }

    public void executeDeviceOrder(ExecuteDeviceOrderCommand command) {
        List<ExecuteDeviceOrderCommand.ExecuteDeviceOrderItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "执行确认一次仅允许提交一条记录");
        }

        ExecuteDeviceOrderCommand.ExecuteDeviceOrderItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "执行确认时 id 不能为空");
        }

        DeviceRequest request = this.requireDeviceRequest(item.getId());
        request.assertExecutable();

        request.setPerformerId(item.getPerformerId());
        request.setPerformOrgId(item.getPerformOrgId());
        request.toCompleted();
        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new OutpatientDeviceDispenseConfirmedEvent(
            request.getId(), request.getPerformerId(), request.getPerformOrgId()));
    }

    /**
     * 撤销执行。
     */
    public void revokeDeviceOrderExecution(RevokeDeviceOrderExecutionCommand command) {
        List<RevokeDeviceOrderExecutionCommand.RevokeDeviceOrderExecutionItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "撤销执行一次仅允许提交一条记录");
        }

        RevokeDeviceOrderExecutionCommand.RevokeDeviceOrderExecutionItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "撤销执行时 id 不能为空");
        }

        DeviceRequest request = this.requireDeviceRequest(item.getId());
        // 委托聚合根 assertCompleted() 校验撤销执行前置条件，遵守 §D3.2 硬门禁。
        request.assertCompleted();
        // DOC-CONFLICT: 撤销后目标状态由用例编排决定；领域层仅发布撤销事件并保留状态待应用层回写。
        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new OutpatientDeviceDispenseConfirmationCancelledEvent(request.getId()));
    }

    /**
     * 申请退耗材（创建负向单）。
     */
    public Long createDeviceOrderRefund(CreateDeviceOrderRefundCommand command) {
        List<CreateDeviceOrderRefundCommand.CreateDeviceOrderRefundItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "申请退耗材一次仅允许提交一条记录");
        }

        CreateDeviceOrderRefundCommand.CreateDeviceOrderRefundItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getSourceRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "申请退耗材时 sourceRequestId 不能为空");
        }
        // 业务分支处理。
        if (item.getRefundQuantity() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "申请退耗材时 refundQuantity 不能为空");
        }
        // 业务分支处理。
        if (item.getRefundQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "申请退耗材时 refundQuantity 必须大于 0");
        }

        DeviceRequest source = this.requireDeviceRequest(item.getSourceRequestId());

        // 冲销校验（BR-DR-011）：严格使用 refundDeviceId + quantity<0 汇总口径。
        BigDecimal remaining = this.offsetVerification(source.getId(), item.getRefundQuantity());
        // 业务分支处理。
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_RULE_CONFLICT, "退耗材冲销数量超额");
        }

        DeviceRequest refund = new DeviceRequest();
        refund.setDeviceDefId(source.getDeviceDefId());
        refund.setEncounterId(source.getEncounterId());
        refund.setPatientId(source.getPatientId());
        refund.setUnitCode(source.getUnitCode());
        refund.setQuantity(item.getRefundQuantity().negate());
        refund.setRefundDeviceId(source.getId());
        refund.setRemark(item.getReason());
        refund.setIsDeleted(Boolean.FALSE);
        refund.setStatusEnum(RequestStatus.CANCELLED.getCode());
        refund.markAsRefundFromPrescription(source.getPrescriptionNo());
        refund.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.DEVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));

        DeviceRequest saved = deviceRequestRepository.save(refund);
        domainEventPublisher.publish(new DeviceOrderRefundRequestedEvent(saved.getId(), saved.getRefundDeviceId()));
        return saved.getId();
    }

    /**
     * 门诊药房退药闭环：将负向耗材申请推进为已完成。
     *
     * @param requestId 负向耗材申请主键
     */
    public void completeDeviceRequestAfterRefund(Long requestId) {
        DeviceRequest request = this.requireDeviceRequest(requestId);
        request.ensureCompletableAfterPharmacyRefund();
        request.toCompletedAfterPharmacyRefund();
        deviceRequestRepository.save(request);
    }

    /**
     * 生成已完成请求（执行侧直写完成态，支持批量）。
     *
     * <p>护士划价/补费路径可携带 {@code basedOnTable}/{@code basedOnId} 写入上级医嘱锚点。</p>
     *
     * @param command 批次命令；每行一条耗材完成态请求
     * @return 新请求主键列表（顺序与入参一致）
     * @throws BusinessException 参数非法、父锚点不成对或护士划价缺库房时
     */
    public List<Long> createExecutedDeviceOrder(CreateExecutedDeviceOrderCommand command) {
        List<CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand> items = command.resolveItems();
        // 批量完成态建单须至少一行。
        if (items.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT,
                "生成已完成请求至少须提交一条记录");
        }
        List<DeviceRequest> toSave = new ArrayList<>();
        LocalDateTime authoredTime = LocalDateTime.now();
        for (CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand item : items) {
            boolean hasBasedOnTable = StringUtils.isNotBlank(item.getBasedOnTable());
            boolean hasBasedOnId = item.getBasedOnId() != null;
            // 父锚点表名与主键须成对出现。
            if (hasBasedOnTable != hasBasedOnId) {
                throw new BusinessException(
                    ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT,
                    "生成已完成请求时 basedOnTable 与 basedOnId 须同时为空或同时有值");
            }
            DeviceRequest request = new DeviceRequest();
            request.setGenerateSourceEnum(item.getGenerateSourceEnum());
            request.setDeviceDefId(item.getDeviceDefId());
            request.setQuantity(item.getQuantity());
            request.setUnitCode(item.getUnitCode());
            request.setEncounterId(item.getEncounterId());
            request.setPatientId(item.getPatientId());
            // 执行侧补单同样以后端当前操作人为开嘱人兜底。
            request.setRequesterId(command.getOperatorId());
            request.setPerformLocation(item.getPerformLocation());
            if (hasBasedOnTable) {
                request.setBasedOnTable(item.getBasedOnTable());
                request.setBasedOnId(item.getBasedOnId());
            }
            request.setIsDeleted(Boolean.FALSE);
            request.setBusNo(businessSequenceService.getSeqByDay(
                AggregateTypeConstants.DEVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
            // 执行侧直建完成单无草稿阶段，开立时间与列表展示锚定为创建时刻。
            request.setReqAuthoredTime(authoredTime);
            request.assertSourceAllowedForCompletedCreate();
            request.assertRequiredForCreate();
            // BR-DR-012：护士划价路径库房必填。
            if (item.getGenerateSourceEnum() != null
                && GenerateSource.NURSE_PRICING.getCode().equals(item.getGenerateSourceEnum())) {
                if (request.getPerformLocation() == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT,
                        "护士划价路径发放库房 performLocation 不能为空");
                }
            }
            request.toCompleted();
            toSave.add(request);
        }
        List<DeviceRequest> saved = deviceRequestRepository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        for (DeviceRequest row : saved) {
            ids.add(row.getId());
            if (row.getGenerateSourceEnum() != null
                && GenerateSource.NURSE_PRICING.getCode().equals(row.getGenerateSourceEnum())) {
                domainEventPublisher.publish(new NursePricingDeviceRequestCreatedEvent(
                    row.getId(),
                    row.getEncounterId(),
                    row.getPatientId(),
                    row.getDeviceDefId(),
                    row.getQuantity(),
                    row.getUnitCode(),
                    row.getPerformLocation(),
                    row.getBasedOnTable(),
                    row.getBasedOnId()));
            }
        }
        return ids;
    }

    /**
     * 撤销已完成请求（对执行侧直写完成态进行撤销/软删收口）。
     */
    public void revokeExecutedDeviceOrder(RevokeExecutedDeviceOrderCommand command) {
        List<RevokeExecutedDeviceOrderCommand.RevokeExecutedDeviceOrderItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "撤销已完成请求一次仅允许提交一条记录");
        }

        RevokeExecutedDeviceOrderCommand.RevokeExecutedDeviceOrderItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "撤销已完成请求时 id 不能为空");
        }

        DeviceRequest request = this.requireDeviceRequest(item.getId());
        // 委托聚合根 assertCompleted() 校验撤销请求前置条件，遵守 §D3.2 硬门禁。
        request.assertCompleted();
        // DOC-CONFLICT: 下游引用判定与跨域回滚由应用服务编排；领域层仅做软删收口占位。
        request.setIsDeleted(Boolean.TRUE);
        deviceRequestRepository.save(request);
    }

    /**
     * 半退保留链：按源耗材请求事实在本聚合内正向克隆新请求（DM-WKF-003 §6.1.11）。
     *
     * <p>复刻可对齐的业务字段；签发与退费链字段清空；状态回落草稿；{@code based_on_table}/{@code based_on_id} 指向源行；
     * 新 {@code bus_no} 按日取号；先 {@code save} 后发布 {@link DeviceOrderDraftCreatedEvent}（B.14）。</p>
     *
     * @param command 克隆入参，须含源主键与操作人
     * @return 新耗材请求主键
     * @throws BusinessException 入参非法或源不可克隆时
     */
    public Long cloneDeviceRequestForPreservedChargeRegeneration(CloneDeviceRequestForPreservedChargeRegenerationCommand command) {
        if (command == null || command.getSourceDeviceRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "半退保留链克隆入参不能为空");
        }
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "半退保留链克隆操作人不能为空");
        }
        DeviceRequest source = this.requireDeviceRequest(command.getSourceDeviceRequestId());
        this.assertDeviceRequestCloneableForPreservedChargeRegeneration(source);
        DeviceRequest cloned = this.buildDeviceRequestCloneFromSourceForPreservedChargeRegeneration(source, command.getOperatorId());
        cloned.assertRequiredForCreate();
        DeviceRequest saved = deviceRequestRepository.save(cloned);
        domainEventPublisher.publish(new DeviceOrderDraftCreatedEvent(
            saved.getId(), saved.getDeviceDefId(), saved.getEncounterId(), saved.getQuantity()));
        return saved.getId();
    }

    /**
     * 校验源耗材请求是否允许作为半退保留链正向克隆源。
     *
     * @param source 源耗材请求
     * @throws BusinessException 软删或取消/暂挂等不稳定状态时拒绝
     */
    private void assertDeviceRequestCloneableForPreservedChargeRegeneration(DeviceRequest source) {
        if (Boolean.TRUE.equals(source.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_RULE_CONFLICT, "已删除耗材请求不允许半退保留链克隆");
        }
        String statusEnum = source.getStatusEnum();
        if (statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_RULE_CONFLICT, "源耗材请求状态为空，不允许半退保留链克隆");
        }
        if (RequestStatus.CANCELLED.getCode().equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_RULE_CONFLICT, "已取消耗材请求不允许半退保留链克隆");
        }
        if (RequestStatus.HOLD.getCode().equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_RULE_CONFLICT, "暂挂耗材请求不允许半退保留链克隆");
        }
    }

    /**
     * 由源耗材请求组装半退保留链用的新实体：字段复刻、草稿初始化、追溯锚点与新业务号。
     *
     * @param source     源行
     * @param operatorId 操作人，写入 requester
     * @return 未持久化的新实体
     */
    private DeviceRequest buildDeviceRequestCloneFromSourceForPreservedChargeRegeneration(DeviceRequest source, Long operatorId) {
        DeviceRequest out = new DeviceRequest();
        out.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.DEVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        out.setActivityId(source.getActivityId());
        out.setPackageId(source.getPackageId());
        out.setIntentCode(source.getIntentCode());
        out.setCategoryEnum(source.getCategoryEnum());
        out.setPriorityEnum(source.getPriorityEnum());
        out.setPerformFlag(source.getPerformFlag());
        out.setGroupNo(source.getGroupNo());
        out.setDeviceTypeCode(source.getDeviceTypeCode());
        out.setQuantity(source.getQuantity());
        out.setUnitCode(source.getUnitCode());
        out.setLotNumber(source.getLotNumber());
        out.setDeviceDefId(source.getDeviceDefId());
        out.setDeviceSpecifications(source.getDeviceSpecifications());
        out.setOrgId(source.getOrgId());
        out.setLocationId(source.getLocationId());
        out.setPerformLocation(source.getPerformLocation());
        out.setEncounterId(source.getEncounterId());
        out.setPatientId(source.getPatientId());
        out.setRateCode(source.getRateCode());
        out.setUseTime(source.getUseTime());
        out.setUseStartTime(source.getUseStartTime());
        out.setUseEndTime(source.getUseEndTime());
        out.setUseTiming(source.getUseTiming());
        out.setPerformerEnum(source.getPerformerEnum());
        out.setPerformerId(source.getPerformerId());
        out.setPerformOrgId(source.getPerformOrgId());
        out.setAsNeedFlag(source.getAsNeedFlag());
        out.setAsNeedReason(source.getAsNeedReason());
        out.setContractCode(source.getContractCode());
        out.setYbClassEnum(source.getYbClassEnum());
        out.setTraceNo(source.getTraceNo());
        out.setConditionDefinitionId(source.getConditionDefinitionId());
        out.setEncounterDiagnosisId(source.getEncounterDiagnosisId());
        out.setObservationIdJson(source.getObservationIdJson());
        out.setSupportInfo(source.getSupportInfo());
        out.setGenerateSourceEnum(source.getGenerateSourceEnum());
        out.setSortNumber(source.getSortNumber());
        out.setRemark(source.getRemark());
        out.setPrescriptionNo(null);
        out.setRefundDeviceId(null);
        out.setBasedOnTable(TableNameConstants.WKF_DEVICE_REQUEST);
        out.setBasedOnId(source.getId());
        out.setRequesterId(operatorId);
        out.setReqAuthoredTime(LocalDateTime.now());
        out.setIsDeleted(Boolean.FALSE);
        out.initDraft();
        return out;
    }

    /**
     * 私有方法：requireDeviceRequest。
     *
     * <p>按主键加载耗材请求，不存在则抛出业务异常。</p>
     *
     * @param id 耗材请求主键
     * @return 耗材请求实体
     * @throws BusinessException 耗材请求不存在时
     */
    private DeviceRequest requireDeviceRequest(Long id) {
        return deviceRequestRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在"));
    }

    /**
     * 冲销校验剩余可退数量（仅本聚合仓储判定）。
     */
    private BigDecimal offsetVerification(Long sourceRequestId, BigDecimal preOffsetQuantity) {
        if (sourceRequestId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "冲销校验时 sourceRequestId 不能为空");
        }
        if (preOffsetQuantity == null) {
            preOffsetQuantity = BigDecimal.ZERO;
        }
        DeviceRequest source = this.requireDeviceRequest(sourceRequestId);
        List<DeviceRequest> negativeRows = deviceRequestRepository.findByRefundDeviceIdAndQuantityLessThan(
            sourceRequestId, BigDecimal.ZERO);
        BigDecimal offsetQuantity = BigDecimal.ZERO;
        for (DeviceRequest row : negativeRows) {
            if (row.getQuantity() == null) {
                continue;
            }
            offsetQuantity = offsetQuantity.add(row.getQuantity().abs());
        }
        return source.getQuantity().subtract(offsetQuantity).subtract(preOffsetQuantity);
    }
}
