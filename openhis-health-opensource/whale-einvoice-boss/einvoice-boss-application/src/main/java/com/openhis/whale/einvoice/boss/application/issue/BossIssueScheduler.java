package com.openhis.whale.einvoice.boss.application.issue;

import com.openhis.whale.einvoice.boss.contracts.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.contracts.reader.HealthInvoiceSnapshotReader;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.task.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开票任务调度：重试 FAILED 任务 + DRAFT 兜底扫描。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.einvoice-boss.enabled", havingValue = "true")
public class BossIssueScheduler {

    private final EinvBossIssueTaskRepository taskRepository;
    private final BossIssueWorkerAppService issueWorker;
    private final EinvoiceBossIssueAppService issueAppService;
    private final HealthInvoiceSnapshotReader healthInvoiceSnapshotReader;

    /**
     * 每 30 秒拉取可重试任务并消费。
     */
    @Scheduled(fixedDelayString = "${einvoice.boss.scheduler.poll-ms:30000}")
    public void pollRetryableTasks() {
        List<EinvBossIssueTask> pending = taskRepository.findByStatusAndIsDeletedFalse(TaskStatus.PENDING.getCode());
        List<EinvBossIssueTask> failed = taskRepository.findByStatusAndIsDeletedFalseAndNextRetryAtBefore(
                TaskStatus.FAILED.getCode(), LocalDateTime.now());
        for (EinvBossIssueTask task : pending) {
            try {
                issueWorker.processTask(task.getId());
            } catch (Exception ex) {
                log.warn("Boss issue task failed, taskId={}, msg={}", task.getId(), ex.getMessage());
            }
        }
        for (EinvBossIssueTask task : failed) {
            try {
                issueWorker.processTask(task.getId());
            } catch (Exception ex) {
                log.warn("Boss issue retry failed, taskId={}, msg={}", task.getId(), ex.getMessage());
            }
        }
    }

    /**
     * 每 5 分钟扫描 orphan DRAFT（事件丢失兜底）。
     */
    @Scheduled(fixedDelayString = "${einvoice.boss.scheduler.orphan-scan-ms:300000}")
    public void scanOrphanDrafts() {
        List<Long> invoiceIds = healthInvoiceSnapshotReader.findOrphanDraftInvoiceIds(20);
        for (Long invoiceId : invoiceIds) {
            issueAppService.scheduleIssueAsync(invoiceId);
        }
    }
}
