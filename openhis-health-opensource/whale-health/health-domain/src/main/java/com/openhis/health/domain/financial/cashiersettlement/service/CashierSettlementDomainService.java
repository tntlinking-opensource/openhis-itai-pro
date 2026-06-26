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
import java.util.List;

/**
 * 收费员交账写侧领域服务（DM-FIN-003）。
 *
 * <p>不变式：交账头只增不改；提交时按与预览相同口径锁定未交账 payment（经 {@code PaymentReconciliationQueryService}，B.6.4），
 * 整批挂载 {@code cashier_settlement_id}；并发挂载由 {@link PaymentReconciliation#attachCashierSettlement(Long)} 拒绝。</p>
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
     * @param command 交账类型、科室、经办人、业务日窗与操作人
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
        for (PaymentReconciliation payment : candidates) {
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

    private PreviewCashierSettlementQuery toPreviewQuery(SubmitCashierSettlementCommand command) {
        PreviewCashierSettlementQuery query = new PreviewCashierSettlementQuery();
        query.setTypeEnum(command.getTypeEnum());
        query.setCashierId(command.getCashierId());
        query.setEntererMatchIds(command.getEntererMatchIds());
        query.setDateFrom(command.getDateFrom());
        query.setDateTo(command.getDateTo());
        return query;
    }

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
