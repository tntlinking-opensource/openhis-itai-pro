package com.openhis.whale.einvoice.boss.application.issue;

import com.openhis.whale.einvoice.boss.application.document.EinvoiceBossDocumentInitializerAppService;
import com.openhis.whale.einvoice.boss.contracts.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.task.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 开票任务入队 AppService：初始化 document/line 并创建 task。
 * <p>由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 管理 UoW。</p>
 */
@Service
@RequiredArgsConstructor
public class BossIssueTaskEnqueueAppService {

    private final EinvoiceBossDocumentInitializerAppService documentInitializer;
    private final EinvBossIssueTaskRepository taskRepository;

    /**
     * 为指定票据创建 boss 单据并入队任务。
     *
     * @param invoiceId 票据主键
     * @param triggerSource 触发来源码
     */
    public void enqueue(Long invoiceId, String triggerSource) {
        EinvBossDocument document = documentInitializer.initialize(invoiceId);
        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setTenantId(document.getTenantId());
        task.setInvoiceId(invoiceId);
        task.setDocumentId(document.getId());
        task.setStatus(TaskStatus.PENDING.getCode());
        task.setAttemptCount(0);
        task.setTriggerSource(triggerSource);
        task.setIsDeleted(false);
        taskRepository.save(task);
    }
}
