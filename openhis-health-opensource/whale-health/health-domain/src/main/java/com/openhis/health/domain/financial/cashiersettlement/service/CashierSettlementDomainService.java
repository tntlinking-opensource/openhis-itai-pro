/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.service;

import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.entity.CashierSettlement;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.repository.CashierSettlementRepository;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentReconciliationRepository;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.financial.cashiersettlement.CashierSettlementSubmittedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
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
 * 收费员交账写侧领域服务（DM-FIN-003）。
 *
 * <p>不变式：交账头只增不改；提交时按与预览相同口径锁定未交账 payment（经 {@code PaymentReconciliationQueryService}，B.6.4），
 * 挂载 {@code cashier_settlement_id}（全量或 {@code selectedPaymentIds} 子集）；并发挂载由
 * {@link PaymentReconciliation#attachCashierSettlement(Long)} 拒绝。</p>
 */
@Service
@RequiredArgsConstructor
public class CashierSettlementDomainService {

    private final CashierSettlementRepository cashierSettlementRepository;
    private final PaymentReconciliationRepository paymentReconciliationRepository;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 提交交账：insert 头表并批量回写 payment.cashier_settlement_id（BR-CS-007～008）。
     *
     * @param command 交账类型、科室、经办人、业务日窗、勾选 payment 与操作人
     * @return 新建交账头主键
     * @throws BusinessException 参数非法、无可交账流水或并发挂载冲突
     */
    public Long submitCashierSettlement(SubmitCashierSettlementCommand command) {
        // 前置校验：命令必填项与业务日窗合法（BR-CS-001 窗参数）
        this.assertSubmitCommandValid(command);
        PreviewCashierSettlementQuery previewQuery = this.toPreviewQuery(command);
        // 加载候选：与预览共用 Payment 读服务口径，防止提交与预览不一致（B.6.4）
        List<PaymentReconciliation> candidates =
            this.paymentReconciliationQueryService.findUnsettledPaymentsForCashierSettlement(previewQuery);
        // 无可交账流水则拒绝提交，避免空批次（BR-CS-007）
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_EMPTY_CANDIDATES, "当前条件下没有未交账流水");
        }
        // Pre：按勾选子集过滤；null 回落全量候选（兼容存量应用层）
        List<PaymentReconciliation> toMount = this.resolveMountCandidates(candidates, command.getSelectedPaymentIds());
        // 勾选后仍为空则拒绝（显式传空列表或过滤后无行）
        if (toMount.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_EMPTY_CANDIDATES, "当前条件下没有未交账流水");
        }
        LocalDateTime settledAt = LocalDateTime.now();
        String busNo = this.resolveBusNo();
        // 组装交账头：新建即 HANDED_OVER 归档态
        CashierSettlement settlement = new CashierSettlement();
        settlement.setBusNo(busNo);
        settlement.setTypeEnum(command.getTypeEnum());
        settlement.setOrgId(command.getOrgId());
        settlement.setLocationId(command.getLocationId());
        settlement.setCashierId(command.getCashierId());
        settlement.initHandedOver(settledAt);
        settlement.assertBusNoNotBlank();
        // 持久化：先写交账头，再批量挂载 payment
        CashierSettlement savedSettlement = this.cashierSettlementRepository.save(settlement);
        List<PaymentReconciliation> toUpdate = new ArrayList<>();
        for (PaymentReconciliation payment : toMount) {
            // 并发交账：行级校验仍为未挂载，否则 attach 抛错整单回滚（BR-CS-008）
            payment.attachCashierSettlement(savedSettlement.getId());
            toUpdate.add(payment);
        }
        this.paymentReconciliationRepository.saveAll(toUpdate);
        // 发布事件：须在 save 之后、beforeCommit 同步投递
        CashierSettlementSubmittedEvent event = new CashierSettlementSubmittedEvent(
            savedSettlement.getId(),
            savedSettlement.getBusNo(),
            savedSettlement.getTypeEnum(),
            savedSettlement.getCashierId(),
            savedSettlement.getOrgId(),
            savedSettlement.getSettledAt(),
            toUpdate.size(),
            command.getOperatorId()
        );
        this.domainEventPublisher.publish(event);
        return savedSettlement.getId();
    }

    /**
     * 交账提交命令 → 预览筛选 Query（类型、经办人、录入人、业务日窗）。
     *
     * @param command 交账命令
     * @return 预览读侧入参
     */
    private PreviewCashierSettlementQuery toPreviewQuery(SubmitCashierSettlementCommand command) {
        PreviewCashierSettlementQuery query = new PreviewCashierSettlementQuery();
        query.setTypeEnum(command.getTypeEnum());
        query.setCashierId(command.getCashierId());
        query.setEntererMatchIds(command.getEntererMatchIds());
        query.setDateFrom(command.getDateFrom());
        query.setDateTo(command.getDateTo());
        return query;
    }

    /**
     * 解析本次实际挂载的 payment 实体列表：{@code selectedPaymentIds} 为 null 时回落全量候选。
     *
     * @param candidates          锁定后的未交账候选
     * @param selectedPaymentIds  勾选主键；null 表示整批；非空须 ⊆ 候选
     * @return 待挂载实体（保序：按勾选顺序，去重）
     * @throws BusinessException 空勾选或含非候选 ID 时 {@link ErrorCodeConstants#CASHIER_SETTLEMENT_INVALID_ARGUMENT}
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
     * 交账提交命令 Gate：科室、经办人、类型与业务日窗非空且止≥起。
     *
     * @param command 交账命令
     * @throws BusinessException {@link ErrorCodeConstants#CASHIER_SETTLEMENT_INVALID_ARGUMENT}
     */
    private void assertSubmitCommandValid(SubmitCashierSettlementCommand command) {
        // 命令体不能为空，否则无法构造筛选窗
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账命令不能为空");
        }
        // 科室为交账归档维度，应用层 Gate 亦须校验
        if (command.getOrgId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账科室不能为空");
        }
        if (command.getCashierId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账经办人不能为空");
        }
        if (command.getTypeEnum() == null || command.getTypeEnum().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账类型不能为空");
        }
        if (command.getDateFrom() == null || command.getDateTo() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账业务日范围不能为空");
        }
        // 业务日止早于起日则无法构成有效 bill_date 窗
        if (command.getDateTo().isBefore(command.getDateFrom())) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账业务日范围不合法");
        }
    }

    /**
     * 生成租户内唯一的交账单 busNo（日序 + 冲突重试，最多 10 次）。
     *
     * @return 新单号
     * @throws BusinessException {@link ErrorCodeConstants#CASHIER_SETTLEMENT_BUS_NO_CONFLICT} 重试耗尽
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
