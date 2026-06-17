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

package com.openhis.health.domain.financial.paymentreconciliation.service;

import com.openhis.health.domain.financial.paymentreconciliation.command.*;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentRecDetail;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentRecDetailRepository;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentReconciliationRepository;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.events.financial.paymentreconciliation.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>不负责内容：应用层参数门禁、跨聚合流程编排、展示口径组装。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationDomainService {
    private final PaymentReconciliationRepository paymentRepository;
    private final PaymentRecDetailRepository detailRepository;
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
        // 业务分支处理：命令显式传入状态时优先采用；未传则保持 initDraft 设定的 DRAFT
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
     * 完成门诊支付结算，将草稿推进为成功态并回写结算结果。
     *
     * @param cmd 门诊支付结算命令，包含支付主键、结算结果、医保结算号及渠道回执明细
     * @return 支付主键
     */
    public Long settleOutpatientPayment(SettleOutpatientPaymentCommand cmd) {
        // Pre：按主键加载支付主记录，不存在则抛出业务异常
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())
            && !PaymentStatus.SETTLING.equals(payment.getStatusEnum())) {
            payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        }
        payment.setStatusEnum(PaymentStatus.SUCCESS);
        payment.setOutcomeEnum(cmd.getOutcomeEnum());
        payment.setYbSettleIds(cmd.getYbSettleIds());
        payment.setBillDate(cmd.getBillDate());
        paymentRepository.save(payment);
        // 业务分支处理：支付渠道返回明细时覆盖保存；无明细时保留草稿阶段已有明细
        if (cmd.getPayReceiptDetails() != null) {
            this.replaceDetails(payment.getId(), cmd.getPayReceiptDetails());
        }
        // Event：支付流水落库完成后再投递结算事件；就诊状态不属于支付聚合边界，由就诊域在挂号确认路径单独发布
        OutpatientPaymentSettledEvent settledEvent = new OutpatientPaymentSettledEvent(
            payment.getId(), payment.getEncounterId(), payment.getChargeItemIds(),
            payment.getDisplayAmount(), payment.getBillDate(), payment.getEntererId());
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
        // Pre：按主键加载支付主记录，不存在则抛出业务异常
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())
            && !PaymentStatus.SETTLING.equals(payment.getStatusEnum())) {
            payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        }
        payment.setStatusEnum(PaymentStatus.SUCCESS);
        payment.setOutcomeEnum(cmd.getOutcomeEnum());
        payment.setYbSettleIds(cmd.getYbSettleIds());
        payment.setBillDate(cmd.getBillDate());
        paymentRepository.save(payment);
        // Asm：装配住院结算完成事件，回填所有展示字段
        InpatientPaymentSettledEvent event = new InpatientPaymentSettledEvent(
            payment.getId(), payment.getEncounterId(), payment.getChargeItemIds(),
            payment.getDisplayAmount(), payment.getYbSettleIds());
        event.setEntererId(payment.getEntererId());
        event.setBillDate(payment.getBillDate());
        // Event：持久化完成后发布住院结算事件
        domainEventPublisher.publish(event);
        return payment.getId();
    }

    /**
     * 住院医保协同单路径：支付草稿推进为 SETTLING，等待 Worker 完成本地落账。
     *
     * @param cmd 含支付主键与操作员
     */
    public void markInpatientPaymentSettling(MarkInpatientPaymentSettlingCommand cmd) {
        // 前置校验：支付单须存在且允许 DRAFT→SETTLING 迁移
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        payment.setStatusEnum(PaymentStatus.SETTLING);
        // 持久化：锁定支付单，防止并发重复结算
        paymentRepository.save(payment);
    }

    /**
     * 协同单失败时回退支付单 SETTLING → DRAFT，供用户重新预结算。
     *
     * @param cmd 含支付主键
     */
    public void revertInpatientPaymentFromSettling(RevertInpatientPaymentFromSettlingCommand cmd) {
        // 前置校验：支付单须处于 SETTLING 方可回退
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.SETTLING);
        payment.setStatusEnum(PaymentStatus.DRAFT);
        // 持久化：释放结算窗口，允许重新预结算
        paymentRepository.save(payment);
    }

    /**
     * 门诊医保协同单路径：支付草稿推进为 SETTLING，等待 Worker 完成本地落账。
     *
     * @param cmd 含支付主键与操作员
     */
    public void markOutpatientPaymentSettling(MarkOutpatientPaymentSettlingCommand cmd) {
        // 前置校验：支付单须存在且允许 DRAFT→SETTLING 迁移
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.DRAFT);
        payment.setStatusEnum(PaymentStatus.SETTLING);
        // 持久化：锁定支付单，防止并发重复结算
        paymentRepository.save(payment);
    }

    /**
     * 协同单失败时回退门诊支付单 SETTLING → DRAFT。
     *
     * @param cmd 含支付主键
     */
    public void revertOutpatientPaymentFromSettling(RevertOutpatientPaymentFromSettlingCommand cmd) {
        // 前置校验：支付单须处于 SETTLING 方可回退
        PaymentReconciliation payment = this.mustGetPayment(cmd.getPaymentId());
        payment.assertStatusTransitionAllowed(PaymentStatus.SETTLING);
        payment.setStatusEnum(PaymentStatus.DRAFT);
        // 持久化：释放结算窗口，允许重新预结算
        paymentRepository.save(payment);
    }

    /**
     * 将支付单批量推进至“退款处理中”窗口状态。
     *
     * <p>用于在正式生成退款单前锁定可退费主单，避免并发重复退费。</p>
     *
     * @param cmd 退款受理命令，包含待受理的支付单主键列表
     */
    public void enterRefundProcessingWindow(EnterRefundProcessingWindowCommand cmd) {
        // Pre：批量查询待受理退款的支付单
        List<PaymentReconciliation> payments = paymentRepository.findByIdIn(cmd.getPaymentIds());
        if (payments.size() != cmd.getPaymentIds().size()) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_NOT_FOUND, "进入退款受理时存在无效支付流水");
        }
        // Pre：将查询结果按主键建索引，加速后续按 ID 定位
        Map<Long, PaymentReconciliation> paymentMap = new HashMap<>();
        for (PaymentReconciliation payment : payments) {
            paymentMap.put(payment.getId(), payment);
        }
        List<PaymentReconciliation> toSave = new ArrayList<>();
        // Loop：遍历入参主键列表，逐条校验并推进状态
        for (Long id : cmd.getPaymentIds()) {
            PaymentReconciliation payment = paymentMap.get(id);
            payment.assertRefundRequestable();
            payment.setStatusEnum(PaymentStatus.REFUND_PROCESSING);
            toSave.add(payment);
        }
        paymentRepository.saveAll(toSave);
        // Asm：取首条就诊 ID 作为事件上下文
        Long firstEncounterId = null;
        if (!toSave.isEmpty()) {
            firstEncounterId = toSave.get(0).getEncounterId();
        }
        // Event：持久化完成后发布退款受理窗口进入事件
        domainEventPublisher.publish(new RefundProcessingWindowEnteredEvent(
            cmd.getPaymentIds().get(0), cmd.getPaymentIds(), firstEncounterId));
    }

    /**
     * 基于原支付单创建门诊退款单（逆向支付单）。
     *
     * @param cmd 门诊退款申请命令，包含原单关联、退费金额、支付方向及明细信息
     * @return 新建退款支付单主键
     */
    public Long requestOutpatientRefund(RequestOutpatientRefundCommand cmd) {
        if (paymentRepository.existsByRelationId(cmd.getRelationId())) {
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
        payment.setPaymentNo(this.nextPaymentNo());
        payment.initDraft();
        payment.setStatusEnum(PaymentStatus.REFUND_PROCESSING);
        payment.assertHeaderCompleteBeforeNewPersist();
        payment.assertAmountNonNegative();
        PaymentReconciliation saved = paymentRepository.save(payment);
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
        payment.setYbSettleIds(cmd.getYbSettleIds());
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
     * 门诊账单退费执行：将<strong>原支付主单</strong>从退款处理中收口为已退款（静默写库，不发退费完成事件）。
     *
     * <p>产品半退场景下资金侧对该 payment 整单冲正后，原单终态为 {@link PaymentStatus#REFUNDED}。
     * 须在逆向退款单 {@link #completeOutpatientRefund} 之前或与之同编排调用。
     * 日结/对外退费完成事件<strong>仅</strong>由 {@link #completeOutpatientRefund} 在逆向单上发布一次。</p>
     *
     * <p><strong>退号（挂号 IF-REG-10）</strong>不得调用本方法，应使用
     * {@link #completeRegistrationCancelRefund}。</p>
     *
     * @param cmd 原单收口命令
     */
    public void finalizeOriginalOutpatientPayment(FinalizeOriginalOutpatientPaymentCommand cmd) {
        PaymentReconciliation original = this.mustGetPayment(cmd.getOriginalPaymentId());
        original.assertStatusTransitionAllowed(PaymentStatus.REFUND_PROCESSING);
        original.setStatusEnum(PaymentStatus.REFUNDED);
        if (cmd.getOutcomeEnum() != null) {
            original.setOutcomeEnum(cmd.getOutcomeEnum());
        }
        if (cmd.getYbSettleIds() != null) {
            original.setYbSettleIds(cmd.getYbSettleIds());
        }
        LocalDateTime billDate = LocalDateTime.now();
        if (cmd.getBillDate() != null) {
            billDate = cmd.getBillDate();
        }
        original.setBillDate(billDate);
        paymentRepository.save(original);
    }

    /**
     * 退号联动退费完成：原单与逆向单一并收口为已退款，并发布唯一退费完成事件（IF-REG-10）。
     *
     * <p>挂号退号仅为<strong>整单全退</strong>，不存在产品「半退」、保留链再生或
     * {@link #finalizeOriginalOutpatientPayment} 组合；与门诊账单退费（AS-OP-FIN-003）编排隔离。</p>
     *
     * @param cmd 退号退费完成命令
     */
    public void completeRegistrationCancelRefund(CompleteRegistrationCancelRefundCommand cmd) {
        PaymentReconciliation original = this.mustGetPayment(cmd.getOriginalPaymentId());
        PaymentReconciliation reverse = this.mustGetPayment(cmd.getReversePaymentId());
        if (!original.getId().equals(reverse.getRelationId())) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "退号退费逆向单与原支付主单关联不一致");
        }
        original.assertStatusTransitionAllowed(PaymentStatus.REFUND_PROCESSING);
        reverse.assertStatusTransitionAllowed(PaymentStatus.REFUND_PROCESSING);
        LocalDateTime billDate = LocalDateTime.now();
        if (cmd.getBillDate() != null) {
            billDate = cmd.getBillDate();
        }
        original.setStatusEnum(PaymentStatus.REFUNDED);
        reverse.setStatusEnum(PaymentStatus.REFUNDED);
        if (cmd.getOutcomeEnum() != null) {
            original.setOutcomeEnum(cmd.getOutcomeEnum());
            reverse.setOutcomeEnum(cmd.getOutcomeEnum());
        }
        if (cmd.getYbSettleIds() != null) {
            original.setYbSettleIds(cmd.getYbSettleIds());
            reverse.setYbSettleIds(cmd.getYbSettleIds());
        }
        original.setBillDate(billDate);
        reverse.setBillDate(billDate);
        List<PaymentReconciliation> toSave = new ArrayList<>();
        toSave.add(original);
        toSave.add(reverse);
        paymentRepository.saveAll(toSave);
        OutpatientRefundCompletedEvent event = new OutpatientRefundCompletedEvent(
            reverse.getId(), original.getId(), reverse.getEncounterId(),
            reverse.getDisplayAmount(), PaymentStatus.REFUNDED);
        event.setEntererId(reverse.getEntererId());
        event.setRefundTime(billDate);
        domainEventPublisher.publish(event);
    }

    /**
     * 取消住院结算并生成逆向退款支付单。
     *
     * @param cmd 住院取消结算命令，包含原单关联、退款金额、支付方向与明细
     * @return 新建逆向支付单主键
     */
    public Long cancelInpatientSettleAsRefund(CancelInpatientSettleAsRefundCommand cmd) {
        if (paymentRepository.existsByRelationId(cmd.getRelationId())) {
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
        payment.setPaymentNo(this.nextPaymentNo());
        payment.initDraft();
        payment.setStatusEnum(PaymentStatus.REFUND_PROCESSING);
        payment.assertHeaderCompleteBeforeNewPersist();
        payment.assertAmountNonNegative();
        PaymentReconciliation saved = paymentRepository.save(payment);
        this.replaceDetails(saved.getId(), cmd.getPaymentDetails());
        return saved.getId();
    }

    /**
     * 住院取消结算收口：原单 SUCCESS 与逆向单 REFUND_PROCESSING 一并推进为 REFUNDED，并发布取消事件。
     *
     * @param cmd 收口命令
     */
    public void completeInpatientSettleCancelRefund(CompleteInpatientSettleCancelRefundCommand cmd) {
        PaymentReconciliation original = this.mustGetPayment(cmd.getOriginalPaymentId());
        PaymentReconciliation reverse = this.mustGetPayment(cmd.getReversePaymentId());
        if (!original.getId().equals(reverse.getRelationId())) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "取消结算逆向单与原支付主单关联不一致");
        }
        original.assertStatusTransitionAllowed(PaymentStatus.SUCCESS);
        reverse.assertStatusTransitionAllowed(PaymentStatus.REFUND_PROCESSING);
        LocalDateTime billDate = LocalDateTime.now();
        if (cmd.getBillDate() != null) {
            billDate = cmd.getBillDate();
        }
        original.setStatusEnum(PaymentStatus.REFUNDED);
        reverse.setStatusEnum(PaymentStatus.REFUNDED);
        if (cmd.getOutcomeEnum() != null) {
            original.setOutcomeEnum(cmd.getOutcomeEnum());
            reverse.setOutcomeEnum(cmd.getOutcomeEnum());
        }
        if (cmd.getYbSettleIds() != null) {
            original.setYbSettleIds(cmd.getYbSettleIds());
            reverse.setYbSettleIds(cmd.getYbSettleIds());
        }
        original.setBillDate(billDate);
        reverse.setBillDate(billDate);
        List<PaymentReconciliation> toSave = new ArrayList<>();
        toSave.add(original);
        toSave.add(reverse);
        paymentRepository.saveAll(toSave);
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
        payment.setYbSettleIds(cmd.getYbSettleIds());
        payment.setOutcomeEnum(cmd.getOutcomeEnum());
        paymentRepository.save(payment);
        // Event：持久化完成后发布状态编辑事件，携带变更前后状态供审计
        domainEventPublisher.publish(new PaymentStatusEditedEvent(
            payment.getId(), beforeStatusEnum, payment.getStatusEnum(), payment.getYbSettleIds()));
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
        // 业务分支处理：入参为空表示当前不保留任何支付明细，执行清空后直接返回
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
     * 生成支付业务号。
     *
     * <p>展示前缀 PMR-，末段为 {@link AggregateTypeConstants#PAYMENT_RECONCILIATION} 框架序号去前缀后的数字段。</p>
     *
     * @return 业务号（PMR- 前缀 + 16 位数字）
     */
    private String nextPaymentNo() {
        String agg = AggregateTypeConstants.PAYMENT_RECONCILIATION;
        String full = businessSequenceService.getSeq(agg, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        // Asm：拼接展示前缀 PMR- 与数字段，形成最终业务号
        return "PMR-" + full.substring(agg.length());
    }
}
