/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.service;

import com.openhis.health.domain.financial.paymentreconciliation.command.*;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentRecDetail;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentRecStatic;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentYbSettlement;
import com.openhis.health.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentRecDetailRepository;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentRecStaticRepository;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentReconciliationRepository;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentYbSettlementRepository;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.share.events.financial.paymentreconciliation.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付流水聚合写侧领域服务。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>承接预结算草稿、支付完成、退费受理/完成、作废等写操作；</li>
 *   <li>维护 {@link PaymentReconciliation} 与 {@link PaymentRecDetail} 主从一致性；</li>
 *   <li>在主从落库成功后发布对应领域事件，供读模型与下游流程消费。</li>
 * </ul>
 *
 * <p>不负责内容：应用层参数门禁、跨聚合流程编排、展示口径组装；仅注入本聚合仓储。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationDomainService {
    private final PaymentReconciliationRepository paymentRepository;
    private final PaymentRecDetailRepository detailRepository;
    private final PaymentRecStaticRepository recStaticRepository;
    private final PaymentYbSettlementRepository ybSettlementRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 支付流水号：沿用展示前缀 PMR-，末段为 {@link AggregateTypeConstants#PAYMENT_RECONCILIATION} 序号去前缀后的 16 位数字。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 根据预结算结果创建支付草稿主记录与明细记录。
     *
     * <p>该方法用于把“待收款事实”固化为可结算的支付草稿：
     * 主记录初始化为草稿状态（命令显式传状态时优先采用），并替换式写入明细列表。</p>
     *
     * @param cmd 预结算创建草稿命令，包含就诊、患者、金额、支付类型及明细等信息
     * @return 新建支付草稿主键
     */
    public Long createPaymentDraftFromPreSettle(CreatePaymentDraftFromPreSettleCommand cmd) {
        PaymentReconciliation payment = new PaymentReconciliation();
        payment.setEncounterId(cmd.getEncounterId());
        payment.setPatientId(cmd.getPatientId());
        payment.setChargeItemIds(PaymentReconciliation.coerceChargeItemIds(cmd.getChargeItemIds()));
        payment.setKindEnum(cmd.getKindEnum());
        payment.setPaymentEnum(cmd.getPaymentEnum());
        payment.setEntererId(cmd.getEntererId());
        payment.setTenderedAmount(cmd.getTenderedAmount());
        payment.setDisplayAmount(cmd.getDisplayAmount());
        payment.setContractNo(cmd.getContractNo());
        payment.setPaymentNo(this.nextPaymentNo());
        payment.initDraft();
        // 命令显式传入状态时优先采用；未传则保持 initDraft 设定的 DRAFT。
        if (cmd.getStatusEnum() != null) {
            payment.setStatusEnum(cmd.getStatusEnum());
        }
        payment.assertHeaderCompleteBeforeNewPersist();
        payment.assertChargeItemsNotEmpty();
        payment.assertAmountNonNegative();
        PaymentReconciliation saved = paymentRepository.save(payment);
        this.replaceDetails(saved.getId(), cmd.getPaymentDetails());
        // Event：saveAll 完成后发布草稿创建事件，供下游读模型与流程消费
        domainEventPublisher.publish(new PaymentDraftCreatedEvent(
            saved.getId(), saved.getEncounterId(), saved.getPatientId(),
            saved.getChargeItemIds(), saved.getDisplayAmount()));
        return saved.getId();
    }

    /**
     * 医保预结算完成后刷新支付草稿展示金额与占位明细（混合结算）。
     *
     * @param paymentId     支付草稿主键
     * @param displayAmount 患者应付金额（Σ 医保 psn_cash_pay + 自费原价）
     * @param details       按账户汇总后的占位明细
     */
    public void refreshPaymentDraftPreSettleAmounts(
        Long paymentId,
        BigDecimal displayAmount,
        List<PaymentDetailItemCommand> details) {
        PaymentReconciliation payment = this.mustGetPayment(paymentId);
        payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        payment.setDisplayAmount(displayAmount);
        payment.setTenderedAmount(displayAmount);
        paymentRepository.save(payment);
        if (details != null && !details.isEmpty()) {
            this.replaceDetails(paymentId, details);
        }
    }

    /**
     * 草稿态支付流水 contract_no 为空时补写费用性质合同业务号（日结/报表读侧口径）。
     *
     * @param paymentId  支付草稿主键
     * @param contractNo 合同业务号（fin_contract.bus_no）；空则跳过
     */
    public void backfillPaymentContractNoIfBlank(Long paymentId, String contractNo) {
        if (StringUtils.isEmpty(contractNo)) {
            return;
        }
        PaymentReconciliation payment = this.mustGetPayment(paymentId);
        payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        if (StringUtils.isNotEmpty(payment.getContractNo())) {
            return;
        }
        payment.setContractNo(contractNo.trim());
        paymentRepository.save(payment);
    }

    /**
     * 完成门诊支付结算，将草稿推进为成功态并回写结算结果。
     *
     * @param cmd 门诊支付结算命令，包含支付主键、结算结果、医保结算号及渠道回执明细
     * @return 支付主键
     */
    public Long settleOutpatientPayment(SettleOutpatientPaymentCommand cmd) {
        // Pre：按主键加载支付主记录，不存在则抛出业务异常
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        // Gate：头字段 payment_enum 须为 PaymentType；历史误写渠道码时收口为 PAY
        this.normalizeForwardPaymentEnum(payment);
        payment.setStatusEnum(PaymentStatus.SUCCESS);
        payment.setOutcomeEnum(cmd.getOutcomeEnum());
        payment.setBillDate(cmd.getBillDate());
        // 2207 权威现金回写头表，避免仍锁在预结算草稿 display/tendered
        if (cmd.getDisplayAmount() != null) {
            payment.setDisplayAmount(cmd.getDisplayAmount());
        }
        if (cmd.getTenderedAmount() != null) {
            payment.setTenderedAmount(cmd.getTenderedAmount());
        }
        paymentRepository.save(payment);
        List<PaymentYbSettlementItemCommand> ybItems = this.persistYbSettlements(payment.getId(), cmd.getYbSettlements());
        if (cmd.getPayReceiptDetails() != null) {
            this.replaceDetails(payment.getId(), cmd.getPayReceiptDetails());
        }
        payment = this.mustGetPayment(cmd.getPaymentId());
        OutpatientPaymentSettledEvent settledEvent = new OutpatientPaymentSettledEvent(
            payment.getId(), payment.getEncounterId(), payment.getChargeItemIds(),
            payment.getDisplayAmount(), payment.getBillDate(), payment.getEntererId());
        settledEvent.setYbSettlements(PaymentYbSettlementSupport.toEventItems(ybItems));
        domainEventPublisher.publish(settledEvent);
        return payment.getId();
    }

    /**
     * 完成住院支付结算，将草稿推进为成功态并输出住院结算事件。
     *
     * @param cmd 住院支付结算命令，包含支付主键、结算结果与医保结算号
     * @return 支付主键
     */
    public Long settleInpatientPayment(SettleInpatientPaymentCommand cmd) {
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        // Gate：头字段 payment_enum 须为 PaymentType；历史误写渠道码时收口为 PAY
        this.normalizeForwardPaymentEnum(payment);
        payment.setStatusEnum(PaymentStatus.SUCCESS);
        payment.setOutcomeEnum(cmd.getOutcomeEnum());
        payment.setBillDate(cmd.getBillDate());
        paymentRepository.save(payment);
        List<PaymentYbSettlementItemCommand> ybItems = this.persistYbSettlements(payment.getId(), cmd.getYbSettlements());
        // 医保正式结算：覆盖为统筹/个账/现金明细（对齐门诊 payReceiptDetails）
        if (cmd.getPayReceiptDetails() != null) {
            this.replaceDetails(payment.getId(), cmd.getPayReceiptDetails());
        }
        payment = this.mustGetPayment(cmd.getPaymentId());
        InpatientPaymentSettledEvent event = new InpatientPaymentSettledEvent(
            payment.getId(), payment.getEncounterId(), payment.getChargeItemIds(), payment.getDisplayAmount());
        event.setEntererId(payment.getEntererId());
        event.setBillDate(payment.getBillDate());
        event.setYbSettlements(PaymentYbSettlementSupport.toEventItems(ybItems));
        domainEventPublisher.publish(event);
        return payment.getId();
    }

    /**
     * 基于原支付单创建门诊退款单（逆向支付单）。
     *
     * <p>BR-PMR-008 / BR-REF-008：对原单加悲观写锁后再判重；落库依赖
     * {@code uk_fin_payment_rec_active_unpay_relation} 部分唯一索引兜底并发双写，
     * 避免收费日结对同一原单重复冲减西药等费用性质。</p>
     *
     * @param cmd 门诊退款申请命令，包含原单关联、退费金额、支付方向及明细信息
     * @return 新建退款支付单主键
     */
    public Long requestOutpatientRefund(RequestOutpatientRefundCommand cmd) {
        // 前置校验：原单主键必填，否则无法加锁与追溯
        if (cmd.getRelationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "退款须关联原支付单");
        }
        // 加载/组装聚合：锁原单，串行化同一原支付的并发退费申请
        this.lockOriginalPaymentForRefund(cmd.getRelationId());
        // 前置校验：已有进行中或已完成逆向单则拒绝，防止日结重复取负
        if (this.existsActiveRefundByRelationId(cmd.getRelationId())) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "原单已存在退款单");
        }
        PaymentReconciliation payment = new PaymentReconciliation();
        payment.setRelationId(cmd.getRelationId());
        payment.setEncounterId(cmd.getEncounterId());
        payment.setPatientId(cmd.getPatientId());
        payment.setEntererId(cmd.getEntererId());
        payment.setTenderedAmount(cmd.getTenderedAmount());
        payment.setDisplayAmount(cmd.getDisplayAmount());
        payment.setChargeItemIds(PaymentReconciliation.coerceChargeItemIds(cmd.getChargeItemIds()));
        payment.setKindEnum(cmd.getKindEnum());
        payment.setPaymentEnum(cmd.getPaymentEnum());
        payment.setContractNo(cmd.getContractNo());
        payment.setPaymentNo(this.nextPaymentNo());
        payment.initDraft();
        // 逆向单进入退款受理中；收费原单保持 SUCCESS 不变
        payment.setStatusEnum(PaymentStatus.REFUND_PROCESSING);
        payment.assertHeaderCompleteBeforeNewPersist();
        payment.assertAmountNonNegative();
        PaymentReconciliation saved;
        try {
            // 持久化：flush 以触发部分唯一索引；并发后到请求转为业务冲突
            saved = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException integrityViolationException) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "原单已存在退款单");
        }
        this.replaceDetails(saved.getId(), cmd.getPaymentDetails());
        // Event：持久化完成后发布门诊退款申请事件
        domainEventPublisher.publish(new OutpatientRefundRequestedEvent(
            saved.getId(), saved.getRelationId(), saved.getEncounterId(), saved.getDisplayAmount()));
        return saved.getId();
    }

    /**
     * 完成门诊退款，将退款处理中支付单推进为已退款。
     *
     * @param cmd 门诊退款完成命令，包含支付主键、结算结果、退款完成时间等信息
     */
    public void completeOutpatientRefund(CompleteOutpatientRefundCommand cmd) {
        // Pre：按主键加载退款单，不存在则抛出业务异常
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.REFUND_PROCESSING);
        payment.setStatusEnum(PaymentStatus.REFUNDED);
        payment.setOutcomeEnum(cmd.getOutcomeEnum());
        this.persistYbSettlements(payment.getId(), cmd.getYbSettlements());
        payment.setBillDate(cmd.getBillDate());
        paymentRepository.save(payment);
        // Asm：装配门诊退款完成事件，回填所有展示与审计字段
        OutpatientRefundCompletedEvent event = new OutpatientRefundCompletedEvent(
            payment.getId(), payment.getRelationId(), payment.getEncounterId(),
            payment.getDisplayAmount(), payment.getStatusEnum());
        event.setEntererId(payment.getEntererId());
        event.setRefundTime(payment.getBillDate());
        // Event：持久化完成后发布门诊退款完成事件
        domainEventPublisher.publish(event);
    }

    /**
     * 门诊账单退费失败时回滚逆向单：清空 {@code relation_id} 并置 {@link PaymentStatus#CANCELLED}，允许原单再次发起退费。
     *
     * @param reversePaymentId 逆向支付主键
     */
    public void abortOutpatientRefundAttempt(Long reversePaymentId) {
        if (reversePaymentId == null) {
            return;
        }
        PaymentReconciliation reverse = this.mustGetPayment(reversePaymentId);
        if (!PaymentStatus.REFUND_PROCESSING.equals(reverse.getStatusEnum())) {
            // 已完成或已取消的逆向单无需回滚
            return;
        }
        reverse.setStatusEnum(PaymentStatus.CANCELLED);
        reverse.setRelationId(null);
        paymentRepository.save(reverse);
    }

    /**
     * 退号联动退费完成：仅收口逆向单为 {@link PaymentStatus#REFUNDED} 并发布退费完成事件（IF-REG-10）。
     *
     * <p>收费原单保持 {@link PaymentStatus#SUCCESS}；与门诊账单退费共用 {@link #completeOutpatientRefund} 逆向收口。</p>
     *
     * @param cmd 退号退费完成命令
     */
    public void completeRegistrationCancelRefund(CompleteRegistrationCancelRefundCommand cmd) {
        if (cmd == null || cmd.getReversePaymentId() == null) {
            throw new IllegalArgumentException("registration cancel refund requires reversePaymentId");
        }
        CompleteOutpatientRefundCommand completeCommand = new CompleteOutpatientRefundCommand();
        completeCommand.setPaymentId(cmd.getReversePaymentId());
        completeCommand.setOutcomeEnum(cmd.getOutcomeEnum());
        completeCommand.setYbSettlements(cmd.getYbSettlements());
        completeCommand.setBillDate(cmd.getBillDate());
        this.completeOutpatientRefund(completeCommand);
    }

    /**
     * 对原支付加悲观写锁，保证随后的有效逆向单判重与插入在同一 UoW 内串行。
     *
     * @param originalPaymentId 原支付主键（{@code relation_id}）
     * @throws BusinessException 原单不存在或已软删
     */
    private void lockOriginalPaymentForRefund(Long originalPaymentId) {
        PaymentReconciliation original = paymentRepository.findWithLockById(originalPaymentId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_NOT_FOUND, "原支付单不存在"));
        // 已软删原单不得再发起逆向，避免挂到幽灵 relation_id
        if (Boolean.TRUE.equals(original.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_NOT_FOUND, "原支付单不存在");
        }
    }

    /**
     * 判断原支付是否已有未删除的有效逆向退费（{@code REFUND_PROCESSING} 或 {@code REFUNDED}）。
     *
     * <p>abort 会清空 {@code relation_id} 并置 {@code CANCELLED}，故不占有效坑；软删行忽略。</p>
     *
     * @param relationId 原支付主键
     * @return 存在有效逆向单时 {@code true}
     */
    private boolean existsActiveRefundByRelationId(Long relationId) {
        if (relationId == null) {
            return false;
        }
        List<PaymentReconciliation> payments = paymentRepository.findByRelationId(relationId);
        for (PaymentReconciliation payment : payments) {
            if (payment == null || Boolean.TRUE.equals(payment.getIsDeleted())) {
                continue;
            }
            if (!relationId.equals(payment.getRelationId())) {
                continue;
            }
            // 仅进行中/已完成逆向单算有效；CANCELLED 等终态不拦截重试
            if (PaymentStatus.REFUND_PROCESSING.equals(payment.getStatusEnum())
                || PaymentStatus.REFUNDED.equals(payment.getStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取消住院结算并生成逆向退款支付单。
     *
     * <p>与门诊 {@link #requestOutpatientRefund} 同构：锁原单 + 有效逆向判重 + 唯一索引兜底（BR-PMR-008）。</p>
     *
     * @param cmd 住院取消结算命令，包含原单关联、退款金额、支付方向与明细
     * @return 新建逆向支付单主键
     */
    public Long cancelInpatientSettleAsRefund(CancelInpatientSettleAsRefundCommand cmd) {
        // 前置校验：原单主键必填
        if (cmd.getRelationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "退款须关联原支付单");
        }
        // 加载/组装聚合：锁原单后判重，避免住院反结并发双写 UN_PAY
        this.lockOriginalPaymentForRefund(cmd.getRelationId());
        if (this.existsActiveRefundByRelationId(cmd.getRelationId())) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "原单已存在退款单");
        }
        PaymentReconciliation payment = new PaymentReconciliation();
        payment.setRelationId(cmd.getRelationId());
        payment.setEncounterId(cmd.getEncounterId());
        payment.setPatientId(cmd.getPatientId());
        payment.setEntererId(cmd.getEntererId());
        payment.setTenderedAmount(cmd.getTenderedAmount());
        payment.setDisplayAmount(cmd.getDisplayAmount());
        payment.setChargeItemIds(PaymentReconciliation.coerceChargeItemIds(cmd.getChargeItemIds()));
        payment.setKindEnum(cmd.getKindEnum());
        payment.setPaymentEnum(cmd.getPaymentEnum());
        payment.setContractNo(cmd.getContractNo());
        payment.setPaymentNo(this.nextPaymentNo());
        payment.initDraft();
        payment.setStatusEnum(PaymentStatus.REFUND_PROCESSING);
        payment.assertHeaderCompleteBeforeNewPersist();
        payment.assertAmountNonNegative();
        PaymentReconciliation saved;
        try {
            // 持久化：flush 触发部分唯一索引，将并发冲突转为业务异常
            saved = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException integrityViolationException) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "原单已存在退款单");
        }
        this.replaceDetails(saved.getId(), cmd.getPaymentDetails());
        return saved.getId();
    }

    /**
     * 住院取消结算收口：逆向单 {@link PaymentStatus#REFUND_PROCESSING} → {@link PaymentStatus#REFUNDED}；
     * 原正向结算单保持 {@link PaymentStatus#SUCCESS} 与原始 {@code bill_date}（与门诊退费 §6.1.6 对称）。
     *
     * @param cmd 收口命令
     */
    public void completeInpatientSettleCancelRefund(CompleteInpatientSettleCancelRefundCommand cmd) {
        PaymentReconciliation original = this.mustGetPayment(cmd.getOriginalPaymentId());
        PaymentReconciliation reverse = this.mustGetPayment(cmd.getReversePaymentId());
        if (!original.getId().equals(reverse.getRelationId())) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "取消结算逆向单与原支付主单关联不一致");
        }
        if (!PaymentStatus.SUCCESS.equals(original.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "原结算单非成功态，不能收口反结");
        }
        reverse.assertStatusTransitionAllowed(PaymentStatus.REFUND_PROCESSING);
        LocalDateTime billDate = LocalDateTime.now();
        if (cmd.getBillDate() != null) {
            billDate = cmd.getBillDate();
        }
        reverse.setStatusEnum(PaymentStatus.REFUNDED);
        if (cmd.getOutcomeEnum() != null) {
            reverse.setOutcomeEnum(cmd.getOutcomeEnum());
        }
        if (cmd.getYbSettlements() != null && !cmd.getYbSettlements().isEmpty()) {
            this.persistYbSettlements(reverse.getId(), cmd.getYbSettlements());
        }
        reverse.setBillDate(billDate);
        paymentRepository.save(reverse);
        InpatientSettleCancelledEvent event = new InpatientSettleCancelledEvent(
            reverse.getId(), reverse.getRelationId(), reverse.getEncounterId());
        event.setCancelledAmount(reverse.getDisplayAmount());
        event.setEntererId(reverse.getEntererId());
        event.setCancelTime(billDate);
        domainEventPublisher.publish(event);
    }

    /**
     * 按支付主键回写状态、结算号与结果枚举。
     *
     * @param cmd 支付状态回写命令
     */
    public void updatePaymentStatusWithSettleIds(UpdatePaymentStatusWithSettleIdsCommand cmd) {
        // Pre：按主键加载支付主记录
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        // Pre：保存变更前状态，用于事件追溯
        PaymentStatus beforeStatusEnum = payment.getStatusEnum();
        payment.setStatusEnum(cmd.getStatusEnum());
        List<PaymentYbSettlementItemCommand> ybItems = this.persistYbSettlements(payment.getId(), cmd.getYbSettlements());
        payment.setOutcomeEnum(cmd.getOutcomeEnum());
        paymentRepository.save(payment);
        PaymentStatusEditedEvent editedEvent = new PaymentStatusEditedEvent(
            payment.getId(), beforeStatusEnum, payment.getStatusEnum());
        editedEvent.setYbSettlements(PaymentYbSettlementSupport.toEventItems(ybItems));
        domainEventPublisher.publish(editedEvent);
    }

    /**
     * 在支付前放弃预结算，将草稿支付单作废。
     *
     * @param cmd 放弃预结算命令，包含待作废支付单主键
     */
    public void abortPreSettlementBeforePayment(AbortPreSettlementBeforePaymentCommand cmd) {
        // Pre：按主键加载支付主记录
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        payment.setStatusEnum(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);
        // Event：持久化完成后发布预结算放弃事件
        domainEventPublisher.publish(new PreSettlementAbortedEvent(
            payment.getId(), payment.getEncounterId()));
    }

    /**
     * 刷新挂号预结算阶段的支付草稿金额、收费项关联与明细。
     *
     * @param cmd 刷新支付草稿命令
     * @return 支付草稿主键
     */
    public Long refreshRegistrationPaymentDraft(RefreshRegistrationPaymentDraftCommand cmd) {
        // Pre：按主键加载支付草稿
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        payment.setChargeItemIds(PaymentReconciliation.coerceChargeItemIds(cmd.getChargeItemIds()));
        payment.setTenderedAmount(cmd.getTenderedAmount());
        payment.setDisplayAmount(cmd.getDisplayAmount());
        payment.assertHeaderCompleteBeforeNewPersist();
        payment.assertChargeItemsNotEmpty();
        payment.assertAmountNonNegative();
        paymentRepository.save(payment);
        this.replaceDetails(payment.getId(), cmd.getPaymentDetails());
        // Event：持久化完成后发布挂号支付草稿刷新事件
        domainEventPublisher.publish(new RegistrationPaymentDraftRefreshedEvent(
            payment.getId(), payment.getPatientId()));
        return payment.getId();
    }

    /**
     * 关闭单条支付明细结果（更新第三方支付流水号和对账结果）。
     *
     * <p>精确加载目标明细后校验归属，避免全量加载再内存筛选（A.9.1 性能约束：禁止 findAll 后内存过滤）。</p>
     *
     * @param cmd 关闭命令，包含结算单标识、明细标识、支付流水号及结果枚举
     */
    public void closeSinglePaymentResult(CloseSinglePaymentResultCommand cmd) {
        // Pre：按主键加载支付主记录
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        // Pre：精确按明细 ID 加载，避免全量加载所有明细后在内存中定位单条记录（A.9.1 性能约束）
        PaymentRecDetail detail = detailRepository.findById(cmd.getDetailId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PAYMENT_REC_DETAIL_NOT_FOUND, "结算明细不存在"));
        if (!detail.getReconciliationId().equals(payment.getId())) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_REC_DETAIL_NOT_MATCH, "明细不属于该结算单");
        }
        detail.setResultEnum(cmd.getResultEnum());
        detail.setPayTransNo(cmd.getPayTransNo());
        detailRepository.save(detail);
        // Event：持久化完成后发布单条支付结果关闭事件
        domainEventPublisher.publish(new SinglePaymentResultClosedEvent(
            payment.getId(), payment.getStatusEnum()));
    }

    /**
     * 收口支付回执明细，用回执明细替换主单现有明细。
     *
     * @param cmd 回执明细收口命令，包含支付主键与最终回执明细集合
     */
    public void finalizePaymentDetailReceipt(FinalizePaymentDetailReceiptCommand cmd) {
        // Pre：按主键加载支付主记录
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        this.replaceDetails(payment.getId(), cmd.getPayReceiptDetails());
        // Asm：统计回执明细数量，用于事件展示
        int detailCount = 0;
        if (cmd.getPayReceiptDetails() != null) {
            detailCount = cmd.getPayReceiptDetails().size();
        }
        // Asm：装配回执收口完成事件，回填操作人与完成时间
        PaymentDetailReceiptFinalizedEvent event = new PaymentDetailReceiptFinalizedEvent(
            payment.getId(), detailCount);
        event.setEntererId(payment.getEntererId());
        event.setFinalizedTime(LocalDateTime.now());
        // Event：持久化完成后发布回执收口完成事件
        domainEventPublisher.publish(event);
    }

    /**
     * 收取预交金，创建预交金支付草稿并写入明细。
     *
     * <p>落库为 {@link PaymentStatus#DRAFT}；须由应用层再调 {@link #settleInpatientPayment} 收口。</p>
     *
     * @param cmd 预交金收取命令
     * @return 支付主键
     */
    public Long collectAdvanceDeposit(CollectAdvanceDepositCommand cmd) {
        PaymentReconciliation payment = new PaymentReconciliation();
        payment.setEncounterId(cmd.getEncounterId());
        payment.setPatientId(cmd.getPatientId());
        payment.setEntererId(cmd.getEntererId());
        payment.setTenderedAmount(cmd.getTenderedAmount());
        payment.setDisplayAmount(cmd.getDisplayAmount());
        payment.setKindEnum(cmd.getKindEnum());
        payment.setPaymentEnum(cmd.getPaymentEnum());
        payment.setPaymentNo(this.nextPaymentNo());
        payment.setChargeItemIds(GeneralSharedConstants.EMPTY_STRING);
        payment.initDraft();
        payment.assertHeaderCompleteBeforeNewPersist();
        payment.assertAmountNonNegative();
        PaymentReconciliation saved = paymentRepository.save(payment);
        this.replaceDetails(saved.getId(), cmd.getPaymentDetails());
        // Asm：装配预交金收取事件，回填操作人与收取时间
        AdvanceDepositCollectedEvent event = new AdvanceDepositCollectedEvent(
            saved.getId(), saved.getEncounterId(), saved.getPatientId(), saved.getDisplayAmount());
        event.setEntererId(saved.getEntererId());
        event.setCollectionTime(saved.getCreatedAt());
        // Event：持久化完成后发布预交金收取事件
        domainEventPublisher.publish(event);
        return saved.getId();
    }

    /**
     * 退还预交金，创建预交金退款草稿并写入明细。
     *
     * <p>不关联原收款单；落库为 {@link PaymentStatus#DRAFT}；须由应用层再调 {@link #settleInpatientPayment} 收口。</p>
     *
     * @param cmd 预交金退还命令
     * @return 支付主键
     */
    public Long refundAdvanceDeposit(RefundAdvanceDepositCommand cmd) {
        PaymentReconciliation payment = new PaymentReconciliation();
        payment.setEncounterId(cmd.getEncounterId());
        payment.setPatientId(cmd.getPatientId());
        payment.setEntererId(cmd.getEntererId());
        payment.setTenderedAmount(cmd.getTenderedAmount());
        payment.setDisplayAmount(cmd.getDisplayAmount());
        payment.setKindEnum(cmd.getKindEnum());
        payment.setPaymentEnum(cmd.getPaymentEnum());
        payment.setRelationId(null);
        payment.setPaymentNo(this.nextPaymentNo());
        payment.setChargeItemIds(GeneralSharedConstants.EMPTY_STRING);
        payment.initDraft();
        payment.assertHeaderCompleteBeforeNewPersist();
        payment.assertAmountNonNegative();
        PaymentReconciliation saved = paymentRepository.save(payment);
        this.replaceDetails(saved.getId(), cmd.getPaymentDetails());
        // Asm：装配预交金退还事件，回填操作人与退还时间
        AdvanceDepositRefundedEvent event = new AdvanceDepositRefundedEvent(
            saved.getId(), saved.getEncounterId(), saved.getPatientId(), saved.getDisplayAmount());
        event.setEntererId(saved.getEntererId());
        event.setRefundTime(saved.getCreatedAt());
        // Event：持久化完成后发布预交金退还事件
        domainEventPublisher.publish(event);
        return saved.getId();
    }

    /**
     * 以先删后插方式替换支付费用分类统计快照（结算/退费成功后由应用层写入）。
     *
     * @param paymentId 支付主键
     * @param staticItems 分类快照行；空则仅清空
     */
    public void replacePaymentRecStatics(Long paymentId, List<PaymentRecStaticItemCommand> staticItems) {
        if (paymentId == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付主键不能为空");
        }
        this.mustGetPayment(paymentId);
        this.recStaticRepository.deleteByReconciliationId(paymentId);
        if (staticItems == null || staticItems.isEmpty()) {
            return;
        }
        List<PaymentRecStatic> toSave = new ArrayList<>();
        for (PaymentRecStaticItemCommand item : staticItems) {
            if (item == null || StringUtils.isEmpty(item.getTypeCode()) || item.getAmount() == null) {
                continue;
            }
            if (StringUtils.isEmpty(item.getMethodCode())) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    "支付分类快照 method_code 不能为空");
            }
            PaymentRecStatic row = new PaymentRecStatic();
            row.setReconciliationId(paymentId);
            row.setMethodCode(item.getMethodCode().trim());
            row.setTypeCode(item.getTypeCode().trim());
            row.setTypeName(item.getTypeName());
            row.setAmount(item.getAmount());
            row.setChargeItemIds(item.getChargeItemIds());
            toSave.add(row);
        }
        if (!toSave.isEmpty()) {
            this.recStaticRepository.saveAll(toSave);
        }
    }

    /**
     * 按主键强制加载支付主记录，不存在则抛出业务异常。
     *
     * @param paymentId 支付主键
     * @return 支付主记录聚合根
     * @throws BusinessException 当支付记录不存在时抛出
     */
    private PaymentReconciliation mustGetPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_NOT_FOUND, "支付流水不存在"));
    }

    /**
     * 以“先删后插”的方式替换支付明细。
     *
     * <p>适用于草稿更新、回执收口、退款单重建等场景，确保同一支付单明细全集由入参决定。</p>
     *
     * @param paymentId 支付主键
     * @param details 目标明细集合，传空时表示清空明细
     */
    private void replaceDetails(Long paymentId, List<PaymentDetailItemCommand> details) {
        detailRepository.deleteByReconciliationId(paymentId);
        // 入参为空表示当前不保留任何支付明细，执行清空后直接返回。
        if (details == null || details.isEmpty()) {
            return;
        }
        List<PaymentRecDetail> toSave = new ArrayList<>();
        // Loop：遍历命令明细项，逐条装配子实体
        for (PaymentDetailItemCommand cmd : details) {
            PaymentRecDetail detail = new PaymentRecDetail();
            detail.setReconciliationId(paymentId);
            detail.setPredecessorId(cmd.getPredecessorId());
            detail.setTargetEnum(cmd.getTargetEnum());
            detail.setAccountId(cmd.getAccountId());
            detail.setAccountCode(cmd.getAccountCode());
            detail.setPayEnum(cmd.getPayEnum());
            detail.setPayLevelEnum(cmd.getPayLevelEnum());
            detail.setAmount(cmd.getAmount());
            detail.setReturnAmount(cmd.getReturnAmount());
            detail.setChargeAmount(cmd.getChargeAmount());
            detail.setPayTransNo(cmd.getPayTransNo());
            detail.setPayTransText(cmd.getPayTransText());
            // Asm：未传渠道交易时间时回填当前时间，保证落库字段完整
            if (cmd.getPayTransDate() == null) {
                detail.setPayTransDate(LocalDateTime.now());
            } else {
                detail.setPayTransDate(cmd.getPayTransDate());
            }
            detail.setBeforeBalance(cmd.getBeforeBalance());
            detail.setAfterBalance(cmd.getAfterBalance());
            // Asm：未传结果枚举时默认落草稿结果，等待后续渠道回执收口
            if (cmd.getResultEnum() == null) {
                detail.setResultEnum(PaymentStatus.DRAFT.getCode());
            } else {
                detail.setResultEnum(cmd.getResultEnum());
            }
            detail.assertPersistableChildRow();
            toSave.add(detail);
        }
        detailRepository.saveAll(toSave);
    }

    /**
     * 正向结算：头字段 {@code payment_enum} 须为 {@link PaymentType#PAY}。
     *
     * <p>历史草稿可能误写入渠道码（如 {@code CASHIER}）；正式结算时防御收口，避免按 PAY 过滤的读路径漏单。</p>
     *
     * @param payment 待结算支付主单
     */
    private void normalizeForwardPaymentEnum(PaymentReconciliation payment) {
        if (payment == null) {
            return;
        }
        String paymentEnum = payment.getPaymentEnum();
        if (PaymentType.PAY.getCode().equals(paymentEnum)
            || PaymentType.UN_PAY.getCode().equals(paymentEnum)) {
            return;
        }
        payment.setPaymentEnum(PaymentType.PAY.getCode());
    }

    /**
     * 医保结算子表写路径：空输入跳过，否则全量替换并返回原命令列表。
     *
     * @param paymentId 对账主键
     * @param items     医保结算行命令
     * @return 持久化后的命令列表；空输入返回不可变空列表
     */
    private List<PaymentYbSettlementItemCommand> persistYbSettlements(
        Long paymentId,
        List<PaymentYbSettlementItemCommand> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        this.replaceYbSettlements(paymentId, items);
        return items;
    }

    /**
     * 按对账 id 先删后插医保结算子表；跳过 setlId 为空的行。
     *
     * @param paymentId 对账主键
     * @param items     待写入命令
     */
    private void replaceYbSettlements(Long paymentId, List<PaymentYbSettlementItemCommand> items) {
        ybSettlementRepository.deleteByReconciliationId(paymentId);
        if (items == null || items.isEmpty()) {
            return;
        }
        List<PaymentYbSettlement> toSave = new ArrayList<>();
        for (PaymentYbSettlementItemCommand cmd : items) {
            if (cmd == null || StringUtils.isEmpty(cmd.getSetlId())) {
                continue;
            }
            PaymentYbSettlement row = new PaymentYbSettlement();
            row.setReconciliationId(paymentId);
            row.setSetlId(cmd.getSetlId().trim());
            if (StringUtils.isNotEmpty(cmd.getChrgBchno())) {
                row.setChrgBchno(cmd.getChrgBchno().trim());
            }
            if (StringUtils.isNotEmpty(cmd.getMdtrtId())) {
                row.setMdtrtId(cmd.getMdtrtId().trim());
            }
            row.setSettlementRecordId(cmd.getSettlementRecordId());
            row.setAccountId(cmd.getAccountId());
            if (StringUtils.isNotEmpty(cmd.getStatus())) {
                row.setStatus(cmd.getStatus().trim());
            } else {
                row.setStatus(PaymentYbSettlementStatus.CONFIRMED);
            }
            row.assertPersistableChildRow();
            toSave.add(row);
        }
        ybSettlementRepository.saveAll(toSave);
    }

    /**
     * 2208/2305/2601 撤销成功后：将关联 {@code fin_payment_yb_settlement} 标为 {@code REVOKED}（幂等）。
     *
     * <p>优先按 {@code settlementRecordId} 定位；未命中时回落 {@code setlId}。
     * 日结汇总依赖 CONFIRMED 桥表行计入个账/统筹，须与 {@code yb_settlement_record} 同态。</p>
     * <p>独立事务提交：2208/2601 平台成功后调用方外层若回滚，桥表仍保持 REVOKED，避免日结虚增。</p>
     *
     * @param settlementRecordId CHS {@code yb_settlement_record.id}；可空
     * @param setlId             平台结算号；可空（与 settlementRecordId 至少一个有效）
     * @return 实际改为 REVOKED 的行数（已是 REVOKED 的不计入）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markYbSettlementsRevoked(Long settlementRecordId, String setlId) {
        List<PaymentYbSettlement> rows = new ArrayList<>();
        // Pre：优先按结算回执主键定位桥表
        if (settlementRecordId != null) {
            List<PaymentYbSettlement> byRecord =
                this.ybSettlementRepository.findAllBySettlementRecordIdAndIsDeletedFalse(settlementRecordId);
            if (byRecord != null && !byRecord.isEmpty()) {
                rows.addAll(byRecord);
            }
        }
        // Pre：无 settlement_record_id 挂载时回落 setl_id
        if (rows.isEmpty() && StringUtils.isNotEmpty(setlId)) {
            List<PaymentYbSettlement> bySetl =
                this.ybSettlementRepository.findAllBySetlIdAndIsDeletedFalse(setlId.trim());
            if (bySetl != null && !bySetl.isEmpty()) {
                rows.addAll(bySetl);
            }
        }
        if (rows.isEmpty()) {
            return 0;
        }
        int changed = 0;
        LocalDateTime now = LocalDateTime.now();
        // Loop：仅将非 REVOKED 行改为 REVOKED，已撤销幂等跳过
        for (PaymentYbSettlement row : rows) {
            if (row == null) {
                continue;
            }
            if (PaymentYbSettlementStatus.REVOKED.equals(row.getStatus())) {
                continue;
            }
            row.setStatus(PaymentYbSettlementStatus.REVOKED);
            row.setUpdatedAt(now);
            changed++;
        }
        if (changed > 0) {
            this.ybSettlementRepository.saveAll(rows);
        }
        return changed;
    }

    /**
     * 2601 平台冲正成功后：软删该 {@code setl_id} 对应的医保统筹/个账明细（幂等）。
     *
     * <p>仅触碰 {@link PaymentChannel#INSURANCE_FUND}/{@link PaymentChannel#INSURANCE_ACCT}，
     * 不改现金/微信等自付渠道，不影响正常 2208 一收一退（退费走 UN_PAY 对冲）。
     * 日结下半区统筹/个账读 {@code fin_payment_rec_detail}；2601 视同结算未发生，须与平台净额 0 同态，
     * 否则会出现「父行跟明细虚增、子行读不到已软删回执 insutype」的不平。</p>
     * <p>独立事务：与 {@link #markYbSettlementsRevoked} 同口径，外层回滚不回滚本清理。</p>
     *
     * @param setlId 平台结算号（明细 {@code pay_trans_no}）；空则跳过
     * @return 实际软删行数（已删不计入）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int softDeleteInsuranceFundAcctDetailsBySetlId(String setlId) {
        // Gate：无 setl_id 无法定位明细，跳过以免误伤其它支付单
        if (StringUtils.isEmpty(setlId)) {
            return 0;
        }
        String trimmed = setlId.trim();
        List<String> insurancePayEnums = List.of(
            PaymentChannel.INSURANCE_FUND.getCode(),
            PaymentChannel.INSURANCE_ACCT.getCode());
        List<PaymentRecDetail> rows =
            this.detailRepository.findAllByPayTransNoAndPayEnumInAndIsDeletedFalse(trimmed, insurancePayEnums);
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        // Loop：仅软删统筹/个账；已软删底座过滤后仍二次防呆
        for (PaymentRecDetail row : rows) {
            if (row == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            row.setIsDeleted(true);
            row.setDeletedAt(now);
            row.setUpdatedAt(now);
            changed++;
        }
        if (changed > 0) {
            this.detailRepository.saveAll(rows);
        }
        return changed;
    }

    /**
     * 生成支付业务号。
     *
     * <p>展示前缀 {@code PMR-}，末段为 {@link AggregateTypeConstants#PAYMENT_RECONCILIATION} 按日序号去前缀后的数字段（示例 {@code PMR-202506250001}）。</p>
     *
     * @return 业务号（PMR- 前缀 + yyyyMMdd + 4 位当日序号）
     */
    private String nextPaymentNo() {
        String agg = AggregateTypeConstants.PAYMENT_RECONCILIATION;
        String full = businessSequenceService.getSeqByDay(agg, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        // Asm：拼接展示前缀 PMR- 与按日数字段，形成最终业务号
        return "PMR-" + full.substring(agg.length());
    }
}
