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
 * 开票任务入队 AppService：初始化 document/line 并创建 task。
 * <p>由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 管理 UoW。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BossIssueTaskEnqueueService {

    private final EinvoiceBossDocumentInitializerService documentInitializer;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossIssueTaskRepository taskRepository;

    /**
     * 为指定票据创建 boss 单据并入队任务。
     *
     * @param invoiceId 票据主键
     * @param triggerSource 触发来源码
     */
    public void enqueue(Long invoiceId, String triggerSource) {
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
        } catch (DataIntegrityViolationException ex) {
            // 蓝绿/重复事件：uk_einv_task_invoice_active 保证仅一条活跃任务
            log.debug("Issue task already enqueued, invoiceId={}", invoiceId);
        }
    }

    /**
     * 解析或初始化与 invoiceId 关联的 boss 单据。
     *
     * <p>若已有单据且金额已调整（{@code amountStatus=ADJUSTED}），则刷新明细；
     * 否则走完整 initialize。</p>
     *
     * @param invoiceId 票据主键
     * @return 可用于创建 task 的 document
     */
    private EinvBossDocument resolveDocument(Long invoiceId) {
        Optional<EinvBossDocument> existing = documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
        if (existing.isPresent() && AmountStatus.ADJUSTED.getCode().equals(existing.get().getAmountStatus())) {
            return documentInitializer.refreshForIssue(invoiceId);
        }
        return documentInitializer.initialize(invoiceId);
    }
}
