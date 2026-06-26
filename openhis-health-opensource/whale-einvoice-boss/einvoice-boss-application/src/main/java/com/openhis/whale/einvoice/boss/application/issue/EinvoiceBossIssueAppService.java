package com.openhis.whale.einvoice.boss.application.issue;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.contracts.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.contracts.enums.TaskTriggerSource;
import com.openhis.whale.einvoice.boss.contracts.writer.HealthInvoiceWritebackWriter;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.task.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 博思电子票据开具编排服务（异步入队、手工补偿入口）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EinvoiceBossIssueAppService {

    private final BossIssueTaskEnqueueAppService enqueueService;
    private final EinvBossIssueTaskRepository taskRepository;
    private final BossIssueWorkerAppService issueWorker;
    private final BossRecoverAppService recoverService;
    private final BossWriteOffAppService writeOffService;
    private final HealthInvoiceWritebackWriter healthInvoiceWritebackWriter;

    /**
     * 支付完成后异步入队：创建 document/line/task。
     *
     * @param invoiceId health 票据主键
     */
    public void scheduleIssueAsync(Long invoiceId) {
        // Gate：无 invoiceId 则忽略，避免空事件污染任务表
        if (invoiceId == null) {
            return;
        }
        // Pre：若已有活跃任务则幂等返回，防止重复入队
        Optional<EinvBossIssueTask> existing = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
        if (existing.isPresent()) {
            log.debug("Issue task already exists for invoiceId={}", invoiceId);
            return;
        }
        // Cmd：初始化 boss 单据并入队 PENDING 任务
        enqueueService.enqueue(invoiceId, TaskTriggerSource.EVENT.getCode());
    }

    /**
     * 手工重新入队（待补偿页）。
     *
     * @param invoiceId 票据主键
     */
    public void requeue(Long invoiceId) {
        EinvBossIssueTask task = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "无活跃开票任务"));
        // Cmd：重置为待消费并标记人工触发
        task.setStatus(TaskStatus.PENDING.getCode());
        task.setTriggerSource(TaskTriggerSource.MANUAL.getCode());
        task.setLastErrorCode(null);
        task.setLastErrorMsg(null);
        task.setNextRetryAt(null);
        taskRepository.save(task);
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
            healthInvoiceWritebackWriter.issueElectronicInvoice(invoiceId, recovered.get(), null);
            EinvBossIssueTask task = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId).orElse(null);
            if (task != null) {
                task.setStatus(TaskStatus.SUCCESS.getCode());
                taskRepository.save(task);
            }
        }
        return recovered;
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
