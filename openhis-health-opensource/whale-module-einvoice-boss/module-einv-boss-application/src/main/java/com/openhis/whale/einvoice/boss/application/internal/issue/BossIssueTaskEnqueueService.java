/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.einvoice.boss.application.internal.document.EinvoiceBossDocumentInitializerService;
import com.openhis.whale.einvoice.boss.domain.enums.AmountStatus;
import com.openhis.whale.einvoice.boss.domain.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 开票任务入队：初始�?document/line 并创�?PENDING task�?
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BossIssueTaskEnqueueService {

    private final EinvoiceBossDocumentInitializerService documentInitializer;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossIssueTaskRepository taskRepository;

    /**
     * 为指定票据创�?boss 单据并入队任务�?
     *
     * @param invoiceId 票据主键
     * @param triggerSource 触发来源�?
     * @return 新建任务主键；唯一约束冲突（已有活跃任务）时为�?
     */
    public Optional<Long> enqueue(Long invoiceId, String triggerSource) {
        EinvBossDocument document = resolveDocument(invoiceId);
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setTenantId(document.getTenantId());
        task.setInvoiceId(invoiceId);
        task.setDocumentId(document.getId());
        task.setStatus(TaskStatus.PENDING.getCode());
        task.setAttemptCount(0);
        task.setTriggerSource(triggerSource);
        task.setIsDeleted(false);
        try {
            taskRepository.saveAndFlush(task);
            return Optional.ofNullable(task.getId());
        } catch (DataIntegrityViolationException ex) {
            // 蓝绿/重复事件：uk_einv_task_invoice_active 保证仅一条活跃任�?
            log.debug("Issue task already enqueued, invoiceId={}", invoiceId);
            return Optional.empty();
        }
    }

    /**
     * 解析或初始化�?invoiceId 关联�?boss 单据�?
     *
     * <p>若已有单据且金额已调整（{@code amountStatus=ADJUSTED}），则刷新明细；
     * 否则走完�?initialize�?/p>
     *
     * @param invoiceId 票据主键
     * @return 可用于创�?task �?document
     */
    private EinvBossDocument resolveDocument(Long invoiceId) {
        Optional<EinvBossDocument> existing = documentRepository.findFirstByInvoiceIdAndIsDeletedFalseOrderByIdAsc(invoiceId);
        if (existing.isPresent() && AmountStatus.ADJUSTED.getCode().equals(existing.get().getAmountStatus())) {
            return documentInitializer.refreshForIssue(invoiceId);
        }
        return documentInitializer.initialize(invoiceId);
    }
}
