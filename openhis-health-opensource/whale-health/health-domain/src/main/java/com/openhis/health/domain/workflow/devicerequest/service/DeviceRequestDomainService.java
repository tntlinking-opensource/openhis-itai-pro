/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.service;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.events.workflow.devicerequest.*;
import com.openhis.health.domain.workflow.devicerequest.command.*;
import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import com.openhis.health.domain.workflow.devicerequest.repository.DeviceRequestRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 耗材请求（{@code wkf_device_request}）聚合写侧领域服务。
 *
 * <p>职责：耗材医嘱草稿、签发/签退、执行回写、护士划价来源建单及门诊发放确认。</p>
 * <p>不变量：状态迁移门禁；持久化先于耗材请求领域事件（B.14）。</p>
 * <p>仅注入本聚合 {@link DeviceRequestRepository}；库存/发放/收费由应用层编排。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceRequestDomainService {

    private final DeviceRequestRepository deviceRequestRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 耗材请求 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#DEVICE_REQUEST}。 */
    private final BusinessSequenceService businessSequenceService;
    /** 加载候选：经 Encounter QS 断言绑定 ids（B.6.4）。 */
    private final EncounterQueryService encounterQueryService;

    /**
     * 创建耗材草稿。
     */
    public Long createDeviceOrderDraft(CreateDeviceOrderDraftCommand command) {
        List<CreateDeviceOrderDraftCommand.CreateDeviceOrderDraftItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
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
        // 医生站：可选绑定就诊诊断有序 ids 串，与药品医嘱口径一致（列 encounter_diagnosis_ids）。
        if (item.getEncounterDiagnosisIds() != null) {
            // 前置校验：属本就诊、同族、主诊首位
            encounterQueryService.assertBindableEncounterDiagnosisIds(
                item.getEncounterId(), item.getEncounterDiagnosisIds());
            request.setEncounterDiagnosisIds(item.getEncounterDiagnosisIds());
        }
        // Pre：用法/诊疗绑定派生须成对落 based_on，供列表反查与成组共用追溯（对齐住院执行绑定）。
        boolean hasBasedOnTable = StringUtils.isNotEmpty(item.getBasedOnTable());
        boolean hasBasedOnId = item.getBasedOnId() != null;
        if (hasBasedOnTable != hasBasedOnId) {
            throw new BusinessException(
                ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT,
                "创建耗材草稿时 basedOnTable 与 basedOnId 须同时为空或同时有值");
        }
        if (hasBasedOnTable) {
            request.setBasedOnTable(item.getBasedOnTable().trim());
            request.setBasedOnId(item.getBasedOnId());
        }
        // Pre：成组绑定耗材可与主药嘱 package_id 对齐（可空）。
        if (item.getPackageId() != null) {
            request.setPackageId(item.getPackageId());
        }
        // Pre：费用性质合同 bus_no 落 contract_code，供列表反查与挂账回显。
        if (item.getContractCode() != null) {
            request.setContractCode(item.getContractCode());
        }
        request.initDraft();
        request.setIsDeleted(Boolean.FALSE);
        // Pre：开立时间仅首次暂存写入；签发时刻由 sign 写 req_authored_time。
        request.setReqOpenedTime(LocalDateTime.now());
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
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "修改耗材草稿一次仅允许提交一条记录");
        }

        ChangeDeviceOrderDraftCommand.ChangeDeviceOrderDraftItemCommand item = items.get(0);
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
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
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getUnitCode() != null) {
            if (!item.getUnitCode().equals(request.getUnitCode())) {
                changedFields.add(FieldNameConstants.UNIT_CODE);
            }
            request.setUnitCode(item.getUnitCode());
        }
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getPerformLocation() != null) {
            if (!item.getPerformLocation().equals(request.getPerformLocation())) {
                changedFields.add(FieldNameConstants.PERFORM_LOCATION);
            }
            request.setPerformLocation(item.getPerformLocation());
        }
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getLotNumber() != null) {
            if (!item.getLotNumber().equals(request.getLotNumber())) {
                changedFields.add(FieldNameConstants.LOT_NUMBER);
            }
            request.setLotNumber(item.getLotNumber());
        }
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getTraceNo() != null) {
            if (!item.getTraceNo().equals(request.getTraceNo())) {
                changedFields.add(FieldNameConstants.TRACE_NO);
            }
            request.setTraceNo(item.getTraceNo());
        }
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getRemark() != null) {
            if (!item.getRemark().equals(request.getRemark())) {
                changedFields.add(FieldNameConstants.REMARK);
            }
            request.setRemark(item.getRemark());
        }
        // 业务条件分支：见相邻判断的业务后果
        if (item.getSortNumber() != null && !Objects.equals(item.getSortNumber(), request.getSortNumber())) {
            changedFields.add(FieldNameConstants.SORT_NUMBER);
            request.setSortNumber(item.getSortNumber());
        }
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getUseTime() != null) {
            if (!item.getUseTime().equals(request.getUseTime())) {
                changedFields.add(FieldNameConstants.USE_TIME);
            }
            request.setUseTime(item.getUseTime());
        }
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getUseStartTime() != null) {
            if (!item.getUseStartTime().equals(request.getUseStartTime())) {
                changedFields.add(FieldNameConstants.USE_START_TIME);
            }
            request.setUseStartTime(item.getUseStartTime());
        }
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getUseEndTime() != null) {
            if (!item.getUseEndTime().equals(request.getUseEndTime())) {
                changedFields.add(FieldNameConstants.USE_END_TIME);
            }
            request.setUseEndTime(item.getUseEndTime());
        }
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getUseTiming() != null) {
            if (!item.getUseTiming().equals(request.getUseTiming())) {
                changedFields.add(FieldNameConstants.USE_TIMING);
            }
            request.setUseTiming(item.getUseTiming());
        }
        // 就诊诊断有序 ids 串 绑定与药品医嘱一致，仅当命令显式传入且值变化时落库并记入变更摘要。
        if (item.getEncounterDiagnosisIds() != null
            && !Objects.equals(item.getEncounterDiagnosisIds(), request.getEncounterDiagnosisIds())) {
            // 前置校验：改绑时同口径门禁
            encounterQueryService.assertBindableEncounterDiagnosisIds(
                request.getEncounterId(), item.getEncounterDiagnosisIds());
            request.setEncounterDiagnosisIds(item.getEncounterDiagnosisIds());
            changedFields.add(FieldNameConstants.ENCOUNTER_DIAGNOSIS_IDS);
        }
        // 费用性质合同 bus_no：命令显式传入时覆盖 contract_code。
        if (item.getContractCode() != null
            && !Objects.equals(item.getContractCode(), request.getContractCode())) {
            request.setContractCode(item.getContractCode());
            changedFields.add(FieldNameConstants.CONTRACT_CODE);
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
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "删除耗材草稿一次仅允许提交一条记录");
        }

        RemoveDeviceOrderDraftCommand.RemoveDeviceOrderDraftItemCommand item = items.get(0);
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
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
     * 废弃耗材草稿：DRAFT → CANCELLED（保留行，不软删；调价在途配套）。
     *
     * <p>已非草稿（如并发已签发）时静默跳过，避免误伤医嘱。</p>
     *
     * @param command 草稿主键与操作人
     * @return 实际废弃时 true；已非草稿时 false
     */
    public boolean cancelDeviceOrderDraft(CancelDeviceOrderDraftCommand command) {
        // 前置校验：主键不可空
        if (command == null || command.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "废弃耗材草稿时 id 不能为空");
        }
        // 加载/组装：装载申请
        DeviceRequest request = this.requireDeviceRequest(command.getId());
        // Gate：已非草稿或已写签发时刻则跳过（防调价并发误伤已签发耗材申请）
        if (!RequestStatus.DRAFT.getCode().equals(request.getStatusEnum())
            || request.getReqAuthoredTime() != null) {
            return false;
        }
        // 持久化：状态迁 CANCELLED（不软删）
        request.ensureDraftCancellable();
        request.toCancelled();
        deviceRequestRepository.save(request);
        // 发布事件：通知费用/列表侧感知废弃
        domainEventPublisher.publish(new DeviceOrderCancelledEvent(
            request.getId(), command.getCancelReason(), command.getOperatorId()));
        return true;
    }

    /**
     * 门诊退费申请撤销：软删未完结负向退耗申请（须含 {@code refund_device_id}）。
     *
     * <p>药房已完成物理退耗的门禁由应用层先行拦截；本方法在通过门禁后软删开放的负向单。</p>
     *
     * @param command 负向申请主键集合与操作人
     */
    public void voidOpenRefundDeviceOrder(VoidOpenRefundDeviceOrderCommand command) {
        // 前置校验：命令与主键集合不可空。
        if (command == null || command.getRefundRequestIds() == null || command.getRefundRequestIds().isEmpty()) {
            return;
        }
        List<DeviceRequest> toSave = new ArrayList<>();
        // 加载/组装：按主键装载负向退耗申请并断言退耗链字段。
        for (Long refundRequestId : command.getRefundRequestIds()) {
            DeviceRequest request = this.requireDeviceRequest(refundRequestId);
            // Gate：必须是负向退耗单。
            request.ensureOpenRefundDeviceOrderForVoid();
            // 业务：已软删行跳过。
            if (Boolean.TRUE.equals(request.getIsDeleted())) {
                continue;
            }
            // Cmd：软删负向退耗申请。
            request.markDeleted(command.getOperatorId());
            toSave.add(request);
        }
        // 持久化：先落库。
        if (!toSave.isEmpty()) {
            deviceRequestRepository.saveAll(toSave);
        }
    }

    /**
     * 签发耗材请求。
     */
    public void signDeviceOrder(SignDeviceOrderCommand command) {
        List<SignDeviceOrderCommand.SignDeviceOrderItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "签发耗材请求一次仅允许提交一条记录");
        }

        SignDeviceOrderCommand.SignDeviceOrderItemCommand item = items.get(0);
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "签发耗材请求时 id 不能为空");
        }

        DeviceRequest request = this.requireDeviceRequest(item.getId());
        request.assertSignable();

        // DOC-CONFLICT: 库存与费用门控由应用服务编排完成，领域层不跨域读取。

        // 与诊疗/药品签发对齐：签发时间写 req_authored_time，不覆盖 req_opened_time。
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
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "签退耗材请求一次仅允许提交一条记录");
        }

        SignOffDeviceOrderCommand.SignOffDeviceOrderItemCommand item = items.get(0);
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
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
     * 门诊收费完成：已发送耗材请求推进为已完成，并发布门诊发放确认事件（执行机构可空）。
     *
     * @param deviceRequestId 耗材请求主键
     * @param operatorId      收费操作人，写入事件 performerId
     */
    public void completeDeviceRequestAfterOutpatientCharge(Long deviceRequestId, Long operatorId) {
        DeviceRequest request = this.requireDeviceRequest(deviceRequestId);
        // 已完成则幂等跳过，避免重复收费回调
        if (RequestStatus.COMPLETED.getCode().equals(request.getStatusEnum())) {
            return;
        }
        // Cmd：校验收费后可完成态并推进
        request.assertCompletableAfterOutpatientCharge();
        request.toCompleted();
        // 持久化先于事件发布
        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new OutpatientDeviceDispenseConfirmedEvent(deviceRequestId, operatorId, null));
    }

    /**
     * 护士/执行站执行确认：已发送耗材请求推进为已完成并发布门诊发放确认事件。
     *
     * @param command 执行确认命令；一次仅允许一条明细
     */
    public void executeDeviceOrder(ExecuteDeviceOrderCommand command) {
        List<ExecuteDeviceOrderCommand.ExecuteDeviceOrderItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "执行确认一次仅允许提交一条记录");
        }

        ExecuteDeviceOrderCommand.ExecuteDeviceOrderItemCommand item = items.get(0);
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "执行确认时 id 不能为空");
        }

        // Pre：加载请求并校验可执行态
        DeviceRequest request = this.requireDeviceRequest(item.getId());
        request.assertExecutable();

        // Cmd：写入执行人与执行科室并推进为已完成
        request.setPerformerId(item.getPerformerId());
        request.setPerformOrgId(item.getPerformOrgId());
        request.toCompleted();
        // 持久化先于事件发布
        deviceRequestRepository.save(request);
        domainEventPublisher.publish(new OutpatientDeviceDispenseConfirmedEvent(
            request.getId(), request.getPerformerId(), request.getPerformOrgId()));
    }

    /**
     * 撤销执行。
     */
    public void revokeDeviceOrderExecution(RevokeDeviceOrderExecutionCommand command) {
        List<RevokeDeviceOrderExecutionCommand.RevokeDeviceOrderExecutionItemCommand> items = command.resolveItems();
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "撤销执行一次仅允许提交一条记录");
        }

        RevokeDeviceOrderExecutionCommand.RevokeDeviceOrderExecutionItemCommand item = items.get(0);
        // 命令显式传入该字段时才覆盖写，null 表示本条不变更
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
        // 前置校验：写命令一次仅处理一条，防止批量误写
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "申请退耗材一次仅允许提交一条记录");
        }

        CreateDeviceOrderRefundCommand.CreateDeviceOrderRefundItemCommand item = items.get(0);
        // 必填入参缺失时拒绝继续，避免写脏数据
        if (item.getSourceRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "申请退耗材时 sourceRequestId 不能为空");
        }
        // 必填入参缺失时拒绝继续，避免写脏数据
        if (item.getRefundQuantity() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "申请退耗材时 refundQuantity 不能为空");
        }
        // 业务条件分支：见相邻判断的业务后果
        if (item.getRefundQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "申请退耗材时 refundQuantity 必须大于 0");
        }

        DeviceRequest source = this.requireDeviceRequest(item.getSourceRequestId());

        // Pre：原单本身已是负向退耗单时拒绝（不可对退耗单再退，EX-DRA-302）。
        if (source.getRefundDeviceId() != null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_RULE_CONFLICT, "已申请退耗材，请勿重复申请");
        }
        // Pre：已有未完结负向单则幂等返回（BR-DR-008）；本批多收费行共锚同一耗嘱时不误拦。
        Long existingOpenId = this.findOpenRefundDeviceOrderId(source.getId());
        if (existingOpenId != null) {
            return existingOpenId;
        }

        // 冲销校验（BR-DR-011）：严格使用 refundDeviceId + quantity<0 汇总口径。
        BigDecimal remaining = this.offsetVerification(source.getId(), item.getRefundQuantity());
        // 业务条件分支：见相邻判断的业务后果
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
     * 退耗发放确认后收口负向耗材申请：CANCELLED → COMPLETED（对称药嘱 completeMedicationRequestAfterRefund）。
     *
     * @param requestId 负向耗材申请主键（须含 refund_device_id）
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
            boolean hasBasedOnTable = StringUtils.isNotEmpty(item.getBasedOnTable());
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
            // 组套标识：执行绑定耗材可与主药嘱 package_id 对齐（可空）
            if (item.getPackageId() != null) {
                request.setPackageId(item.getPackageId());
            }
            request.setIsDeleted(Boolean.FALSE);
            request.setBusNo(businessSequenceService.getSeqByDay(
                AggregateTypeConstants.DEVICE_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
            // 执行侧直建完成单无草稿阶段：开立与签发同落创建时刻。
            request.setReqOpenedTime(authoredTime);
            request.setReqAuthoredTime(authoredTime);
            request.assertSourceAllowedForCompletedCreate();
            request.assertRequiredForCreate();
            // BR-DR-012：护士/医技划价路径库房必填。
            if (item.getGenerateSourceEnum() != null
                && (GenerateSource.NURSE_PRICING.getCode().equals(item.getGenerateSourceEnum())
                    || GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(item.getGenerateSourceEnum()))) {
                if (request.getPerformLocation() == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT,
                        "划价路径发放库房 performLocation 不能为空");
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
     * 撤销已完成请求（对执行侧直写完成态进行撤销/软删收口；支持批量）。
     *
     * <p><strong>注意</strong>：自 2026-07-17 起，IF-IP-PRC-06 取消划价改为费用冲销留痕，
     * {@code NurseStationInpatientBillingAppService#cancelNurseStationPricing} <strong>不再调用</strong>本方法；
     * DeviceRequest 保持完成态。本方法仅保留作历史整行还原/其他撤销场景。</p>
     *
     * @param command 批量撤销项；护士划价路径须 {@code generate_source=nurse-pricing}
     * @throws BusinessException 状态不允许、不存在或入参非法时
     */
    public void revokeExecutedDeviceOrder(RevokeExecutedDeviceOrderCommand command) {
        List<RevokeExecutedDeviceOrderCommand.RevokeExecutedDeviceOrderItemCommand> items = command.resolveItems();
        // 前置校验：批量撤销须至少一行。
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "撤销已完成请求入参不能为空");
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (RevokeExecutedDeviceOrderCommand.RevokeExecutedDeviceOrderItemCommand item : items) {
            if (item.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "撤销已完成请求时 id 不能为空");
            }
            distinctIds.add(item.getId());
        }
        List<Long> idList = new ArrayList<>(distinctIds);
        List<DeviceRequest> requests = deviceRequestRepository.findAllById(idList);
        Map<Long, DeviceRequest> requestMap = new HashMap<>();
        for (DeviceRequest request : requests) {
            requestMap.put(request.getId(), request);
        }
        List<DeviceRequest> toSave = new ArrayList<>();
        for (Long id : idList) {
            DeviceRequest request = requestMap.get(id);
            if (request == null) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_NOT_FOUND, "耗材请求不存在");
            }
            // 护士划价路径走专用断言；其它来源仍仅校验 COMPLETED。
            if (request.getGenerateSourceEnum() != null
                && GenerateSource.NURSE_PRICING.getCode().equals(request.getGenerateSourceEnum())) {
                request.assertNursePricingRevocable();
            } else {
                request.assertCompleted();
            }
            request.setIsDeleted(Boolean.TRUE);
            toSave.add(request);
        }
        deviceRequestRepository.saveAll(toSave);
        // 发布事件：护士划价行 save 后通知读侧剔除。
        for (DeviceRequest row : toSave) {
            if (row.getGenerateSourceEnum() != null
                && GenerateSource.NURSE_PRICING.getCode().equals(row.getGenerateSourceEnum())) {
                domainEventPublisher.publish(new NursePricingDeviceRequestRemovedEvent(
                    row.getId(),
                    row.getEncounterId(),
                    row.getBasedOnTable(),
                    row.getBasedOnId()));
            }
        }
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
        out.setEncounterDiagnosisIds(source.getEncounterDiagnosisIds());
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
        // 复制新草稿：开立取当前；签发列待正式签发再写；停嘱列清空。
        out.setReqOpenedTime(LocalDateTime.now());
        out.setReqAuthoredTime(null);
        out.setStopTime(null);
        // 复制/重开新行不得继承源行停嘱人。
        out.setStopperId(null);
        out.setIsDeleted(Boolean.FALSE);
        out.initDraft();
        return out;
    }

    /**
     * 门诊医生站耗材 FIFO 签发后回写请求 {@code lot_number}（首段签发批号摘要）。
     *
     * <p>权威批号仍在各 {@code DeviceDispense.lot_number}；请求侧冗余首段批号供列表/排查，
     * 禁止写空。仅注入本聚合 {@link DeviceRequestRepository}（B.6.4）。</p>
     *
     * @param deviceRequestId 耗材请求主键
     * @param lotNumber       FIFO 首段真实批号
     * @throws BusinessException 主键为空、批号为空或请求不存在时
     */
    public void bindLotNumberAfterDoctorStationFifoSign(Long deviceRequestId, String lotNumber) {
        // Gate：请求主键必填
        if (deviceRequestId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "deviceRequestId 不能为空");
        }
        // Gate：签发批号禁止空，与发放 FIFO 门禁同口径
        if (lotNumber == null || lotNumber.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "签发批号不能为空");
        }
        // Pre：加载本聚合请求行
        DeviceRequest request = this.requireDeviceRequest(deviceRequestId);
        // Cmd：回写请求批号摘要（多段 FIFO 时取首段）
        request.setLotNumber(lotNumber.trim());
        deviceRequestRepository.save(request);
    }

    /**
     * 门诊护士指定耗材房执行时回写请求 {@code perform_location}（BR-NUR-006）。
     *
     * <p>只改库房，不改 {@code lot_number}、不改状态与数量。权威批号在各
     * {@code DeviceDispense.lot_number}。仅注入本聚合 {@link DeviceRequestRepository}（B.6.4）。</p>
     *
     * @param deviceRequestId   耗材请求主键
     * @param performLocationId 护士选定的发放库房位置主键
     * @throws BusinessException 主键为空或请求不存在时
     */
    public void rebindPerformLocationForOutpatientNurseExecute(
        Long deviceRequestId,
        Long performLocationId) {
        // Gate：请求主键必填，防止空锚点写库
        if (deviceRequestId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "deviceRequestId 不能为空");
        }
        // Gate：发放库房必填，避免把 perform_location 写空
        if (performLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "performLocationId 不能为空");
        }
        // Pre：加载本聚合请求行
        DeviceRequest request = this.requireDeviceRequest(deviceRequestId);
        // Cmd：只回写发放耗材房；批号保持签发值
        request.setPerformLocation(performLocationId);
        // 持久化：本路径无领域事件，仅落库 perform_location
        deviceRequestRepository.save(request);
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

    /**
     * 查找原耗材申请上未完结的负向退耗单主键（BR-DR-008 幂等读）。
     * <p>
     * 仅按 {@code refund_device_id = originRequestId} 定位，不按患者/就诊扫全表；
     * 他支付单、他耗嘱的既有退耗申请不会误拦本原单。
     * </p>
     *
     * @param originRequestId 原正向耗材申请主键
     * @return 已有未完结负向单主键；无则 {@code null}
     */
    private Long findOpenRefundDeviceOrderId(Long originRequestId) {
        List<DeviceRequest> existing = this.deviceRequestRepository.findByRefundDeviceIdIn(
            Collections.singletonList(originRequestId));
        for (DeviceRequest row : existing) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!RequestStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                return row.getId();
            }
        }
        return null;
    }
}
