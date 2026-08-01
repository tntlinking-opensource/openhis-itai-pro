/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.service;

import com.openhis.health.domain.financial.cashiersettlement.command.ApproveCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.command.RejectCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.command.WithdrawCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.entity.CashierSettlement;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.repository.CashierSettlementRepository;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentReconciliationRepository;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.PreviewPharmacyCashierSettlementQuery;
import com.openhis.health.domain.medication.medicationdispense.repository.MedicationDispenseRepository;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.health.domain.share.events.financial.cashiersettlement.CashierSettlementApprovedEvent;
import com.openhis.health.domain.share.events.financial.cashiersettlement.CashierSettlementRejectedEvent;
import com.openhis.health.domain.share.events.financial.cashiersettlement.CashierSettlementSubmittedEvent;
import com.openhis.health.domain.share.events.financial.cashiersettlement.CashierSettlementWithdrawnEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 交账批次写侧领域服务（DM-FIN-003 v3.0）。
 *
 * <p>写方法：submit / withdraw / reject / approve。收款挂载经 Payment QS 锁定候选（B.6.4）；
 * 药房挂载经 MedicationDispense QS 锁定候选（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class CashierSettlementDomainService {

    private final CashierSettlementRepository cashierSettlementRepository;
    private final PaymentReconciliationRepository paymentReconciliationRepository;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationDispenseRepository medicationDispenseRepository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 提交交账：insert 头表（SUBMITTED）并批量挂载源行。
     *
     * @param command 类型、范围键、业务窗、勾选源行与操作人
     * @return 新建交账头主键
     * @throws BusinessException 参数非法、空候选或并发挂载冲突
     */
    public Long submitCashierSettlement(SubmitCashierSettlementCommand command) {
        // 前置校验：命令必填项与业务窗合法
        this.assertSubmitCommandValid(command);
        if (CashierSettlementType.isPharmacyType(command.getTypeEnum())) {
            return this.submitPharmacyCashierSettlement(command);
        }
        return this.submitPaymentCashierSettlement(command);
    }

    /**
     * 提交药房日结：insert 头表并批量挂载发药行。
     *
     * @param command 药房交账命令
     * @return 新建交账头主键
     */
    private Long submitPharmacyCashierSettlement(SubmitCashierSettlementCommand command) {
        PreviewPharmacyCashierSettlementQuery previewQuery = this.toPharmacyPreviewQuery(command);
        // 加载候选：与预览共用 MedicationDispense 读服务口径（B.6.4）
        List<MedicationDispense> candidates =
            this.medicationDispenseQueryService.findUnsettledDispensesForCashierSettlement(previewQuery);
        // 无可交账发药则拒绝提交，避免空批次
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_EMPTY_CANDIDATES, "当前条件下没有未交账流水");
        }
        List<Long> selectedIds = this.resolveSelectedSourceIds(command);
        // Pre：按勾选子集过滤；null 回落全量候选
        List<MedicationDispense> toMount = this.resolveMountDispenseCandidates(candidates, selectedIds);
        // 勾选后仍为空则拒绝
        if (toMount.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_EMPTY_CANDIDATES, "当前条件下没有未交账流水");
        }
        LocalDateTime submittedAt = LocalDateTime.now();
        String busNo = this.resolveBusNo();
        Long submitterId = this.resolveSubmitterId(command);
        String practitionerIds = this.resolvePractitionerIds(command);
        CashierSettlement settlement = new CashierSettlement();
        settlement.setBusNo(busNo);
        settlement.setTypeEnum(command.getTypeEnum());
        settlement.setOrgId(command.getOrgId());
        settlement.setLocationId(command.getLocationId());
        settlement.setSubmitterId(submitterId);
        settlement.setDateFrom(command.getDateFrom());
        settlement.setDateTo(command.getDateTo());
        settlement.setPractitionerIds(practitionerIds);
        settlement.setPaymentCount(command.getPaymentCount());
        settlement.setTotalIncome(command.getTotalIncome());
        settlement.initSubmitted(submittedAt);
        settlement.assertBusNoNotBlank();
        settlement.assertPaymentScopeKeys();
        CashierSettlement savedSettlement = this.cashierSettlementRepository.save(settlement);
        List<MedicationDispense> toUpdate = new ArrayList<>();
        for (MedicationDispense dispense : toMount) {
            // 并发交账：行级校验仍为未挂载，否则 attach 抛错整单回滚
            dispense.attachCashierSettlement(savedSettlement.getId());
            toUpdate.add(dispense);
        }
        this.medicationDispenseRepository.saveAll(toUpdate);
        CashierSettlementSubmittedEvent event = new CashierSettlementSubmittedEvent(
            savedSettlement.getId(),
            savedSettlement.getBusNo(),
            savedSettlement.getTypeEnum(),
            submitterId,
            savedSettlement.getOrgId(),
            savedSettlement.getSubmittedAt(),
            toUpdate.size(),
            command.getOperatorId()
        );
        this.domainEventPublisher.publish(event);
        return savedSettlement.getId();
    }

    /**
     * 提交收款日结：insert 头表并批量挂载 payment。
     *
     * @param command 收款交账命令
     * @return 新建交账头主键
     */
    private Long submitPaymentCashierSettlement(SubmitCashierSettlementCommand command) {
        PreviewCashierSettlementQuery previewQuery = this.toPreviewQuery(command);
        // 加载候选：与预览共用 Payment 读服务口径（B.6.4）
        List<PaymentReconciliation> candidates =
            this.paymentReconciliationQueryService.findUnsettledPaymentsForCashierSettlement(previewQuery);
        // 无可交账流水则拒绝提交，避免空批次
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_EMPTY_CANDIDATES, "当前条件下没有未交账流水");
        }
        List<Long> selectedIds = this.resolveSelectedSourceIds(command);
        // Pre：按勾选子集过滤；null 回落全量候选
        List<PaymentReconciliation> toMount = this.resolveMountCandidates(candidates, selectedIds);
        // 勾选后仍为空则拒绝
        if (toMount.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_EMPTY_CANDIDATES, "当前条件下没有未交账流水");
        }
        LocalDateTime submittedAt = LocalDateTime.now();
        String busNo = this.resolveBusNo();
        Long submitterId = this.resolveSubmitterId(command);
        String practitionerIds = this.resolvePractitionerIds(command);
        // 组装交账头：新建即 SUBMITTED 待审
        CashierSettlement settlement = new CashierSettlement();
        settlement.setBusNo(busNo);
        settlement.setTypeEnum(command.getTypeEnum());
        settlement.setOrgId(command.getOrgId());
        settlement.setLocationId(command.getLocationId());
        settlement.setSubmitterId(submitterId);
        settlement.setDateFrom(command.getDateFrom());
        settlement.setDateTo(command.getDateTo());
        settlement.setPractitionerIds(practitionerIds);
        settlement.setPaymentCount(command.getPaymentCount());
        settlement.setTotalIncome(command.getTotalIncome());
        settlement.initSubmitted(submittedAt);
        settlement.assertBusNoNotBlank();
        settlement.assertPaymentScopeKeys();
        // 持久化：先写交账头，再批量挂载 payment
        CashierSettlement savedSettlement = this.cashierSettlementRepository.save(settlement);
        List<PaymentReconciliation> toUpdate = new ArrayList<>();
        for (PaymentReconciliation payment : toMount) {
            // 并发交账：行级校验仍为未挂载，否则 attach 抛错整单回滚
            payment.attachCashierSettlement(savedSettlement.getId());
            toUpdate.add(payment);
        }
        this.paymentReconciliationRepository.saveAll(toUpdate);
        // 发布事件：须在 save 之后、beforeCommit 同步投递
        CashierSettlementSubmittedEvent event = new CashierSettlementSubmittedEvent(
            savedSettlement.getId(),
            savedSettlement.getBusNo(),
            savedSettlement.getTypeEnum(),
            submitterId,
            savedSettlement.getOrgId(),
            savedSettlement.getSubmittedAt(),
            toUpdate.size(),
            command.getOperatorId()
        );
        this.domainEventPublisher.publish(event);
        return savedSettlement.getId();
    }

    /**
     * 撤回待审交账：清挂载并将头改为 WITHDRAWN。
     *
     * @param command 交账头与操作人
     */
    public void withdrawCashierSettlement(WithdrawCashierSettlementCommand command) {
        // Gate：命令与头主键必填
        if (command == null || command.getSettlementId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账头标识不能为空");
        }
        CashierSettlement settlement = this.requireSettlement(command.getSettlementId());
        // Cmd：仅 SUBMITTED 可撤回；清源行挂载后改态
        this.clearMountedSources(settlement);
        settlement.markWithdrawn();
        this.cashierSettlementRepository.save(settlement);
        CashierSettlementWithdrawnEvent event = new CashierSettlementWithdrawnEvent(
            settlement.getId(),
            command.getOperatorId());
        this.domainEventPublisher.publish(event);
    }

    /**
     * 驳回待审交账：清挂载并将头改为 REJECTED；写入驳回人与驳回时刻。
     *
     * @param command 交账头、驳回人与原因
     */
    public void rejectCashierSettlement(RejectCashierSettlementCommand command) {
        // Gate：命令与头主键必填
        if (command == null || command.getSettlementId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账头标识不能为空");
        }
        // Gate：驳回人必填，与通过路径一致，供四类日结/审批列表展示
        if (command.getApproverId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "审批人不能为空");
        }
        CashierSettlement settlement = this.requireSettlement(command.getSettlementId());
        LocalDateTime approvedAt = LocalDateTime.now();
        // Cmd：仅 SUBMITTED 可驳回；清源行挂载后改态并落审批人/时间
        this.clearMountedSources(settlement);
        settlement.markRejected(command.getApproverId(), approvedAt);
        this.cashierSettlementRepository.save(settlement);
        CashierSettlementRejectedEvent event = new CashierSettlementRejectedEvent(
            settlement.getId(),
            command.getApproverId(),
            approvedAt,
            command.getReason());
        this.domainEventPublisher.publish(event);
    }

    /**
     * 审批通过：SUBMITTED → APPROVED；不改挂载。
     *
     * @param command 交账头与审批人
     */
    public void approveCashierSettlement(ApproveCashierSettlementCommand command) {
        // Gate：命令与审批人必填
        if (command == null || command.getSettlementId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账头标识不能为空");
        }
        if (command.getApproverId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "审批人不能为空");
        }
        CashierSettlement settlement = this.requireSettlement(command.getSettlementId());
        LocalDateTime approvedAt = LocalDateTime.now();
        // Cmd：改态写审批人；挂载保持
        settlement.markApproved(command.getApproverId(), approvedAt);
        this.cashierSettlementRepository.save(settlement);
        CashierSettlementApprovedEvent event = new CashierSettlementApprovedEvent(
            settlement.getId(),
            command.getApproverId(),
            approvedAt);
        this.domainEventPublisher.publish(event);
    }

    /**
     * 交账提交命令 → 药房预览筛选 Query。
     *
     * @param command 交账命令
     * @return 药房预览读侧入参
     */
    private PreviewPharmacyCashierSettlementQuery toPharmacyPreviewQuery(SubmitCashierSettlementCommand command) {
        PreviewPharmacyCashierSettlementQuery query = new PreviewPharmacyCashierSettlementQuery();
        query.setTypeEnum(command.getTypeEnum());
        query.setLocationId(command.getLocationId());
        query.setDateFrom(command.getDateFrom());
        query.setDateTo(command.getDateTo());
        return query;
    }

    /**
     * 交账提交命令 → 收款预览筛选 Query。
     *
     * @param command 交账命令
     * @return 预览读侧入参
     */
    private PreviewCashierSettlementQuery toPreviewQuery(SubmitCashierSettlementCommand command) {
        PreviewCashierSettlementQuery query = new PreviewCashierSettlementQuery();
        query.setTypeEnum(command.getTypeEnum());
        query.setOrgId(command.getOrgId());
        query.setLocationIds(command.getLocationIds());
        query.setEntererMatchIds(command.getEntererMatchIds());
        query.setDateFrom(command.getDateFrom());
        query.setDateTo(command.getDateTo());
        return query;
    }

    /**
     * 解析勾选源行 ID：优先 selectedSourceIds，否则 selectedPaymentIds。
     *
     * @param command 提交命令
     * @return 勾选集；可能为 null
     */
    private List<Long> resolveSelectedSourceIds(SubmitCashierSettlementCommand command) {
        // 药房语义字段优先；收款存量仍用 selectedPaymentIds
        if (command.getSelectedSourceIds() != null) {
            return command.getSelectedSourceIds();
        }
        return command.getSelectedPaymentIds();
    }

    /**
     * 解析提交人（必填）。
     *
     * @param command 提交命令
     * @return 提交人主键
     */
    private Long resolveSubmitterId(SubmitCashierSettlementCommand command) {
        if (command.getSubmitterId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "提交人不能为空");
        }
        return command.getSubmitterId();
    }

    /**
     * 解析人员快照（可空）。
     *
     * @param command 提交命令
     * @return 逗号分隔人员快照；可空
     */
    private String resolvePractitionerIds(SubmitCashierSettlementCommand command) {
        return command.getPractitionerIds();
    }

    /**
     * 解析本次实际挂载的 dispense 实体列表。
     *
     * @param candidates          锁定后的未交账候选
     * @param selectedDispenseIds 勾选主键；null 表示整批
     * @return 待挂载实体
     */
    private List<MedicationDispense> resolveMountDispenseCandidates(
        List<MedicationDispense> candidates,
        List<Long> selectedDispenseIds) {
        // null 表示存量应用层未传勾选集，保持整批挂载语义
        if (selectedDispenseIds == null) {
            return candidates;
        }
        // 显式空列表视为无可交账流水
        if (selectedDispenseIds.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueSelected = new LinkedHashSet<>();
        for (Long dispenseId : selectedDispenseIds) {
            // 勾选主键不能为空，否则无法定位发药行
            if (dispenseId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
                    "所选流水不在待交账范围内");
            }
            uniqueSelected.add(dispenseId);
        }
        Map<Long, MedicationDispense> candidateById = new HashMap<>();
        for (MedicationDispense dispense : candidates) {
            candidateById.put(dispense.getId(), dispense);
        }
        List<MedicationDispense> toMount = new ArrayList<>();
        for (Long dispenseId : uniqueSelected) {
            MedicationDispense dispense = candidateById.get(dispenseId);
            // 越权或已交账 ID 不在锁定候选内，拒绝部分挂载
            if (dispense == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
                    "所选流水不在待交账范围内");
            }
            toMount.add(dispense);
        }
        return toMount;
    }

    /**
     * 解析本次实际挂载的 payment 实体列表。
     *
     * @param candidates         锁定后的未交账候选
     * @param selectedPaymentIds 勾选主键；null 表示整批
     * @return 待挂载实体
     */
    private List<PaymentReconciliation> resolveMountCandidates(
        List<PaymentReconciliation> candidates,
        List<Long> selectedPaymentIds) {
        // null 表示存量应用层未传勾选集，保持整批挂载语义
        if (selectedPaymentIds == null) {
            return candidates;
        }
        // 显式空列表视为无可交账流水
        if (selectedPaymentIds.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueSelected = new LinkedHashSet<>();
        for (Long paymentId : selectedPaymentIds) {
            // 勾选主键不能为空，否则无法定位 payment
            if (paymentId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
                    "所选流水不在待交账范围内");
            }
            uniqueSelected.add(paymentId);
        }
        Map<Long, PaymentReconciliation> candidateById = new HashMap<>();
        for (PaymentReconciliation payment : candidates) {
            candidateById.put(payment.getId(), payment);
        }
        List<PaymentReconciliation> toMount = new ArrayList<>();
        for (Long paymentId : uniqueSelected) {
            PaymentReconciliation payment = candidateById.get(paymentId);
            // 越权或已交账 ID 不在锁定候选内，拒绝部分挂载
            if (payment == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
                    "所选流水不在待交账范围内");
            }
            toMount.add(payment);
        }
        return toMount;
    }

    /**
     * 交账提交命令 Gate：范围键、类型与业务窗。
     *
     * @param command 交账命令
     */
    private void assertSubmitCommandValid(SubmitCashierSettlementCommand command) {
        // 命令体不能为空，否则无法构造筛选窗
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账命令不能为空");
        }
        if (command.getTypeEnum() == null || command.getTypeEnum().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账类型不能为空");
        }
        // 提交人必填（submitter 或存量 cashier）
        if (this.resolveSubmitterId(command) == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账提交人不能为空");
        }
        // 收款须科室；药房须药房（与实体 assert 同口径，提前失败）
        if (CashierSettlementType.isPharmacyType(command.getTypeEnum())) {
            if (command.getLocationId() == null) {
                throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "药房日结须指定药房");
            }
        } else if (command.getOrgId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "收款日结须指定科室");
        }
        if (command.getDateFrom() == null || command.getDateTo() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账业务时间范围不能为空");
        }
        // 业务窗止早于起则无法构成有效时间窗
        if (command.getDateTo().isBefore(command.getDateFrom())) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账业务时间范围不合法");
        }
    }

    /**
     * 加载交账头；不存在则拒绝。
     *
     * @param settlementId 交账头主键
     * @return 实体
     */
    private CashierSettlement requireSettlement(Long settlementId) {
        return this.cashierSettlementRepository.findById(settlementId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT,
                "交账记录不存在"));
    }

    /**
     * 按类型清空本批次源行挂载。
     *
     * @param settlement 交账头
     */
    private void clearMountedSources(CashierSettlement settlement) {
        Long settlementId = settlement.getId();
        // 药房类型清 dispense；收款清 payment
        if (CashierSettlementType.isPharmacyType(settlement.getTypeEnum())) {
            List<MedicationDispense> dispenses =
                this.medicationDispenseRepository.findByCashierSettlementIdAndIsDeletedFalse(settlementId);
            List<MedicationDispense> toUpdate = new ArrayList<>();
            for (MedicationDispense dispense : dispenses) {
                dispense.clearCashierSettlement(settlementId);
                toUpdate.add(dispense);
            }
            // 无挂载行仍允许改头态（审计一致）
            if (!toUpdate.isEmpty()) {
                this.medicationDispenseRepository.saveAll(toUpdate);
            }
            return;
        }
        List<PaymentReconciliation> payments =
            this.paymentReconciliationRepository.findByCashierSettlementIdAndIsDeletedFalse(settlementId);
        List<PaymentReconciliation> toUpdate = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            payment.clearCashierSettlement(settlementId);
            toUpdate.add(payment);
        }
        if (!toUpdate.isEmpty()) {
            this.paymentReconciliationRepository.saveAll(toUpdate);
        }
    }

    /**
     * 生成租户内唯一的交账单 busNo。
     *
     * @return 新单号
     */
    private String resolveBusNo() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        for (int attempt = 0; attempt < 10; attempt++) {
            String busNo = this.businessSequenceService.getSeqByDay(
                AggregateTypeConstants.CASHIER_SETTLEMENT,
                BusinessSequenceConstants.BUS_NO_SEQ_LENGTH
            );
            // 无租户上下文时跳过唯一性校验；有租户则避免 bus_no 冲突
            if (tenantId == null || !this.cashierSettlementRepository.existsByBusNoAndTenantId(busNo, tenantId)) {
                return busNo;
            }
        }
        throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_BUS_NO_CONFLICT, "交账单号生成失败，请稍后重试");
    }
}
