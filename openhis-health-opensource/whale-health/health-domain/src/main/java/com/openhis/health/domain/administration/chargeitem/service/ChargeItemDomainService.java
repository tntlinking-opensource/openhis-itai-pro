/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.administration.chargeitem.command.ActivateChargeByExeCommand;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemsLinkingRegeneratedAnchorsCommand;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.SwitchChargeItemAccountCommand;
import com.openhis.health.domain.administration.chargeitem.command.DeactivateChargeByExeCommand;
import com.openhis.health.domain.administration.chargeitem.command.OffsetNursePricingChargeItemsByServiceAnchorsCommand;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.administration.chargeitem.command.VoidOrderExecuteChargeItemsByProcedureIdsCommand;
import com.openhis.health.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.repository.ChargeItemRepository;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ChargeItemEventType;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.events.administration.chargeitem.ChargeItemDomainEvents;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 收费明细领域服务，承载收费明细（ChargeItem）的写侧编排能力。
 *
 * <p>管理收费明细的创建、状态批量迁移（激活/结算/退款等）和账户切换全流程。
 * 所有写操作均遵循「先落库后发事件」时序（通用规范 B.14 硬门禁），确保事件与数据一致性。</p>
 *
 * <p>对应 DM-ADM-011 §6.1 写侧方法集；写库仅经本聚合 {@link ChargeItemRepository}。
 * 挂费拷贝诊断有序 ids 串 时经药品/诊疗/耗材 {@code *QueryService} 读上游请求（B.6.4 读路径）。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeItemDomainService {
    private final ChargeItemRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 收费明细 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#CHARGE_ITEM}。 */
    private final BusinessSequenceService businessSequenceService;
    private final ChargeItemRegenerationChainSupport regenerationChainSupport;
    /** 加载候选：药品请求诊断有序 ids 串（B.6.4）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 加载候选：诊疗请求诊断有序 ids 串（B.6.4）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 加载候选：耗材请求诊断有序 ids 串（B.6.4）。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /**
     * 创建挂号收费明细，初始状态为已计划（statusEnum=PLANNED），并发布 REGISTRATION_CREATED 事件。
     *
     * @param cmd 创建收费明细命令
     * @return 首条新建明细主键
     */
    public Long createRegistrationChargeItem(CreateChargeItemCommand cmd) {
        return this.createByCommand(cmd, ChargeItemStatus.PLANNED, ChargeItemEventType.REGISTRATION_CREATED);
    }

    /**
     * 从医嘱创建草稿收费明细，初始状态为草稿（statusEnum=DRAFT），并发布 ORDER_DRAFT_CREATED 事件。
     *
     * @param cmd 创建收费明细命令
     * @return 首条新建明细主键
     */
    public Long createDraftChargeItemFromOrder(CreateChargeItemCommand cmd) {
        return this.createByCommand(cmd, ChargeItemStatus.DRAFT, ChargeItemEventType.ORDER_DRAFT_CREATED);
    }

    /**
     * 按就诊与上游业务锚点作废仍处于草稿且未结算的收费明细（医嘱删行前清理费用草稿，AS-OP-DOR-003 IF-DOR-14）。
     *
     * @param cmd 锚点与就诊上下文
     */
    public void voidUnbilledDraftChargeItemsByServiceAnchor(VoidUnbilledDraftChargeItemsByServiceAnchorCommand cmd) {
        if (cmd.getEncounterId() == null || cmd.getServiceTable() == null || cmd.getServiceId() == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "作废草稿费用锚点参数不完整");
        }
        // Pre：查询本就诊、本锚点下仍处于草稿状态的收费明细。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndServiceTableAndServiceIdAndStatusEnum(
            cmd.getEncounterId(), cmd.getServiceTable(), cmd.getServiceId(), ChargeItemStatus.DRAFT.getCode());
        // 本锚点下无草稿收费行时静默跳过，重复调用幂等无操作。
        if (rows.isEmpty()) {
            return;
        }
        repository.deleteAll(rows);
    }

    /**
     * 护士站计价创建收费明细，状态为待结算（statusEnum=PENDING_SETTLEMENT），并发布 NURSE_PRICING_CREATED 事件。
     *
     * @param cmd 创建收费明细命令
     * @return 首条新建明细主键
     */
    public Long priceByNurseStation(CreateChargeItemCommand cmd) {
        return this.createByCommand(cmd, ChargeItemStatus.PENDING_SETTLEMENT, ChargeItemEventType.NURSE_PRICING_CREATED);
    }

    /**
     * 住院滚费创建收费明细，状态为待结算（statusEnum=PENDING_SETTLEMENT），并发布 AUTO_ROLL_CREATED 事件。
     *
     * <p>生成来源固定为 {@code auto-roll-fees}（DM-ADM-011 §6.1.3b；AS-IP-NUR-013 BR-IP-ARG-015）。</p>
     *
     * @param cmd 创建收费明细命令
     * @return 首条新建明细主键
     */
    public Long priceByAutoRoll(CreateChargeItemCommand cmd) {
        return this.createByCommand(cmd, ChargeItemStatus.PENDING_SETTLEMENT, ChargeItemEventType.AUTO_ROLL_CREATED);
    }

    /**
     * 执行激活收费明细，状态迁移至已计划（statusEnum=PLANNED），并发布 ACTIVATED_BY_EXECUTION 事件。
     *
     * @param cmd 批量收费明细主键命令
     */
    public void activateChargeItemAfterExecution(BatchChargeItemIdsCommand cmd) {
        this.batchUpdateStatus(cmd, ChargeItemStatus.PLANNED, ChargeItemEventType.ACTIVATED_BY_EXECUTION);
    }

    /**
     * 执行后激活收费明细并挂接执行记录（临时医嘱：PLANNED/DRAFT → PENDING_SETTLEMENT + procedureId）。
     *
     * <p>住院临时医嘱签发后费用行已为 {@code PLANNED}（待收费）；护士站执行时须从该态激活为 {@code PENDING_SETTLEMENT}。
     * {@code DRAFT} 仍兼容未签发升位的历史/门诊对齐路径。</p>
     *
     * @param cmd 执行激活命令（含 chargeItemId、procedureId、performedAt、performerId）
     */
    public void activateChargeByExe(ActivateChargeByExeCommand cmd) {
        List<ActivateChargeByExeCommand.Item> items = cmd.resolveItems();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "执行激活收费明细入参不能为空");
        }
        List<Long> ids = new ArrayList<>();
        for (ActivateChargeByExeCommand.Item item : items) {
            ids.add(item.getChargeItemId());
        }
        Map<Long, ChargeItem> map = this.loadMap(ids);
        List<ChargeItem> toSave = new ArrayList<>();
        for (ActivateChargeByExeCommand.Item item : items) {
            ChargeItem row = require(map, item.getChargeItemId());
            if (!this.isActivatableByExecutionStatus(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    "收费明细 " + item.getChargeItemId() + " 状态非 PLANNED/DRAFT，不允许执行激活");
            }
            row.setProcedureId(item.getProcedureId());
            if (item.getPerformedAt() != null) {
                row.setOccurrenceTime(item.getPerformedAt());
            }
            // 执行激活不写 enterer_*（收费人）；命令携带的执行护士可落 performer_id 供费用明细「操作人」展示
            if (item.getPerformerId() != null) {
                row.setPerformerId(item.getPerformerId());
            }
            row.setStatusEnum(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
            toSave.add(row);
        }
        repository.saveAll(toSave);
        for (ChargeItem row : toSave) {
            ChargeItemDomainEvents.ChargeItemActivatedByExecutionEvent event =
                new ChargeItemDomainEvents.ChargeItemActivatedByExecutionEvent(row.getId());
            event.setProcedureId(row.getProcedureId());
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 长期医嘱执行后新建待结算收费明细（generate_source=ORDER_EXECUTE，挂 procedureId）。
     *
     * @param cmd 创建收费明细命令
     * @return 首条新建明细主键
     */
    public Long createPlannedChargeItemFromOrderExecution(CreateChargeItemCommand cmd) {
        return this.createByCommand(cmd, ChargeItemStatus.PENDING_SETTLEMENT, ChargeItemEventType.ACTIVATED_BY_EXECUTION);
    }

    /**
     * 医嘱签发后：按就诊与上游医嘱锚点，将仍处于草稿（DRAFT）的收费明细批量升级为已计划（PLANNED），
     * 与门诊预结算门禁一致（{@link #preSettleChargeItems} 仅接受 PLANNED）。
     *
     * <p>无草稿行时静默跳过；锚点参数不完整时直接跳过，不抛异常，避免编排层重复门控。</p>
     *
     * @param encounterId  就诊主键
     * @param serviceTable 上游业务表名（与 {@code ChargeItem.serviceTable} 一致）
     * @param serviceId    上游医嘱申请主键（与 {@code ChargeItem.serviceId} 一致）
     * @throws BusinessException 经 {@link #activateChargeItemAfterExecution} 间接抛出时，含义同批量状态迁移（如明细不存在）
     */
    public void planDraftChargeItemsAfterAdviceSigned(Long encounterId, String serviceTable, Long serviceId) {
        if (encounterId == null || StringUtils.isEmpty(serviceTable) || serviceId == null) {
            return;
        }
        // Pre：仅处理本锚点、本就诊下仍为草稿的医嘱费用行，避免误升挂号费等其他 PLANNED 路径。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndServiceTableAndServiceIdAndStatusEnum(
            encounterId, serviceTable, serviceId, ChargeItemStatus.DRAFT.getCode());
        // 本锚点下无草稿收费行时静默跳过，重复调用幂等无操作。
        if (rows.isEmpty()) {
            return;
        }
        // Event：activateChargeItemAfterExecution 内部满足 B.14 先 save 后 publish 时序。
        this.activateChargeItemAfterExecution(this.toBatchChargeItemIdsCommand(rows));
    }

    /**
     * 医嘱签退后：将锚点下仍处于已计划（PLANNED）的收费明细回退为草稿（DRAFT），与签发路径对称。
     *
     * <p>已结算（SETTLED）等终态行不在本查询范围内，故不会被误回退；无已计划行时静默跳过。</p>
     *
     * @param encounterId  就诊主键
     * @param serviceTable 上游业务表名（与 {@code ChargeItem.serviceTable} 一致）
     * @param serviceId    上游医嘱申请主键（与 {@code ChargeItem.serviceId} 一致）
     * @throws BusinessException 经 {@link #cancelExecutionRollbackChargeItem} 间接抛出时，含义同批量状态迁移（如明细不存在）
     */
    public void rollbackPlannedChargeItemsAfterAdviceSignedOff(Long encounterId, String serviceTable, Long serviceId) {
        if (encounterId == null || StringUtils.isEmpty(serviceTable) || serviceId == null) {
            return;
        }
        // Pre：查询本锚点下仍处于已计划状态的收费明细。
        // 业务语义：签退后患者不应再就该医嘱行发起门诊预结算，回退为草稿与「未签发」费用语义一致。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndServiceTableAndServiceIdAndStatusEnum(
            encounterId, serviceTable, serviceId, ChargeItemStatus.PLANNED.getCode());
        // 本锚点下无已计划收费行时静默跳过，签退可能已无待回退费用。
        if (rows.isEmpty()) {
            return;
        }
        this.cancelExecutionRollbackChargeItem(this.toBatchChargeItemIdsCommand(rows));
    }

    /**
     * 按多条上游锚点作废仍处于草稿且未结算的收费明细（签发重置分段锚点前清理旧费用草稿）。
     *
     * @param encounterId  就诊主键
     * @param serviceTable 上游业务表名
     * @param serviceIds   上游业务主键集合
     */
    public void voidUnbilledDraftChargeItemsByServiceAnchors(
        Long encounterId, String serviceTable, Collection<Long> serviceIds) {
        if (encounterId == null || StringUtils.isEmpty(serviceTable) || serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        // Pre：循环去重并过滤 null 值，保证批量查询效率。
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long serviceId : serviceIds) {
            if (serviceId != null) {
                distinctIds.add(serviceId);
            }
        }
        // 去重后无有效上游主键时静默跳过，入参可能全为 null。
        if (distinctIds.isEmpty()) {
            return;
        }
        // Pre：批量查询本就诊、本表、多锚点下仍处于草稿状态的收费明细。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndServiceTableAndServiceIdInAndStatusEnum(
            encounterId, serviceTable, distinctIds, ChargeItemStatus.DRAFT.getCode());
        // 本锚点下无草稿收费行时静默跳过，重复调用幂等无操作。
        if (rows.isEmpty()) {
            return;
        }
        repository.deleteAll(rows);
    }

    /**
     * 医嘱签发后：将锚定在多条上游业务主键下、仍处于草稿的收费明细批量升为已计划。
     *
     * @param encounterId  就诊主键
     * @param serviceTable 上游业务表名（如发放物理表）
     * @param serviceIds   上游主键集合
     */
    public void planDraftChargeItemsAfterAdviceSignedForAnchors(
        Long encounterId, String serviceTable, Collection<Long> serviceIds) {
        if (encounterId == null || StringUtils.isEmpty(serviceTable) || serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        // Pre：循环去重并过滤 null 值。
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long serviceId : serviceIds) {
            if (serviceId != null) {
                distinctIds.add(serviceId);
            }
        }
        // 去重后无有效上游主键时静默跳过，入参可能全为 null。
        if (distinctIds.isEmpty()) {
            return;
        }
        // Pre：批量查询草稿状态的收费明细。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndServiceTableAndServiceIdInAndStatusEnum(
            encounterId, serviceTable, distinctIds, ChargeItemStatus.DRAFT.getCode());
        // 本锚点下无草稿收费行时静默跳过，重复调用幂等无操作。
        if (rows.isEmpty()) {
            return;
        }
        this.activateChargeItemAfterExecution(this.toBatchChargeItemIdsCommand(rows));
    }

    /**
     * 医嘱签退后：将多条上游锚点下仍处于已计划的收费明细批量回退为草稿。
     *
     * @param encounterId  就诊主键
     * @param serviceTable 上游业务表名
     * @param serviceIds   上游主键集合
     */
    public void rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors(
        Long encounterId, String serviceTable, Collection<Long> serviceIds) {
        if (encounterId == null || StringUtils.isEmpty(serviceTable) || serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        // Pre：循环去重并过滤 null 值。
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long serviceId : serviceIds) {
            if (serviceId != null) {
                distinctIds.add(serviceId);
            }
        }
        // 去重后无有效上游主键时静默跳过，入参可能全为 null。
        if (distinctIds.isEmpty()) {
            return;
        }
        // Pre：批量查询已计划状态的收费明细。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndServiceTableAndServiceIdInAndStatusEnum(
            encounterId, serviceTable, distinctIds, ChargeItemStatus.PLANNED.getCode());
        // 本锚点下无已计划收费行时静默跳过，签退可能已无待回退费用。
        if (rows.isEmpty()) {
            return;
        }
        this.cancelExecutionRollbackChargeItem(this.toBatchChargeItemIdsCommand(rows));
    }

    /**
     * 按发放物理行锚点作废仍处于草稿且未结算的收费明细。
     *
     * <p>用于门诊医生站签退：药品/耗材已在签发相位按 FIFO 生成多条发放分段费用，清理时需按 {@code dispense_table+dispense_id}
     * 命中草稿行并软删（底座语义），与 {@link #voidUnbilledDraftChargeItemsByServiceAnchors}（按医嘱请求锚点清理）互补。</p>
     *
     * @param encounterId   就诊主键，限定删除范围避免误伤同表其他就诊
     * @param dispenseTable 发放物理表名，与 {@code adm_charge_item.dispense_table} 一致
     * @param dispenseIds   本次签退涉及的发放主键集合；调用方应已去重业务含义，此处仍做 null 过滤与集合去重以满足 A.9.1 批量习惯
     */
    public void voidUnbilledDraftChargeItemsByDispenseAnchors(
        Long encounterId, String dispenseTable, Collection<Long> dispenseIds) {
        if (encounterId == null || StringUtils.isEmpty(dispenseTable) || dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        // Pre：循环去重并过滤 null 值。
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long dispenseId : dispenseIds) {
            if (dispenseId != null) {
                distinctIds.add(dispenseId);
            }
        }
        // 去重后无有效发放主键时静默跳过，入参可能全为 null。
        if (distinctIds.isEmpty()) {
            return;
        }
        // Pre：单次 IN 查询加载目标草稿行（A.9.1），避免循环逐条删除。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndDispenseTableAndDispenseIdInAndStatusEnum(
            encounterId, dispenseTable, distinctIds, ChargeItemStatus.DRAFT.getCode());
        // 本锚点下无草稿收费行时静默跳过，重复调用幂等无操作。
        if (rows.isEmpty()) {
            return;
        }
        repository.deleteAll(rows);
    }

    /**
     * 医嘱签发后：将仍锚定在多条发放主键下、处于草稿的收费明细批量升为已计划。
     *
     * <p>对应门诊医生站 IF-DOR-15 中药品/耗材 FIFO 分段路径：费用行的 {@code service_*} 指向医嘱请求，计费粒度由 {@code dispense_*}
     * 区分；本方法按发放主键集合批量升位，供收费预结算门禁消费。</p>
     *
     * @param encounterId   就诊主键
     * @param dispenseTable 发放物理表名（如药品发放表、耗材发放表）
     * @param dispenseIds   本批次签发新生成的发放分段主键集合
     */
    public void planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
        Long encounterId, String dispenseTable, Collection<Long> dispenseIds) {
        if (encounterId == null || StringUtils.isEmpty(dispenseTable) || dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        // Pre：循环去重并过滤 null 值。
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long dispenseId : dispenseIds) {
            if (dispenseId != null) {
                distinctIds.add(dispenseId);
            }
        }
        // 去重后无有效上游主键时静默跳过，入参可能全为 null。
        if (distinctIds.isEmpty()) {
            return;
        }
        // Pre：批量查询草稿状态的收费明细。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndDispenseTableAndDispenseIdInAndStatusEnum(
            encounterId, dispenseTable, distinctIds, ChargeItemStatus.DRAFT.getCode());
        // 本锚点下无草稿收费行时静默跳过，重复调用幂等无操作。
        if (rows.isEmpty()) {
            return;
        }
        this.activateChargeItemAfterExecution(this.toBatchChargeItemIdsCommand(rows));
    }

    /**
     * 医嘱签退后：将仍锚定在多条发放主键下、处于已计划的收费明细批量回退为草稿。
     *
     * <p>与 {@link #planDraftChargeItemsAfterAdviceSignedByDispenseAnchors} 对称，用于 IF-DOR-16 签退后收回「可预结算」语义，
     * 避免患者对已撤回医嘱继续发起门诊预结算。</p>
     *
     * @param encounterId   就诊主键
     * @param dispenseTable 发放物理表名
     * @param dispenseIds   需回退的计费分段对应的发放主键集合
     */
    public void rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
        Long encounterId, String dispenseTable, Collection<Long> dispenseIds) {
        if (encounterId == null || StringUtils.isEmpty(dispenseTable) || dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        // Pre：循环去重并过滤 null 值。
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long dispenseId : dispenseIds) {
            if (dispenseId != null) {
                distinctIds.add(dispenseId);
            }
        }
        // 去重后无有效上游主键时静默跳过，入参可能全为 null。
        if (distinctIds.isEmpty()) {
            return;
        }
        // Pre：批量查询已计划状态的收费明细。
        List<ChargeItem> rows = repository.findAllByEncounterIdAndDispenseTableAndDispenseIdInAndStatusEnum(
            encounterId, dispenseTable, distinctIds, ChargeItemStatus.PLANNED.getCode());
        // 本锚点下无已计划收费行时静默跳过，签退可能已无待回退费用。
        if (rows.isEmpty()) {
            return;
        }
        this.cancelExecutionRollbackChargeItem(this.toBatchChargeItemIdsCommand(rows));
    }

    /**
     * 取消执行回滚收费明细，状态回退至草稿（statusEnum=DRAFT），并发布 EXECUTION_CANCELLED 事件。
     *
     * @param cmd 批量收费明细主键命令
     */
    public void cancelExecutionRollbackChargeItem(BatchChargeItemIdsCommand cmd) {
        this.batchUpdateStatus(cmd, ChargeItemStatus.DRAFT, ChargeItemEventType.EXECUTION_CANCELLED);
    }

    /**
     * 按执行记录主键逻辑删除长期医嘱执行新建的待结算收费明细（generate_source=ORDER_EXECUTE，AS-IP-NUR-012 BR-IP-CXL-008）。
     *
     * <p>仅命中 {@code PENDING_SETTLEMENT + order-execute} 行；同 procedureId 下若存在已结算/退费链路行则拒绝（BR-CHG-002）。</p>
     *
     * @param cmd 执行记录主键集合
     */
    public void voidOrderExecuteChargeItemsByProcedureIds(VoidOrderExecuteChargeItemsByProcedureIdsCommand cmd) {
        Collection<Long> procedureIds = this.distinctNonNullProcedureIds(cmd.getProcedureIds());
        // 无有效 procedureId 时静默跳过，取消执行入参可能为空集合。
        if (procedureIds.isEmpty()) {
            return;
        }
        // Pre：加载各执行记录关联的全部收费明细，用于结算 Gate 与目标行筛选。
        List<ChargeItem> linkedRows = repository.findAllByProcedureIdIn(procedureIds);
        Long encounterId = null;
        for (ChargeItem row : linkedRows) {
            if (row != null && row.getEncounterId() != null) {
                encounterId = row.getEncounterId();
                break;
            }
        }
        Set<Long> supersededSettledSourceIds =
            this.resolveSupersededSettledSourceIds(encounterId, linkedRows);
        // 任一关联行已进入结算/退费链路则整批拒绝，避免部分回退造成账实不一致。
        for (ChargeItem row : linkedRows) {
            if (this.isNonRollbackableChargeStatus(row.getStatusEnum())) {
                if (row.getId() != null && supersededSettledSourceIds.contains(row.getId())) {
                    continue;
                }
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    "执行记录 " + row.getProcedureId() + " 关联费用已结算或退费中，不允许取消执行回退");
            }
        }
        List<ChargeItem> toDelete = repository.findAllByProcedureIdInAndStatusEnumAndGenerateSourceEnum(
            procedureIds,
            ChargeItemStatus.PENDING_SETTLEMENT.getCode(),
            GenerateSource.ORDER_EXECUTE.getCode());
        // 无长期执行新建行时静默跳过，取消执行可能已无待删费用。
        if (toDelete.isEmpty()) {
            return;
        }
        repository.deleteAll(toDelete);
        for (ChargeItem row : toDelete) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemExecutionCancelledEvent(row.getId()));
        }
    }

    /**
     * 按主键集合逻辑删除仍为待结算的 {@code ORDER_EXECUTE} 费用行（代煎费刷新等，仅删命中行）。
     *
     * @param chargeItemIds 收费明细主键集合
     */
    public void voidPendingOrderExecuteChargeItemsByChargeItemIds(Collection<Long> chargeItemIds) {
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long chargeItemId : chargeItemIds) {
            if (chargeItemId != null) {
                distinctIds.add(chargeItemId);
            }
        }
        if (distinctIds.isEmpty()) {
            return;
        }
        List<ChargeItem> rows = repository.findAllByIdIn(distinctIds);
        List<ChargeItem> toDelete = new ArrayList<>();
        for (ChargeItem row : rows) {
            if (row == null) {
                continue;
            }
            // 仅删仍为待结算的 ORDER_EXECUTE 行
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (row.getGenerateSourceEnum() == null
                || !GenerateSource.ORDER_EXECUTE.getCode().equals(row.getGenerateSourceEnum())) {
                continue;
            }
            toDelete.add(row);
        }
        if (toDelete.isEmpty()) {
            return;
        }
        repository.deleteAll(toDelete);
        for (ChargeItem row : toDelete) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemExecutionCancelledEvent(row.getId()));
        }
    }

    /**
     * 按上游请求锚点冲销护士划价待结算收费明细（generate_source=nurse-pricing，AS-IP-NUR-005 IF-IP-PRC-06）。
     *
     * <p>保留原正向 {@code PENDING_SETTLEMENT} 行；对尚未冲销的正向行插入数量/金额取反的冲销行，
     * {@code refund_id} 链回原行。同锚点下若存在已结算/退费链路行则整批拒绝（BR-CHG-002）；无待冲销行时静默跳过。</p>
     *
     * @param cmd 就诊、上游表名与主键集合
     */
    public void offsetNursePricingChargeItemsByServiceAnchors(OffsetNursePricingChargeItemsByServiceAnchorsCommand cmd) {
        if (cmd == null || cmd.getEncounterId() == null || StringUtils.isEmpty(cmd.getServiceTable())
            || cmd.getServiceIds() == null || cmd.getServiceIds().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "冲销护士划价费用锚点参数不完整");
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long serviceId : cmd.getServiceIds()) {
            if (serviceId != null) {
                distinctIds.add(serviceId);
            }
        }
        // 去重后无有效划价锚点时静默跳过。
        if (distinctIds.isEmpty()) {
            return;
        }
        // Pre：加载本就诊、本表、多锚点下全部费用行，用于结算 Gate 与冲销候选筛选。
        List<ChargeItem> linkedRows = repository.findAllByEncounterIdAndServiceTableAndServiceIdIn(
            cmd.getEncounterId(), cmd.getServiceTable(), distinctIds);
        Set<Long> supersededSettledSourceIds =
            this.resolveSupersededSettledSourceIds(cmd.getEncounterId(), linkedRows);
        for (ChargeItem row : linkedRows) {
            if (this.isNonRollbackableChargeStatus(row.getStatusEnum())) {
                // 反结/半退再生链上源 SETTLED 终态行不参与 Gate（BR-REF-020 / BR-IP-RVS-011）。
                if (row.getId() != null && supersededSettledSourceIds.contains(row.getId())) {
                    continue;
                }
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    "划价锚点关联费用已结算或退费中，不允许取消划价");
            }
        }
        // Pre：收集已被冲销行指向的源 id，避免对同一正向行重复冲销。
        Set<Long> alreadyOffsetSourceIds = new HashSet<>();
        for (ChargeItem row : linkedRows) {
            if (row == null || row.getRefundId() == null) {
                continue;
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (row.getGenerateSourceEnum() == null
                || !GenerateSource.NURSE_PRICING.getCode().equals(row.getGenerateSourceEnum())) {
                continue;
            }
            alreadyOffsetSourceIds.add(row.getRefundId());
        }
        List<ChargeItem> sourcesToOffset = new ArrayList<>();
        for (ChargeItem row : linkedRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (row.getGenerateSourceEnum() == null
                || !GenerateSource.NURSE_PRICING.getCode().equals(row.getGenerateSourceEnum())) {
                continue;
            }
            // 冲销行本身（refund_id 非空）不再作为冲销源。
            if (row.getRefundId() != null) {
                continue;
            }
            // 数量非正（已是负向事实）不冲销。
            if (row.getQuantityValue() == null || row.getQuantityValue().signum() <= 0) {
                continue;
            }
            // 已被冲销过的正向行跳过，保证整行冲销幂等。
            if (alreadyOffsetSourceIds.contains(row.getId())) {
                continue;
            }
            sourcesToOffset.add(row);
        }
        // 无待冲销正向护士划价行时静默跳过（可能已整行冲销）。
        if (sourcesToOffset.isEmpty()) {
            return;
        }
        List<String> busNos = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.CHARGE_ITEM, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, sourcesToOffset.size());
        LocalDateTime now = LocalDateTime.now();
        List<ChargeItem> offsetRows = new ArrayList<>();
        for (int index = 0; index < sourcesToOffset.size(); index++) {
            offsetRows.add(this.cloneChargeItemForNursePricingOffset(
                sourcesToOffset.get(index), busNos.get(index), cmd.getOperatorId(), now));
        }
        // 持久化：先落库冲销行，再发事件（B.14）。
        List<ChargeItem> saved = repository.saveAll(offsetRows);
        for (ChargeItem savedRow : saved) {
            domainEventPublisher.publish(
                new ChargeItemDomainEvents.NursePricingChargeItemOffsetEvent(
                    savedRow.getId(), savedRow.getRefundId(), savedRow.getEncounterId()));
        }
    }

    /**
     * 组装护士划价冲销行：复刻锚点与单价，数量/金额取反，{@code refund_id} 链回原正向行。
     *
     * @param src        原正向待结算护士划价行
     * @param busNo      冲销行业务流水号
     * @param operatorId 冲销操作人（写入 performer_id；非收费人）
     * @param now        冲销发生时刻（写入 occurrence）
     * @return 可落库的冲销行实体
     */
    private ChargeItem cloneChargeItemForNursePricingOffset(
        ChargeItem src,
        String busNo,
        Long operatorId,
        LocalDateTime now) {
        ChargeItem row = new ChargeItem();
        row.setPatientId(src.getPatientId());
        row.setEncounterId(src.getEncounterId());
        row.setAccountId(src.getAccountId());
        row.setDefinitionId(src.getDefinitionId());
        row.setDefDetailId(src.getDefDetailId());
        // 单价保持原值；数量与金额取反形成净额轧差。
        row.setUnitPrice(src.getUnitPrice());
        row.setQuantityValue(MonetaryAmountSupport.nullToZero(src.getQuantityValue()).negate());
        row.setQuantityUnit(src.getQuantityUnit());
        row.setTotalPrice(MonetaryAmountSupport.scaleCalc(MonetaryAmountSupport.nullToZero(src.getTotalPrice()).negate()));
        row.setBaseAmount(MonetaryAmountSupport.scaleCalc(MonetaryAmountSupport.nullToZero(src.getBaseAmount()).negate()));
        row.setDiscountAmount(MonetaryAmountSupport.scaleCalc(MonetaryAmountSupport.nullToZero(src.getDiscountAmount()).negate()));
        row.setSurchargeAmount(MonetaryAmountSupport.scaleCalc(MonetaryAmountSupport.nullToZero(src.getSurchargeAmount()).negate()));
        if (src.getSystemDiscountPrice() != null) {
            row.setSystemDiscountPrice(MonetaryAmountSupport.scaleCalc(src.getSystemDiscountPrice().negate()));
        }
        if (src.getManualAdjustedPrice() != null) {
            row.setManualAdjustedPrice(MonetaryAmountSupport.scaleCalc(src.getManualAdjustedPrice().negate()));
        }
        row.setOverrideReasonCode(src.getOverrideReasonCode());
        row.setOverrideReasonText(src.getOverrideReasonText());
        // 冲销业务时刻取当前，便于费用明细按取消日可见。
        row.setOccurrenceTime(now);
        row.setServiceTable(src.getServiceTable());
        row.setServiceId(src.getServiceId());
        row.setProductTable(src.getProductTable());
        row.setProductId(src.getProductId());
        row.setProcedureId(src.getProcedureId());
        row.setDispenseTable(src.getDispenseTable());
        row.setDispenseId(src.getDispenseId());
        row.setPrescriptionNo(src.getPrescriptionNo());
        row.setParentId(src.getParentId());
        // 操作人：冲销护士优先；否则沿用源行 performer（非 enterer 收费人）
        if (operatorId != null) {
            row.setPerformerId(operatorId);
        } else {
            row.setPerformerId(src.getPerformerId());
        }
        row.setPerformingOrgId(src.getPerformingOrgId());
        row.setCostOrgId(src.getCostOrgId());
        row.setOrgId(src.getOrgId());
        row.setRequestingOrgId(src.getRequestingOrgId());
        row.setGenerateSourceEnum(src.getGenerateSourceEnum());
        row.setContextEnum(src.getContextEnum());
        row.setEncounterDiagnosisIds(src.getEncounterDiagnosisIds());
        row.setTcmFlag(src.getTcmFlag());
        row.setClaimStateEnum(src.getClaimStateEnum());
        row.setPrintCount(src.getPrintCount());
        row.setRefundId(src.getId());
        row.setStatusEnum(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        row.setBusNo(busNo);
        // 冲销新行待再结算：enterer_* 留空，待 settle 写入收费人/收费时间
        row.assertPersistableNewRow();
        return row;
    }

    /**
     * 取消执行逆激活收费明细：PENDING_SETTLEMENT → PLANNED 并清 procedureId（临时医嘱，AS-IP-NUR-012 BR-IP-CXL-008）。
     *
     * @param cmd 批量收费明细主键命令
     */
    public void deactivateChargeByExe(DeactivateChargeByExeCommand cmd) {
        List<DeactivateChargeByExeCommand.Item> items = cmd.resolveItems();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "逆激活收费明细入参不能为空");
        }
        List<Long> ids = new ArrayList<>();
        for (DeactivateChargeByExeCommand.Item item : items) {
            ids.add(item.getChargeItemId());
        }
        Map<Long, ChargeItem> map = this.loadMap(ids);
        List<ChargeItem> toSave = new ArrayList<>();
        for (DeactivateChargeByExeCommand.Item item : items) {
            ChargeItem row = require(map, item.getChargeItemId());
            // 仅待结算态允许逆激活，与 activateChargeByExe 对称。
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    "收费明细 " + item.getChargeItemId() + " 状态非 PENDING_SETTLEMENT，不允许逆激活");
            }
            if (this.isNonRollbackableChargeStatus(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    "收费明细 " + item.getChargeItemId() + " 已进入结算/退费链路，不允许逆激活");
            }
            row.setStatusEnum(ChargeItemStatus.PLANNED.getCode());
            row.setProcedureId(null);
            toSave.add(row);
        }
        repository.saveAll(toSave);
        for (ChargeItem row : toSave) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemExecutionCancelledEvent(row.getId()));
        }
    }

    /**
     * 门诊结算收费明细，状态迁移至已结算（statusEnum=SETTLED），落库收费人/收费时间并发布 SETTLED 事件。
     *
     * <p>收费人/收费时间写入 {@code enterer_id}/{@code entered_date}（{@code adm_practitioner.id} +
     * 与 payment {@code bill_date} 同值的 {@code billDate}，**BR-OPC-016**）。
     * <strong>不再</strong>将收款人写入 {@code performer_id}。</p>
     *
     * @param cmd 批量收费明细主键命令，须含非空 {@code operatorId}（收费人医务人员主键）
     * @throws BusinessException 入参为空或缺少收费人
     */
    @Transactional
    public void settleChargeItems(BatchChargeItemIdsCommand cmd) {
        // Pre：从批量命令中提取收费明细 ID 列表。
        List<Long> ids = this.extractIds(cmd);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细批量入参不能为空");
        }
        Long cashierPractitionerId = cmd.getOperatorId();
        if (cashierPractitionerId == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "门诊收费结算须携带收费人（operatorId）");
        }
        // Pre：批量加载目标明细，确认记录存在且仍处于 PLANNED（与 preSettleChargeItems 对称，GAP-FIN-003）。
        Map<Long, ChargeItem> map = this.loadMap(ids);
        for (Long id : ids) {
            ChargeItem row = require(map, id);
            if (!ChargeItemStatus.PLANNED.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    "收费明细 " + id + " 状态非 PLANNED，不允许门诊结算");
            }
        }
        List<ChargeItem> toSettle = repository.findAllByIdInAndStatusEnum(
            ids, ChargeItemStatus.PLANNED.getCode());
        if (toSettle.size() != ids.size()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                "收费明细状态已变更，不允许重复结算或结算已撤回医嘱");
        }
        for (ChargeItem row : toSettle) {
            row.setStatusEnum(ChargeItemStatus.SETTLED.getCode());
            // Cmd：收费人/收费时间落 enterer_*（与 payment.enterer_id / bill_date 同源）
            this.applyCashierStamp(row, cashierPractitionerId, cmd.getBillDate());
        }
        repository.saveAll(toSettle);
        // Event：SETTLED 为批量粒度事件，以首条 ID 代表整批次发布一次。
        this.publishByType(ChargeItemEventType.SETTLED, ids.get(0), ids);
    }

    /**
     * 挂号退费，状态迁移至已退款（statusEnum=REFUNDED），覆盖收费人/收费时间并发布 REGISTRATION_REFUNDED 事件。
     *
     * @param cmd 批量收费明细主键命令（须含退费操作人；{@code billDate} 可选）
     */
    public void returnRegistrationRefundChargeItems(BatchChargeItemIdsCommand cmd) {
        this.batchUpdateStatusWithCashierStamp(
            cmd, ChargeItemStatus.REFUNDED, ChargeItemEventType.REGISTRATION_REFUNDED);
    }

    /**
     * 切换收费明细所属就诊账户，批量更新 accountId 并发布账户切换事件。
     *
     * <p>适用于收费账户合并/拆分等业务场景，操作前需确保目标账户已存在。</p>
     *
     * @param cmd 账户切换命令，包含收费明细 ID 与目标账户 ID 的映射关系
     */
    public void switchChargeItemAccount(SwitchChargeItemAccountCommand cmd) {
        // Pre：提取本次需要切换账户的收费明细 ID 集合，用于批量加载。
        List<SwitchChargeItemAccountCommand.Item> items = cmd.resolveItems();
        List<Long> ids = new ArrayList<>();
        // Loop：遍历命令项，收集所有收费明细 ID。
        for (SwitchChargeItemAccountCommand.Item item : items) {
            ids.add(item.getChargeItemId());
        }
        // Pre：批量加载目标收费明细，确认记录存在且有效。
        Map<Long, ChargeItem> map = this.loadMap(ids);
        List<ChargeItem> toSave = new ArrayList<>();
        for (SwitchChargeItemAccountCommand.Item item : items) {
            ChargeItem row = require(map, item.getChargeItemId());
            row.setAccountId(item.getAccountId());
            toSave.add(row);
        }
        repository.saveAll(toSave);
        // Event：逐条发布账户切换领域事件，通知下游读模型同步。
        for (SwitchChargeItemAccountCommand.Item item : items) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemAccountSwitchedEvent(item.getChargeItemId(), item.getAccountId()));
        }
    }

    /**
     * 半退保留链：在各上游聚合已完成正向克隆并取得新 service/dispense 锚点后，本聚合批量落库新 {@code adm_charge_item} 行（初始 PLANNED），并写 {@code refund_id} 链回源 SETTLED 行（DM-ADM-011 §6.1.9a，BR-CHG-015）。
     *
     * <p>禁止在本方法内跨聚合写药品/耗材/服务请求或发放表；仅组装 {@link CreateChargeItemCommand} 并复用既有「先落库后发事件」路径（B.14）。应用层 IF-DRA-03 编排本期不落码，由调用方事务内显式编排。</p>
     *
     * @param cmd 源结算行主键与再生后锚点
     * @return 本批首条新行主键
     */
    public Long createChargeItemsLinkingRegeneratedAnchors(CreateChargeItemsLinkingRegeneratedAnchorsCommand cmd) {
        // Pre：解析半退保留链命令条目。
        List<CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item> entries = cmd.resolveItems();
        List<Long> orderedSourceIds = new ArrayList<>();
        // Loop：校验并收集源收费明细 ID。
        for (CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item entry : entries) {
            if (entry.getSourceChargeItemId() == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "sourceChargeItemId 不能为空");
            }
            orderedSourceIds.add(entry.getSourceChargeItemId());
        }
        LinkedHashSet<Long> uniqueSourceIds = new LinkedHashSet<>(orderedSourceIds);
        if (uniqueSourceIds.size() != orderedSourceIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "同一批次存在重复 sourceChargeItemId");
        }
        // Pre：批量加载源收费明细实体。
        Map<Long, ChargeItem> sourceMap = this.loadMap(orderedSourceIds);
        // Pre：组装创建命令，准备构建新收费明细。
        CreateChargeItemCommand createCmd = new CreateChargeItemCommand();
        createCmd.setOperatorId(cmd.getOperatorId());
        List<CreateChargeItemCommand.Item> createItems = new ArrayList<>();
        // Loop：逐条校验源行状态并组装新行。
        for (CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item anchor : entries) {
            ChargeItem src = this.require(sourceMap, anchor.getSourceChargeItemId());
            if (!ChargeItemStatus.SETTLED.getCode().equals(src.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    "保留链新行落库要求源行为 SETTLED，chargeItemId=" + src.getId());
            }
            if (StringUtils.isEmpty(anchor.getNewServiceTable()) || anchor.getNewServiceId() == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "再生后 service_table/service_id 不能为空");
            }
            boolean dispenseTableBlank = StringUtils.isEmpty(anchor.getNewDispenseTable());
            if (dispenseTableBlank && anchor.getNewDispenseId() != null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "发放表名为空时发放主键须为空");
            }
            if (!dispenseTableBlank && anchor.getNewDispenseId() == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "发放主键为空时发放表名须为空");
            }
            // Pre：组装新收费明细创建项。
            createItems.add(this.buildCreateItemForPreservedRegeneration(src, anchor));
        }
        createCmd.setItems(createItems);
        return this.createByCommand(createCmd, ChargeItemStatus.PLANNED, ChargeItemEventType.PRESERVED_LINE_REGENERATED_CHARGE_CREATED);
    }

    /**
     * 预结算收费明细（DM-ADM-011 §6.1.9）。
     *
     * <p>仅 PLANNED（statusEnum=PLANNED）状态的明细允许进入预结算流程；
     * 先加载实体校验状态门禁、落库确认，再发布预结算事件（B.14 硬门禁）。</p>
     *
     * @param cmd 批量收费明细主键命令
     * @throws BusinessException 入参为空或存在非 PLANNED 状态明细
     */
    public void preSettleChargeItems(BatchChargeItemIdsCommand cmd) {
        // Pre：从批量命令中提取收费明细 ID 列表。
        List<Long> ids = this.extractIds(cmd);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细批量入参不能为空");
        }
        // Pre：批量加载目标明细，用于后续状态校验。
        Map<Long, ChargeItem> map = this.loadMap(ids);
        // Loop：逐条校验预结算门禁，DM-ADM-011 §2.3 要求明细处于 PLANNED 状态。
        for (Long id : ids) {
            ChargeItem row = require(map, id);
            if (!ChargeItemStatus.PLANNED.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "收费明细 " + id + " 状态非 PLANNED，不允许预结算");
            }
        }
        repository.saveAll(new ArrayList<>(map.values()));
        // Event：发布预结算领域事件，以首条 ID 代表批次。
        domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemPreSettledEvent(ids.get(0), ids.size()));
    }

    /**
     * 住院收费预结算快照：校验待结算明细，不改写主状态（DM-ADM-011 §6.1.9b，AS-IP-FIN-003）。
     *
     * @param cmd 批量收费明细主键命令
     */
    public void preSettleInpatientChargeItems(BatchChargeItemIdsCommand cmd) {
        List<Long> ids = this.extractIds(cmd);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细批量入参不能为空");
        }
        Map<Long, ChargeItem> map = this.loadMap(ids);
        for (Long id : ids) {
            ChargeItem row = require(map, id);
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "收费明细 " + id + " 状态非待结算，不允许住院预结算");
            }
        }
        repository.saveAll(new ArrayList<>(map.values()));
        domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemPreSettledEvent(ids.get(0), ids.size()));
    }

    /**
     * 申请门诊账单退款，状态迁移至退款中（statusEnum=REFUNDING），并发布 OUTPATIENT_BILL_REFUND_REQUESTED 事件。
     *
     * @param cmd 批量收费明细主键命令
     */
    public void requestOutpatientBillRefund(BatchChargeItemIdsCommand cmd) {
        this.batchUpdateStatus(cmd, ChargeItemStatus.REFUNDING, ChargeItemEventType.OUTPATIENT_BILL_REFUND_REQUESTED);
    }

    /**
     * 完成门诊账单退款，状态迁移至已退款（statusEnum=REFUNDED），覆盖收费人/收费时间并发布 OUTPATIENT_BILL_REFUND_COMPLETED 事件。
     *
     * @param cmd 批量收费明细主键命令（须含退费操作人；{@code billDate} 可选）
     */
    public void completeOutpatientBillRefund(BatchChargeItemIdsCommand cmd) {
        this.batchUpdateStatusWithCashierStamp(
            cmd, ChargeItemStatus.REFUNDED, ChargeItemEventType.OUTPATIENT_BILL_REFUND_COMPLETED);
    }

    /**
     * 结算住院收费项目，状态迁移至已结算（statusEnum=SETTLED），落库收费人/收费时间并发布 INPATIENT_ITEMS_SETTLED 事件。
     *
     * @param cmd 批量收费明细主键命令，须含非空 {@code operatorId}（收费人医务人员主键）
     */
    public void settleInpatientChargeItems(BatchChargeItemIdsCommand cmd) {
        List<Long> ids = this.extractIds(cmd);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细批量入参不能为空");
        }
        Long cashierPractitionerId = cmd.getOperatorId();
        if (cashierPractitionerId == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "住院收费结算须携带收费人（operatorId）");
        }
        Map<Long, ChargeItem> map = this.loadMap(ids);
        for (Long id : ids) {
            ChargeItem row = require(map, id);
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "收费明细 " + id + " 状态非待结算，不允许住院结算");
            }
        }
        List<ChargeItem> toSettle = repository.findAllByIdInAndStatusEnum(
            ids, ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        if (toSettle.size() != ids.size()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                "收费明细状态已变更，不允许重复结算");
        }
        for (ChargeItem row : toSettle) {
            row.setStatusEnum(ChargeItemStatus.SETTLED.getCode());
            // Cmd：住院结算同样落 enterer_* 为收费人/收费时间
            this.applyCashierStamp(row, cashierPractitionerId, cmd.getBillDate());
        }
        repository.saveAll(toSettle);
        this.publishByType(ChargeItemEventType.INPATIENT_ITEMS_SETTLED, ids.get(0), ids);
    }

    /**
     * 住院反结再生收费明细：源 {@code SETTLED} 行保持终态不动，批量插入 {@code PENDING_SETTLEMENT} 新行并写 {@code refund_id} 链回源行（DM-ADM-011 §6.1.12a，AS-IP-FIN-004）。
     *
     * <p>与门诊半退 {@link #createChargeItemsLinkingRegeneratedAnchors} 对称：禁止改写历史已结算行，保证支付快照绑定的明细在结算历史中可查。</p>
     *
     * @param cmd 源已结算收费明细主键命令（通常为支付单 {@code charge_item_ids} 快照子集）
     */
    public void revertInpatientSettledChargeItems(BatchChargeItemIdsCommand cmd) {
        List<Long> sourceIds = this.extractIds(cmd);
        if (sourceIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细批量入参不能为空");
        }
        Map<Long, ChargeItem> sourceMap = this.loadMap(sourceIds);
        LinkedHashSet<Long> sourceIdSet = new LinkedHashSet<>(sourceIds);
        List<String> busNos = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.CHARGE_ITEM, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, sourceIds.size());
        List<ChargeItem> toSave = new ArrayList<>();
        for (int index = 0; index < sourceIds.size(); index++) {
            Long sourceId = sourceIds.get(index);
            ChargeItem sourceRow = require(sourceMap, sourceId);
            // Pre：源行须仍为 SETTLED，已再生或已变更则拒绝重复反结。
            if (!ChargeItemStatus.SETTLED.getCode().equals(sourceRow.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "收费明细 " + sourceId + " 状态非已结算，不允许反结再生");
            }
            toSave.add(this.cloneChargeItemForInpatientReverseSettle(sourceRow, busNos.get(index), cmd.getOperatorId()));
        }
        List<ChargeItem> saved = repository.saveAll(toSave);
        Map<Long, Long> sourceToNewId = new HashMap<>();
        for (ChargeItem savedRow : saved) {
            if (savedRow.getRefundId() != null) {
                sourceToNewId.put(savedRow.getRefundId(), savedRow.getId());
            }
        }
        List<ChargeItem> parentRemapBatch = new ArrayList<>();
        for (ChargeItem savedRow : saved) {
            ChargeItem sourceRow = sourceMap.get(savedRow.getRefundId());
            if (sourceRow == null || sourceRow.getParentId() == null) {
                continue;
            }
            // 组套子行：仅当父行亦在本批再生时，将 parent_id 链到新父行主键。
            if (!sourceIdSet.contains(sourceRow.getParentId())) {
                continue;
            }
            Long newParentId = sourceToNewId.get(sourceRow.getParentId());
            if (newParentId == null) {
                continue;
            }
            if (!newParentId.equals(savedRow.getParentId())) {
                savedRow.setParentId(newParentId);
                parentRemapBatch.add(savedRow);
            }
        }
        if (!parentRemapBatch.isEmpty()) {
            repository.saveAll(parentRemapBatch);
        }
        List<Long> newIds = new ArrayList<>();
        for (ChargeItem savedRow : saved) {
            newIds.add(savedRow.getId());
        }
        // Event：发布反结再生事件，携带本批新行主键供待结算队列刷新。
        if (!newIds.isEmpty()) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.InpatientChargeItemsRevertedEvent(newIds.get(0), newIds));
        }
    }

    /**
     * 申请住院账单退款，状态迁移至退款中（statusEnum=REFUNDING），并发布 INPATIENT_BILL_REFUND_REQUESTED 事件。
     *
     * @param cmd 批量收费明细主键命令
     */
    public void requestInpatientBillRefund(BatchChargeItemIdsCommand cmd) {
        this.batchUpdateStatus(cmd, ChargeItemStatus.REFUNDING, ChargeItemEventType.INPATIENT_BILL_REFUND_REQUESTED);
    }

    /**
     * 完成住院账单退款，状态迁移至已退款（statusEnum=REFUNDED），覆盖收费人/收费时间并发布 INPATIENT_BILL_REFUND_COMPLETED 事件。
     *
     * @param cmd 批量收费明细主键命令（须含退费操作人；{@code billDate} 可选）
     */
    public void completeInpatientBillRefund(BatchChargeItemIdsCommand cmd) {
        this.batchUpdateStatusWithCashierStamp(
            cmd, ChargeItemStatus.REFUNDED, ChargeItemEventType.INPATIENT_BILL_REFUND_COMPLETED);
    }

    /**
     * 创建收费明细的内部实现：构建实体 → 批量落库 → 逐条发布事件。
     *
     * @param cmd       创建命令，包含一条或多条创建项
     * @param status    初始状态枚举
     * @param eventType 事件类型
     * @return 首条新建明细主键
     * @throws BusinessException 创建项列表为空
     */
    private Long createByCommand(CreateChargeItemCommand cmd, ChargeItemStatus status, ChargeItemEventType eventType) {
        // Pre：解析创建命令条目列表。
        List<CreateChargeItemCommand.Item> items = cmd.resolveItems();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细创建入参不能为空");
        }
        // Pre：按业务日批量生成业务流水号。
        List<String> busNos = businessSequenceService.getSeqByDay(AggregateTypeConstants.CHARGE_ITEM, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, items.size());
        List<ChargeItem> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        // Loop：遍历命令项，逐条组装收费明细实体。
        for (int index = 0; index < items.size(); index++) {
            CreateChargeItemCommand.Item item = items.get(index);
            ChargeItem row = new ChargeItem();
            // 患者、就诊与定价锚点：决定费用归属与追溯。
            row.setPatientId(item.getPatientId());
            row.setEncounterId(item.getEncounterId());
            row.setAccountId(item.getAccountId());
            row.setDefinitionId(item.getDefinitionId());
            // 总价缺省为零，避免空指针；与后续折后/原价链一致时再校验业务含义。
            BigDecimal totalPrice = item.getTotalPrice();
            if (totalPrice == null) {
                totalPrice = BigDecimal.ZERO;
            }
            row.setTotalPrice(totalPrice);
            // 单价缺省跟随总价（单行挂号等场景一口价）。
            BigDecimal unitPrice = item.getUnitPrice();
            if (unitPrice == null) {
                unitPrice = totalPrice;
            }
            row.setUnitPrice(unitPrice);
            // 数量缺省为 1，单位缺省为「次」，满足 DM 必填且兼容仅传金额的入口。
            BigDecimal quantityValue = item.getQuantityValue();
            if (quantityValue == null) {
                quantityValue = BigDecimal.ONE;
            }
            row.setQuantityValue(quantityValue);
            String quantityUnit = item.getQuantityUnit();
            if (StringUtils.isEmpty(quantityUnit)) {
                quantityUnit = ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT;
            }
            row.setQuantityUnit(quantityUnit);
            BigDecimal baseAmount = item.getBaseAmount();
            if (baseAmount == null) {
                baseAmount = totalPrice;
            }
            row.setBaseAmount(baseAmount);
            BigDecimal discountAmount = item.getDiscountAmount();
            if (discountAmount == null) {
                discountAmount = totalPrice;
            }
            row.setDiscountAmount(discountAmount);
            BigDecimal surchargeAmount = item.getSurchargeAmount();
            if (surchargeAmount == null) {
                surchargeAmount = BigDecimal.ZERO;
            }
            row.setSurchargeAmount(surchargeAmount);
            // 费用发生时刻：命令未带则取当前时刻，与批量取号同一事务内时间轴一致。
            LocalDateTime occurrenceTime = item.getOccurrenceTime();
            if (occurrenceTime == null) {
                occurrenceTime = now;
            }
            row.setOccurrenceTime(occurrenceTime);
            // 业务来源表名缺省为就诊表，锚定挂号/就诊维度（BR-CHG-001）。
            String serviceTable = item.getServiceTable();
            if (StringUtils.isEmpty(serviceTable)) {
                serviceTable = TableNameConstants.ADM_ENCOUNTER;
            }
            row.setServiceTable(serviceTable);
            // 上游主键缺省时回落为就诊主键，保证非空锚点与读模型按就诊汇总一致。
            Long serviceId = item.getServiceId();
            if (serviceId == null) {
                serviceId = item.getEncounterId();
            }
            row.setServiceId(serviceId);
            // 门诊医生站 FIFO 分段：命令可同时携带「医嘱请求 + 产品定义 + 发放分段」三锚点（DM-ADM-011 §3.1），便于按请求汇总且按发放行唯一计费；缺省时各列为 null，不影响挂号费等仅锚就诊的路径。
            row.setProductTable(item.getProductTable());
            row.setProductId(item.getProductId());
            row.setDispenseTable(item.getDispenseTable());
            row.setDispenseId(item.getDispenseId());
            if (StringUtils.isNotEmpty(item.getPrescriptionNo())) {
                row.setPrescriptionNo(item.getPrescriptionNo().trim());
            }
            row.setProcedureId(item.getProcedureId());
            Long refundRootChargeItemId = item.getRefundRootChargeItemId();
            // 半退场景须记录源收费明细 ID，建立保留链供读侧追溯。
            if (refundRootChargeItemId != null) {
                row.setRefundId(refundRootChargeItemId);
            }
            // 挂费诊断：命令显式传入优先；否则按上游请求表锚点拷贝 encounter_diagnosis_ids（DD §7.1）
            String encounterDiagnosisIds = item.getEncounterDiagnosisIds();
            if (StringUtils.isEmpty(encounterDiagnosisIds)) {
                encounterDiagnosisIds = this.resolveEncounterDiagnosisIdsFromServiceAnchor(serviceTable, serviceId);
            }
            if (StringUtils.isNotEmpty(encounterDiagnosisIds)) {
                row.setEncounterDiagnosisIds(encounterDiagnosisIds.trim());
            }
            row.setOrgId(item.getOrgId());
            row.setRequestingOrgId(item.getRequestingOrgId());
            // 创建不写 enterer_*：收费人/收费时间仅 settle / 退费完成时落库；开单医生在 Request.requester_id
            // 护士划价/滚费/执行计费：操作人落 performer_id（非收费人），供护士站费用明细「操作人」展示
            if (cmd.getOperatorId() != null && this.isNurseOperatorPerformerEvent(eventType)) {
                row.setPerformerId(cmd.getOperatorId());
            }
            // 生成来源与收费上下文：命令可覆盖，否则按事件类型补全 DM 存储码。
            this.fillGenerateAndContext(row, item, eventType);
            row.setStatusEnum(status.getCode());
            row.setBusNo(busNos.get(index));
            row.assertPersistableNewRow();
            toSave.add(row);
        }
        List<ChargeItem> saved = repository.saveAll(toSave);
        List<Long> serviceIds = this.extractIdsFromCreate(items);
        // Event：按事件类型路由发布领域事件。
        for (int i = 0; i < saved.size(); i++) {
            ChargeItem row = saved.get(i);
            CreateChargeItemCommand.Item item = items.get(i);
            // 挂号创建事件须携带就诊与金额信息，供挂号列表投影刷新。
            if (eventType == ChargeItemEventType.REGISTRATION_CREATED) {
                ChargeItemDomainEvents.RegistrationChargeItemCreatedEvent registrationCreatedEvent =
                    new ChargeItemDomainEvents.RegistrationChargeItemCreatedEvent(row.getId());
                registrationCreatedEvent.setEncounterId(row.getEncounterId());
                registrationCreatedEvent.setTotalPrice(row.getTotalPrice());
                domainEventPublisher.publish(registrationCreatedEvent);
            } else if (eventType == ChargeItemEventType.PRESERVED_LINE_REGENERATED_CHARGE_CREATED) {
                // 半退保留链创建事件须携带源收费明细 ID，供读侧关联原行。
                ChargeItemDomainEvents.PreservedLineRegeneratedChargeItemCreatedEvent preservedRegeneratedEvent =
                    new ChargeItemDomainEvents.PreservedLineRegeneratedChargeItemCreatedEvent(row.getId());
                preservedRegeneratedEvent.setEncounterId(row.getEncounterId());
                preservedRegeneratedEvent.setSourceChargeItemId(item.getRefundRootChargeItemId());
                domainEventPublisher.publish(preservedRegeneratedEvent);
            } else {
                // 其他事件类型统一路由发布。
                this.publishByType(eventType, row.getId(), serviceIds);
            }
        }
        return saved.get(0).getId();
    }

    /**
     * 按上游请求表锚点回查 {@code encounter_diagnosis_ids}（挂费拷贝；挂号等非请求锚点返回 null）。
     *
     * @param serviceTable 上游表名
     * @param serviceId    上游主键
     * @return 有序 ids 串；无法解析时 null
     */
    private String resolveEncounterDiagnosisIdsFromServiceAnchor(String serviceTable, Long serviceId) {
        if (serviceId == null || StringUtils.isEmpty(serviceTable)) {
            return null;
        }
        String table = serviceTable.trim();
        // 药品请求：签发/暂存挂费主路径
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(table)) {
            try {
                MedicationRequestListQueryModel med = medicationRequestQueryService.mustGetListModelById(serviceId);
                if (med != null) {
                    return med.getEncounterDiagnosisIds();
                }
            } catch (BusinessException ex) {
                return null;
            }
            return null;
        }
        // 诊疗请求
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(table)) {
            try {
                ServiceRequestListModel svc = serviceRequestQueryService.mustGetListModelById(serviceId);
                if (svc != null) {
                    return svc.getEncounterDiagnosisIds();
                }
            } catch (BusinessException ex) {
                return null;
            }
            return null;
        }
        // 耗材请求
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(table)) {
            try {
                DeviceRequestListModel device = deviceRequestQueryService.mustGetListModelById(serviceId);
                if (device != null) {
                    return device.getEncounterDiagnosisIds();
                }
            } catch (BusinessException ex) {
                return null;
            }
            return null;
        }
        return null;
    }

    /**
     * 写入收费明细生成来源与收费上下文存储码；命令显式传入时优先，否则按本次写事件类型推断（DM §4.2 / §4.3）。
     *
     * @param row       待落库行（已具备发生时刻等基础字段）
     * @param item      创建命令条目（可携带覆盖码）
     * @param eventType 本次创建触发的事件类型（挂号/医嘱草稿/护士划价等）
     */
    private void fillGenerateAndContext(ChargeItem row, CreateChargeItemCommand.Item item, ChargeItemEventType eventType) {
        // 生成来源：命令显式传入优先；否则按本次写事件类型落 DM 默认存储码。
        if (StringUtils.isNotEmpty(item.getGenerateSourceEnum())) {
            row.setGenerateSourceEnum(item.getGenerateSourceEnum());
        } else if (eventType == ChargeItemEventType.REGISTRATION_CREATED) {
            row.setGenerateSourceEnum(GenerateSource.REGISTRATION_REGISTER.getCode());
        } else if (eventType == ChargeItemEventType.ORDER_DRAFT_CREATED) {
            row.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        } else if (eventType == ChargeItemEventType.NURSE_PRICING_CREATED) {
            row.setGenerateSourceEnum(GenerateSource.NURSE_PRICING.getCode());
        } else if (eventType == ChargeItemEventType.AUTO_ROLL_CREATED) {
            row.setGenerateSourceEnum(GenerateSource.AUTO_ROLL_FEES.getCode());
        } else if (eventType == ChargeItemEventType.ACTIVATED_BY_EXECUTION
            && GenerateSource.ORDER_EXECUTE.getCode().equals(item.getGenerateSourceEnum())) {
            row.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        } else if (eventType == ChargeItemEventType.PRESERVED_LINE_REGENERATED_CHARGE_CREATED) {
            row.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        } else {
            // 未命中已知事件分支时与挂号路径一致，避免空码阻断落库。
            row.setGenerateSourceEnum(GenerateSource.REGISTRATION_REGISTER.getCode());
        }
        // 收费上下文：命令显式传入优先；挂号场景落「挂号」，其余活动类费用落「活动」。
        if (StringUtils.isNotEmpty(item.getContextEnum())) {
            row.setContextEnum(item.getContextEnum());
        } else if (eventType == ChargeItemEventType.REGISTRATION_CREATED) {
            row.setContextEnum(ChargeItemDmConstants.CONTEXT_REGISTER);
        } else {
            row.setContextEnum(ChargeItemDmConstants.CONTEXT_ACTIVITY);
        }
    }

    /**
     * 批量状态更新的内部实现：加载实体 → 批量状态迁移 → 落库 → 按事件类型发布事件。
     *
     * <p>事件类型 1-8 属于单条粒度事件，逐条发布；
     * 事件类型 10-14 属于批量粒度事件，以首条 ID 代表整批次发布一次。</p>
     *
     * @param cmd       批量 ID 命令
     * @param status    目标状态枚举
     * @param eventType 事件类型
     * @throws BusinessException ID 列表为空
     */
    /**
     * 创建事件是否应将命令操作人写入 {@code performer_id}（划价/滚费/执行计费操作人，非收费人）。
     *
     * @param eventType 创建事件类型
     * @return 护士操作人事件时 true
     */
    private boolean isNurseOperatorPerformerEvent(ChargeItemEventType eventType) {
        if (eventType == ChargeItemEventType.NURSE_PRICING_CREATED) {
            return true;
        }
        if (eventType == ChargeItemEventType.AUTO_ROLL_CREATED) {
            return true;
        }
        return eventType == ChargeItemEventType.ACTIVATED_BY_EXECUTION;
    }

    /**
     * 结算/退费完成：将收费人与收费时间写入 {@code enterer_id}/{@code entered_date}。
     *
     * @param row                     收费明细行
     * @param cashierPractitionerId   收费人 {@code adm_practitioner.id}
     * @param billDate                与 payment {@code bill_date} 同值；空则取当前时刻
     */
    private void applyCashierStamp(ChargeItem row, Long cashierPractitionerId, LocalDateTime billDate) {
        row.setEntererId(cashierPractitionerId);
        if (billDate != null) {
            row.setEnteredDate(billDate);
        } else {
            row.setEnteredDate(LocalDateTime.now());
        }
    }

    /**
     * 退费终态推进并覆盖收费人/收费时间（与结算同源 enterer_* 口径）。
     *
     * @param cmd       须含非空 {@code operatorId}（退费操作人）
     * @param status    目标状态（通常 REFUNDED）
     * @param eventType 领域事件类型
     */
    private void batchUpdateStatusWithCashierStamp(
        BatchChargeItemIdsCommand cmd,
        ChargeItemStatus status,
        ChargeItemEventType eventType) {
        Long cashierPractitionerId = cmd.getOperatorId();
        if (cashierPractitionerId == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "退费完成须携带收费人（operatorId）");
        }
        // Pre：从批量命令中提取收费明细 ID 列表。
        List<Long> ids = this.extractIds(cmd);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细批量入参不能为空");
        }
        // Pre：批量加载目标明细，确认记录存在。
        Map<Long, ChargeItem> map = this.loadMap(ids);
        List<ChargeItem> toSave = new ArrayList<>();
        for (Long id : ids) {
            ChargeItem r = require(map, id);
            r.setStatusEnum(status.getCode());
            // Cmd：退费操作人覆盖 enterer_*，便于按收费人/收费时间筛退费事实
            this.applyCashierStamp(r, cashierPractitionerId, cmd.getBillDate());
            toSave.add(r);
        }
        repository.saveAll(toSave);
        // Event：与 batchUpdateStatus 同口径发布
        for (ChargeItem r : toSave) {
            if (!eventType.isBatchEvent()) {
                if (eventType == ChargeItemEventType.REGISTRATION_REFUNDED) {
                    ChargeItemDomainEvents.RegistrationChargeItemRefundedEvent refundEvent =
                        new ChargeItemDomainEvents.RegistrationChargeItemRefundedEvent(r.getId());
                    refundEvent.setEncounterId(r.getEncounterId());
                    domainEventPublisher.publish(refundEvent);
                } else {
                    this.publishByType(eventType, r.getId(), ids);
                }
            }
        }
        if (eventType.isBatchEvent() && !ids.isEmpty()) {
            this.publishByType(eventType, ids.get(0), ids);
        }
    }

    private void batchUpdateStatus(BatchChargeItemIdsCommand cmd, ChargeItemStatus status, ChargeItemEventType eventType) {
        // Pre：从批量命令中提取收费明细 ID 列表。
        List<Long> ids = this.extractIds(cmd);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细批量入参不能为空");
        }
        // Pre：批量加载目标明细，确认记录存在。
        Map<Long, ChargeItem> map = this.loadMap(ids);
        List<ChargeItem> toSave = new ArrayList<>();
        for (Long id : ids) {
            ChargeItem r = require(map, id);
            r.setStatusEnum(status.getCode());
            toSave.add(r);
        }
        repository.saveAll(toSave);
        // Event：事件类型 1-8 为单条记录粒度事件，逐条发布。
        for (ChargeItem r : toSave) {
            if (!eventType.isBatchEvent()) {
                // 挂号退费须携带就诊主键，供挂号列表与挂号单投影将金额摘要清零。
                if (eventType == ChargeItemEventType.REGISTRATION_REFUNDED) {
                    ChargeItemDomainEvents.RegistrationChargeItemRefundedEvent refundEvent =
                        new ChargeItemDomainEvents.RegistrationChargeItemRefundedEvent(r.getId());
                    refundEvent.setEncounterId(r.getEncounterId());
                    domainEventPublisher.publish(refundEvent);
                } else {
                    this.publishByType(eventType, r.getId(), ids);
                }
            }
        }
        // Event：事件类型 10-14 为批量粒度事件，以首条 ID 作为批次代表，统一发布一次。
        if (eventType.isBatchEvent() && !ids.isEmpty()) {
            this.publishByType(eventType, ids.get(0), ids);
        }
    }

    /**
     * 按事件类型路由并发布收费明细领域事件，替代原魔数分支（B.10.3）。
     *
     * <p>每个 if 分支对应 {@link ChargeItemEventType} 中的一个事件类型，
     * 发布完成后立即 return，避免多余匹配。</p>
     *
     * @param eventType 事件类型
     * @param id        触发事件的收费明细主键（批量事件时为首条代表 ID）
     * @param ids       批量操作的全量 ID 列表（单条事件时可忽略）
     */
    private void publishByType(ChargeItemEventType eventType, Long id, List<Long> ids) {
        if (eventType == ChargeItemEventType.REGISTRATION_CREATED) {
            throw new IllegalStateException("REGISTRATION_CREATED 事件须在 createByCommand 内携带 encounterId/totalPrice 发布");
        }
        if (eventType == ChargeItemEventType.PRESERVED_LINE_REGENERATED_CHARGE_CREATED) {
            throw new IllegalStateException(
                "PRESERVED_LINE_REGENERATED_CHARGE_CREATED 事件须在 createByCommand 内携带 encounterId/sourceChargeItemId 发布");
        }
        // Event：医嘱草稿项目已创建事件，明细处于草稿状态。
        if (eventType == ChargeItemEventType.ORDER_DRAFT_CREATED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.OrderDraftChargeItemCreatedEvent(id));
            return;
        }
        // Event：护理计价项目已创建事件，明细直接进入计费中状态。
        if (eventType == ChargeItemEventType.NURSE_PRICING_CREATED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.NursePricingChargeItemCreatedEvent(id));
            return;
        }
        // Event：住院滚费收费项目已创建事件，来源 auto-roll-fees，不计入护士站 pending 计数。
        if (eventType == ChargeItemEventType.AUTO_ROLL_CREATED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.AutoRollChargeItemCreatedEvent(id));
            return;
        }
        // Event：执行激活收费项目事件，明细从草稿升级为计费中。
        if (eventType == ChargeItemEventType.ACTIVATED_BY_EXECUTION) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemActivatedByExecutionEvent(id));
            return;
        }
        // Event：执行取消收费项目事件，明细从计费中回退为草稿。
        if (eventType == ChargeItemEventType.EXECUTION_CANCELLED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemExecutionCancelledEvent(id));
            return;
        }
        // Event：收费项目已结算事件，门诊或住院费用完成结算时触发。
        if (eventType == ChargeItemEventType.SETTLED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.ChargeItemSettledEvent(id));
            return;
        }
        if (eventType == ChargeItemEventType.REGISTRATION_REFUNDED) {
            throw new IllegalStateException("REGISTRATION_REFUNDED 事件须在 batchUpdateStatus 内携带 encounterId 发布");
        }
        // Event：门诊账单退款申请事件（批量），以首条 ID 代表整批次。
        if (eventType == ChargeItemEventType.OUTPATIENT_BILL_REFUND_REQUESTED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.OutpatientBillRefundRequestedEvent(id, ids));
            return;
        }
        // Event：门诊账单退款完成事件（批量）。
        if (eventType == ChargeItemEventType.OUTPATIENT_BILL_REFUND_COMPLETED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.OutpatientBillRefundCompletedEvent(id, ids));
            return;
        }
        // Event：住院收费项目批量结算事件。
        if (eventType == ChargeItemEventType.INPATIENT_ITEMS_SETTLED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.InpatientChargeItemsSettledEvent(id, ids));
            return;
        }
        // Event：住院收费项目批量反结回退事件。
        if (eventType == ChargeItemEventType.INPATIENT_ITEMS_REVERTED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.InpatientChargeItemsRevertedEvent(id, ids));
            return;
        }
        // Event：住院账单退款申请事件（批量）。
        if (eventType == ChargeItemEventType.INPATIENT_BILL_REFUND_REQUESTED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.InpatientBillRefundRequestedEvent(id, ids));
            return;
        }
        // Event：住院账单退款完成事件（批量）。
        if (eventType == ChargeItemEventType.INPATIENT_BILL_REFUND_COMPLETED) {
            domainEventPublisher.publish(new ChargeItemDomainEvents.InpatientBillRefundCompletedEvent(id, ids));
            return;
        }
    }

    /**
     * 从批量命令中提取收费明细主键列表。
     *
     * @param cmd 批量主键命令
     * @return 收费明细 ID 列表，可能为空
     */
    private List<Long> extractIds(BatchChargeItemIdsCommand cmd) {
        List<Long> ids = new java.util.ArrayList<>();
        // Loop：遍历命令项，收集所有收费明细 ID。
        for (BatchChargeItemIdsCommand.Item item : cmd.resolveItems()) {
            ids.add(item.getChargeItemId());
        }
        return ids;
    }

    /**
     * 从创建命令条目中提取上游业务主键（service_id）列表。
     *
     * @param items 创建命令条目列表
     * @return 上游业务主键列表
     */
    private List<Long> extractIdsFromCreate(List<CreateChargeItemCommand.Item> items) {
        List<Long> ids = new ArrayList<>();
        // Loop：遍历命令项，收集所有非空上游业务主键。
        for (CreateChargeItemCommand.Item item : items) {
            if (item.getServiceId() != null) {
                ids.add(item.getServiceId());
            }
        }
        return ids;
    }

    /**
     * 自源 {@code SETTLED} 行完整复刻业务事实并落为反结再生行（{@code PENDING_SETTLEMENT} + {@code refund_id}）。
     *
     * @param src        源已结算收费明细
     * @param busNo      新业务流水号
     * @param operatorId 操作员主键
     * @return 待持久化新行
     */
    private ChargeItem cloneChargeItemForInpatientReverseSettle(ChargeItem src, String busNo, Long operatorId) {
        ChargeItem row = new ChargeItem();
        row.setPatientId(src.getPatientId());
        row.setEncounterId(src.getEncounterId());
        row.setAccountId(src.getAccountId());
        row.setDefinitionId(src.getDefinitionId());
        row.setDefDetailId(src.getDefDetailId());
        row.setTotalPrice(src.getTotalPrice());
        row.setUnitPrice(src.getUnitPrice());
        row.setQuantityValue(src.getQuantityValue());
        row.setQuantityUnit(src.getQuantityUnit());
        row.setBaseAmount(src.getBaseAmount());
        row.setDiscountAmount(src.getDiscountAmount());
        row.setSurchargeAmount(src.getSurchargeAmount());
        row.setSystemDiscountPrice(src.getSystemDiscountPrice());
        row.setManualAdjustedPrice(src.getManualAdjustedPrice());
        row.setOverrideReasonCode(src.getOverrideReasonCode());
        row.setOverrideReasonText(src.getOverrideReasonText());
        row.setOccurrenceTime(src.getOccurrenceTime());
        row.setServiceTable(src.getServiceTable());
        row.setServiceId(src.getServiceId());
        row.setProductTable(src.getProductTable());
        row.setProductId(src.getProductId());
        row.setProcedureId(src.getProcedureId());
        row.setDispenseTable(src.getDispenseTable());
        row.setDispenseId(src.getDispenseId());
        row.setPrescriptionNo(src.getPrescriptionNo());
        row.setParentId(src.getParentId());
        row.setPerformerId(src.getPerformerId());
        row.setPerformingOrgId(src.getPerformingOrgId());
        row.setCostOrgId(src.getCostOrgId());
        row.setOrgId(src.getOrgId());
        row.setRequestingOrgId(src.getRequestingOrgId());
        row.setGenerateSourceEnum(src.getGenerateSourceEnum());
        row.setContextEnum(src.getContextEnum());
        row.setEncounterDiagnosisIds(src.getEncounterDiagnosisIds());
        row.setTcmFlag(src.getTcmFlag());
        row.setClaimStateEnum(src.getClaimStateEnum());
        row.setPrintCount(src.getPrintCount());
        row.setRefundId(src.getId());
        row.setStatusEnum(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        row.setBusNo(busNo);
        // 反结再生行待再结算：enterer_* 留空，待 settleInpatientChargeItems 写入
        row.assertPersistableNewRow();
        return row;
    }

    /**
     * 组装半退保留链单条创建项：可对齐金额与生成上下文自源 SETTLED 行复刻；service/dispense 取再生锚点；{@code refundRootChargeItemId} 链回源行。
     *
     * @param src    源结算行
     * @param anchor 再生后上游与发放锚点
     * @return 创建命令条目
     */
    private CreateChargeItemCommand.Item buildCreateItemForPreservedRegeneration(
        ChargeItem src,
        CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item anchor
    ) {
        // Pre：创建新命令条目，复刻源行金额与上下文信息。
        CreateChargeItemCommand.Item out = new CreateChargeItemCommand.Item();
        // 基础关联信息复刻：患者、就诊、账户、定价定义。
        out.setPatientId(src.getPatientId());
        out.setEncounterId(src.getEncounterId());
        out.setAccountId(src.getAccountId());
        out.setDefinitionId(src.getDefinitionId());
        // 金额信息复刻：总价、单价、数量、单位、基础金额、折扣金额、附加金额。
        out.setTotalPrice(src.getTotalPrice());
        out.setUnitPrice(src.getUnitPrice());
        out.setQuantityValue(src.getQuantityValue());
        out.setQuantityUnit(src.getQuantityUnit());
        out.setBaseAmount(src.getBaseAmount());
        out.setDiscountAmount(src.getDiscountAmount());
        out.setSurchargeAmount(src.getSurchargeAmount());
        out.setOccurrenceTime(src.getOccurrenceTime());
        // 上游锚点更新：使用再生后的 service 锚点。
        out.setServiceTable(anchor.getNewServiceTable());
        out.setServiceId(anchor.getNewServiceId());
        // 产品锚点复刻。
        out.setProductTable(src.getProductTable());
        out.setProductId(src.getProductId());
        // 再生输入未携带发放锚点时清空 dispense 字段，避免沿用旧分段计费键。
        if (StringUtils.isEmpty(anchor.getNewDispenseTable())) {
            out.setDispenseTable(null);
            out.setDispenseId(null);
        } else {
            out.setDispenseTable(anchor.getNewDispenseTable());
            out.setDispenseId(anchor.getNewDispenseId());
        }
        // 组织机构信息复刻。
        out.setOrgId(src.getOrgId());
        out.setRequestingOrgId(src.getRequestingOrgId());
        // 生成来源与收费上下文复刻。
        out.setGenerateSourceEnum(src.getGenerateSourceEnum());
        out.setContextEnum(src.getContextEnum());
        // 建立保留链：记录源收费明细 ID。
        out.setRefundRootChargeItemId(src.getId());
        return out;
    }

    /**
     * 将已加载的收费明细实体列表装配为 {@link BatchChargeItemIdsCommand}，供批量状态迁移复用。
     *
     * @param persistedRows 仓储查询结果，每条须含有效主键
     * @return 已设置 {@code items} 的批量命令；调用方负责后续设置 {@code operatorId} 等扩展字段（若需要）
     */
    private BatchChargeItemIdsCommand toBatchChargeItemIdsCommand(List<ChargeItem> persistedRows) {
        BatchChargeItemIdsCommand cmd = new BatchChargeItemIdsCommand();
        List<BatchChargeItemIdsCommand.Item> commandItems = new ArrayList<>();
        // Loop：遍历已加载实体，组装批量命令项。
        for (ChargeItem persistedRow : persistedRows) {
            BatchChargeItemIdsCommand.Item batchItem = new BatchChargeItemIdsCommand.Item();
            batchItem.setChargeItemId(persistedRow.getId());
            commandItems.add(batchItem);
        }
        cmd.setItems(commandItems);
        return cmd;
    }

    /**
     * 判断收费明细是否可被护士站临时医嘱执行激活（PLANNED 或 DRAFT）。
     *
     * @param statusEnum 收费状态持久化码
     * @return 可激活时 true
     */
    private boolean isActivatableByExecutionStatus(String statusEnum) {
        if (ChargeItemStatus.PLANNED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.DRAFT.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 是否已进入禁止取消执行回退的收费状态（SETTLED/REFUNDING/REFUNDED）。
     *
     * @param statusEnum 收费状态持久化码
     * @return 禁止回退时为 true
     */
    private boolean isNonRollbackableChargeStatus(String statusEnum) {
        if (ChargeItemStatus.SETTLED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDED.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 解析同锚点/同 procedure 下已被半退或住院反结再生替代的源 {@code SETTLED} 行（BR-REF-020 / BR-IP-RVS-011）。
     *
     * <p>多轮结算/反结时沿 {@code refund_id} 链向上追溯，链末端存在有效再生叶则整条链上 SETTLED 祖先均忽略。</p>
     *
     * @param encounterId 就诊主键
     * @param linkedRows  关联费用行
     * @return 应忽略的源 SETTLED 行主键集合
     */
    private Set<Long> resolveSupersededSettledSourceIds(Long encounterId, List<ChargeItem> linkedRows) {
        if (encounterId == null || linkedRows == null || linkedRows.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> supersededAncestorIds = regenerationChainSupport.collectSupersededSettledAncestorIds(encounterId);
        if (supersededAncestorIds.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Long> supersededInScope = new LinkedHashSet<>();
        for (ChargeItem row : linkedRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (supersededAncestorIds.contains(row.getId())) {
                supersededInScope.add(row.getId());
            }
        }
        return supersededInScope;
    }

    /**
     * 有效再生行状态（门诊半退 PLANNED、住院反结 PENDING_SETTLEMENT）。
     *
     * @param statusEnum 收费状态 code
     * @return 有效再生行时 true
     */
    private boolean isActiveSupersedingRegenerationStatus(String statusEnum) {
        return regenerationChainSupport.isActiveSupersedingRegenerationStatus(statusEnum);
    }

    /**
     * 执行记录主键入参去重归一化。
     *
     * @param procedureIds 原始集合
     * @return 非空去重集合
     */
    private Collection<Long> distinctNonNullProcedureIds(Collection<Long> procedureIds) {
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        if (procedureIds == null) {
            return idSet;
        }
        for (Long procedureId : procedureIds) {
            if (procedureId != null) {
                idSet.add(procedureId);
            }
        }
        return idSet;
    }

    /**
     * 按主键从内存映射中加载收费明细；若不存在则以主键为键构建批量查询索引。
     *
     * @param ids 收费明细主键列表
     * @return 以主键为键的内存映射，用于后续 O(1) 查找
     */
    private Map<Long, ChargeItem> loadMap(List<Long> ids) {
        // Pre：批量查询收费明细实体。
        List<ChargeItem> list = repository.findAllByIdIn(ids);
        // Asm：将查询结果转为以主键为键的内存映射，避免后续遍历时逐条查询（O(1) 查找）。
        Map<Long, ChargeItem> map = new HashMap<>();
        for (ChargeItem row : list) {
            map.put(row.getId(), row);
        }
        return map;
    }

    /**
     * 从内存映射中强制获取指定主键的收费明细；若不存在则抛出业务异常，终止操作。
     *
     * @param map 以主键为键的收费明细内存映射
     * @param id  目标收费明细主键
     * @return 对应的收费明细实体
     * @throws BusinessException 收费明细不存在（CHARGE_ITEM_NOT_FOUND）
     */
    private ChargeItem require(Map<Long, ChargeItem> map, Long id) {
        ChargeItem row = map.get(id);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费明细不存在");
        }
        return row;
    }
}
