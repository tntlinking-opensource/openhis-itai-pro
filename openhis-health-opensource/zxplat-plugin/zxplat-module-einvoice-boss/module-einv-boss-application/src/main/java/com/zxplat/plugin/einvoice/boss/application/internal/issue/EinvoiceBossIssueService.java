/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.internal.issue;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.zxplat.plugin.einvoice.boss.domain.enums.TaskStatus;
import com.zxplat.plugin.einvoice.boss.domain.enums.TaskTriggerSource;
import com.zxplat.plugin.einvoice.boss.domain.writeoff.loader.EinvoiceInvoiceWritebackLoader;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.zxplat.plugin.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.zxplat.plugin.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import com.zxplat.plugin.einvoice.boss.application.internal.document.EinvoiceBossDocumentInitializerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 博思电子票据开具编排服务（异步入队、补偿页、同步触发入口）。
 *
 * <p>介于 {@link com.zxplat.plugin.einvoice.boss.application.issue.workflow.EinvoiceIssueOrchestratorImpl}
 * 与 {@link BossIssueWorkerService} 之间：负责 task 生命周期管理，不直接调用 GatewayClient。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EinvoiceBossIssueService {

    private final BossIssueTaskEnqueueService enqueueService;
    private final EinvBossIssueTaskRepository taskRepository;
    private final EinvoiceBossDocumentInitializerService documentInitializer;
    private final BossIssueWorkerService issueWorker;
    private final BossIssueWorkerWakeBridge wakeBridge;
    private final BossRecoverService recoverService;
    private final BossWriteOffService writeOffService;
    private final EinvoiceInvoiceWritebackLoader invoiceWritebackLoader;
    private final EinvBossDocumentRepository documentRepository;

    /**
     * 支付完成后异步入队：创建 document/line/task，并立即唤醒 Worker。
     *
     * @param invoiceId health 票据主键
     */
    public void scheduleIssueAsync(Long invoiceId) {
        if (invoiceId == null) {
            return;
        }
        Optional<EinvBossIssueTask> existing = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
        if (existing.isPresent()) {
            log.debug("Issue task already exists for invoiceId={}", invoiceId);
            wakeIfPending(existing.get());
            return;
        }
        Optional<Long> taskId = enqueueService.enqueue(invoiceId, TaskTriggerSource.EVENT.getCode());
        if (taskId.isPresent()) {
            wakeBridge.wakeProcessTaskAfterCommit(taskId.get());
            return;
        }
        // 并发入队撞唯一约束：再查一次，PENDING 则补唤醒
        taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId).ifPresent(this::wakeIfPending);
    }

    /**
     * 手工重新入队（待补偿页）。
     *
     * @param invoiceId 票据主键
     */
    public void requeue(Long invoiceId) {
        EinvBossIssueTask task = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "无活跃开票任务"));
        // initialize：非 ADJUSTED 时重建明细（修正 std/数量），ADJUSTED 仍保留人工调额
        documentInitializer.initialize(invoiceId);
        // Cmd：重置为待消费并标记人工触发
        task.setStatus(TaskStatus.PENDING.getCode());
        task.setTriggerSource(TaskTriggerSource.MANUAL.getCode());
        task.setLastErrorCode(null);
        task.setLastErrorMsg(null);
        task.setNextRetryAt(null);
        taskRepository.save(task);
        wakeBridge.wakeProcessTaskAfterCommit(task.getId());
    }

    /**
     * PENDING 任务补唤醒（幂等；PROCESSING/SUCCESS 跳过）。
     *
     * @param task 活跃任务
     */
    private void wakeIfPending(EinvBossIssueTask task) {
        if (task != null && TaskStatus.PENDING.getCode().equals(task.getStatus())) {
            wakeBridge.wakeProcessTaskAfterCommit(task.getId());
        }
    }

    /**
     * 待补偿「重新入队」：无 task 时异步入队，有 task 时重置为 PENDING。
     *
     * @param invoiceId 票据主键
     */
    public void compensateRequeue(Long invoiceId) {
        Optional<EinvBossIssueTask> existing = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
        if (existing.isPresent()) {
            requeue(invoiceId);
            return;
        }
        scheduleIssueAsync(invoiceId);
    }

    /**
     * 立即同步执行一次 Worker。
     *
     * @param invoiceId 票据主键
     * @return 开具结果
     */
    public BossInvoiceIssueResult issueNow(Long invoiceId) {
        return issueWorker.processByInvoiceId(invoiceId);
    }

    /**
     * 按 busNo 查票补偿。
     *
     * @param invoiceId 票据主键
     * @return 查票结果
     */
    public Optional<BossInvoiceIssueResult> recoverByBusNo(Long invoiceId) {
        Optional<BossInvoiceIssueResult> recovered = recoverService.recoverByInvoiceId(invoiceId);
        if (recovered.isPresent()) {
            invoiceWritebackLoader.issueElectronicInvoice(invoiceId, recovered.get(), null);
            EinvBossIssueTask task = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId).orElse(null);
            if (task != null) {
                task.setStatus(TaskStatus.SUCCESS.getCode());
                taskRepository.save(task);
            }
        }
        return recovered;
    }

    /**
     * 按 boss 单据主键同步开票（幂等：已有蓝票号则直接返回）。
     *
     * @param documentId boss 单据主键
     * @return 开具结果
     */
    public BossInvoiceIssueResult issueByDocument(Long documentId) {
        EinvBossDocument document = documentRepository.findById(documentId)
                .filter(doc -> !Boolean.TRUE.equals(doc.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "无电子票据单据"));
        if (StringUtils.isNotBlank(document.getBossBillNo())) {
            BossInvoiceIssueResult result = new BossInvoiceIssueResult();
            result.setBusNo(document.getBusNo());
            result.setBillNo(document.getBossBillNo());
            result.setBatchCode(document.getBossBatchCode());
            result.setPictureUrl(document.getPictureUrl());
            result.setPictureNetUrl(document.getPictureNetUrl());
            return result;
        }
        Long invoiceId = document.getInvoiceId();
        if (invoiceId == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据未关联票据");
        }
        return issueNow(invoiceId);
    }

    /**
     * 同步冲红。
     *
     * @param invoiceId 票据主键
     * @param reason 冲红原因
     * @return 冲红结果
     */
    public BossInvoiceIssueResult writeOff(Long invoiceId, String reason) {
        return writeOffService.writeOff(invoiceId, reason);
    }
}
