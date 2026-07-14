/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.invoice.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.IssueElectronicInvoiceCommand;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.repository.InvoiceRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.domain.share.events.administration.invoice.InvoiceCancelledEvent;
import com.openhis.health.domain.share.events.administration.invoice.InvoiceDraftCreatedEvent;
import com.openhis.health.domain.share.events.administration.invoice.InvoiceIssuedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 票据（{@code adm_invoice}）聚合写侧领域服务。
 *
 * <p>职责：支付后草稿创建、电子票开具、冲红与草稿作废；状态迁移委托 {@link Invoice} 聚合根断言。</p>
 * <p>不变量：DRAFT/ISSUED/CANCELLED 状态机不可逆混用；持久化先于 {@code InvoiceDraftCreatedEvent} 等领域事件。</p>
 * <p>仅注入本聚合 {@link InvoiceRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class InvoiceDomainService {
    private final InvoiceRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 票据 busNo：业务流水按日递增，前缀 {@link AggregateTypeConstants#INVOICE}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 支付完成后创建草稿票据（DRAFT），生成业务流水号并发布草稿创建事件。
     *
     * @param cmd 草稿创建命令（患者、支付单、票据类型、金额等）
     * @return 新建票据主键
     */
    public Long createDraftInvoiceAfterPayment(CreateDraftInvoiceCommand cmd) {
        // 加载/组装：装配草稿行并生成业务流水号
        Invoice row = new Invoice();
        row.setPatientId(cmd.getPatientId());
        row.setPaymentId(cmd.getPaymentId());
        row.setTypeCode(cmd.getTypeCode());
        row.setAmount(cmd.getAmount());
        row.setStatusEnum(InvoiceStatus.DRAFT.getCode());
        row.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.INVOICE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        // 前置校验：草稿行必填与金额口径
        row.assertPersistableDraftRow();
        // 持久化
        row = repository.save(row);
        // 发布事件：草稿已创建
        domainEventPublisher.publish(new InvoiceDraftCreatedEvent(
            row.getId(), row.getPatientId(), row.getPaymentId(), row.getTypeCode(), row.getAmount()));
        return row.getId();
    }

    /**
     * 开具电子票据（DRAFT → ISSUED）。
     *
     * <p>开具后票据状态不可逆，下游可通过 {@link InvoiceIssuedEvent} 感知并推送患者通知。</p>
     *
     * @param cmd 开具命令，含票据主键、电子票据编号、批次码及开具时间
     */
    public void issueElectronicInvoice(IssueElectronicInvoiceCommand cmd) {
        // 加载/组装：加载票据并迁移至 ISSUED
        Invoice row = this.requireById(cmd.getInvoiceId());
        // 开具前无状态前置断言（当前 SDD 未要求，保留现状）；如需门控请在 Invoice 实体添加 assertIssuable()。
        row.setStatusEnum(InvoiceStatus.ISSUED.getCode());
        row.setBillNo(cmd.getBillNo());
        row.setIssuedAt(cmd.getBillCreateTime());
        // 持久化
        repository.save(row);
        // 发布事件：电子票已开具
        InvoiceIssuedEvent event = new InvoiceIssuedEvent(
            row.getId(), row.getPatientId(), row.getReconciliationId(),
            cmd.getBillNo(), cmd.getBillBatchCode(), cmd.getBillCreateTime(), row.getTypeCode());
        event.setEntererId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
    }

    /**
     * 冲红票据并创建负向记录（ISSUED → CANCELLED）。
     *
     * <p>冲红前校验当前状态必须为 ISSUED，防止重复冲红；
     * 冲销逻辑（负向红票创建）由应用服务层或下游域负责。</p>
     *
     * @param cmd 取消命令，含票据主键、取消原因及操作人
     */
    public void writeoffInvoiceAndCreateRedRecord(CancelInvoiceCommand cmd) {
        // 加载/组装：加载已开具票据
        Invoice row = this.requireById(cmd.getInvoiceId());
        // 前置校验：仅 ISSUED 状态允许冲红，委托聚合根执行前置状态断言（P1-N9 修复）。
        row.assertWriteoffable();
        row.markCancelled(cmd.getReason());
        // 持久化
        repository.save(row);
        // 发布事件：票据已冲红
        InvoiceCancelledEvent event = new InvoiceCancelledEvent(
            row.getId(), cmd.getReason(), cmd.getOperatorId(), row.getTypeCode());
        event.setEntererId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
    }

    /**
     * 作废草稿票据（DRAFT → CANCELLED）。
     *
     * <p>作废前校验当前状态必须为 DRAFT；语义与冲红不同，不复用 writeoff 路径。</p>
     *
     * @param cmd 取消命令，含票据主键、取消原因及操作人
     */
    public void voidDraftInvoice(CancelInvoiceCommand cmd) {
        // 加载/组装：加载草稿票据
        Invoice row = this.requireById(cmd.getInvoiceId());
        // 前置校验：仅 DRAFT 状态允许作废，委托聚合根执行前置状态断言（P1-N9 修复：不再复用 writeoff）。
        row.assertVoidable();
        row.markCancelled(cmd.getReason());
        // 持久化
        repository.save(row);
        // 发布事件：草稿已作废
        InvoiceCancelledEvent event = new InvoiceCancelledEvent(
            row.getId(), cmd.getReason(), cmd.getOperatorId(), row.getTypeCode());
        event.setEntererId(cmd.getOperatorId());
        domainEventPublisher.publish(event);
    }

    /**
     * 按主键加载票据，不存在则抛出业务异常。
     *
     * @param id 票据主键
     * @return 票据聚合根
     * @throws BusinessException 票据不存在时
     */
    private Invoice requireById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.INVOICE_NOT_FOUND, "票据不存在"));
    }
}
