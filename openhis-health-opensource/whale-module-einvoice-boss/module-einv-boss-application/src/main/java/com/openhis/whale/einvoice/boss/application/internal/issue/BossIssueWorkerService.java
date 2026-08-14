/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.einvoice.boss.application.internal.common.BossCommonSnapshotSupport;
import com.openhis.whale.einvoice.boss.application.internal.document.EinvoiceBossDocumentInitializerService;
import com.openhis.whale.einvoice.boss.domain.issue.access.BossIssueDocumentResultAccess;
import com.openhis.whale.einvoice.boss.domain.issue.access.BossIssueOutboundAccess;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.domain.enums.IssueKind;
import com.openhis.whale.einvoice.boss.domain.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import com.openhis.whale.einvoice.boss.domain.document.service.BossInvoiceAmountValidator;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.result.IssueInvokeOutcome;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.writeoff.loader.EinvoiceInvoiceWritebackLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 异步开票 Worker：任务状态机 + 委托 {@link BossIssueOutboundAccess} 出站。
 *
 * <p>消费 {@code einv_boss_issue_task}（PENDING/FAILED），在蓝绿多实例下通过
 * {@link BossIssueTaskStatusWriter#tryClaimProcessing} 原子抢占。不直接注入
 * {@code EinvoiceBossGatewayClient}，出站经 bridge Invoker。</p>
 *
 * <p>关键分支：本地已有蓝票号幂等、health 已 ISSUED 短路、开具前 busNo 查票补偿、
 * 出站异常后二次查票兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BossIssueWorkerService {

    private final EinvBossIssueTaskRepository taskRepository;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossLineRepository lineRepository;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final EinvoiceInvoiceWritebackLoader invoiceWritebackLoader;
    private final BossInvoiceAmountValidator amountValidator;
    private final BossIssueOutboundAccess issueOutboundAccess;
    private final BossIssueDocumentResultAccess documentResultAccess;
    private final BossCommonSnapshotSupport commonSnapshotSupport;
    private final BossIssueTaskStatusWriter taskStatusWriter;
    private final EinvoiceBossDocumentInitializerService documentInitializer;
    private final BossRecoverService recoverService;
    private final TenantContext tenantContext;

    /**
     * 按任务主键消费一次开票（Scheduler / 手工触发）。
     *
     * @param taskId {@code einv_boss_issue_task.id}
     * @return 开具结果
     */
    public BossInvoiceIssueResult processTask(Long taskId) {
        EinvBossIssueTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "任务不存在"));
        return processLockedTask(task);
    }

    /**
     * 按 health 票据主键消费活跃任务（补偿页「立即开票」）。
     *
     * @param invoiceId {@code adm_invoice.id}
     * @return 开具结果
     */
    public BossInvoiceIssueResult processByInvoiceId(Long invoiceId) {
        EinvBossIssueTask task = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "无活跃开票任务"));
        return processLockedTask(task);
    }

    /**
     * 任务消费主流程：校验状态 → 幂等 → 抢占 → 快照 → 金额校验 → 查票/出站 → 回写。
     *
     * @param task 待消费的活跃任务（PENDING 或 FAILED）
     * @return 最终开具结果
     */
    private BossInvoiceIssueResult processLockedTask(EinvBossIssueTask task) {
        if (!TaskStatus.PENDING.getCode().equals(task.getStatus())
                && !TaskStatus.FAILED.getCode().equals(task.getStatus())) {
            throw new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "任务状态不可消费: " + task.getStatus());
        }

        EinvBossDocument document = documentRepository.findById(task.getDocumentId())
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据不存在"));
        boolean commonIssue = IssueKind.COM.getCode().equals(document.getIssueKind());

        if (StringUtils.isNotBlank(document.getBossBillNo())) {
            return finishAlreadyIssued(task, document, commonIssue);
        }

        if (!taskStatusWriter.tryClaimProcessing(task.getId())) {
            throw new BusinessException(EinvBossErrorCodes.EINV_TASK_CONCURRENT, "任务正在被其他节点处理");
        }

        // @Async / 定时线程无 HTTP 租户；且可能残留其它请求租户。全任务绑定单据租户，
        // 保证产品甲乙丙展开与单位术语转中文在出站 normalize 时仍可用。
        Long previousTenantId = TenantUtils.getCurrentTenantId();
        boolean tenantBound = bindDocumentTenant(document.getTenantId());
        try {
            return processClaimedTask(task, document, commonIssue);
        } finally {
            restoreTenant(tenantBound, previousTenantId);
        }
    }

    /**
     * 抢占成功后的开具主体（调用方已绑定单据租户）。
     */
    private BossInvoiceIssueResult processClaimedTask(
            EinvBossIssueTask task, EinvBossDocument document, boolean commonIssue) {
        BossIssueSnapshot snapshot;
        if (commonIssue) {
            snapshot = commonSnapshotSupport.build(document);
        } else {
            snapshot = issueSnapshotContextLoader.loadForInvoice(task.getInvoiceId());
            if (InvoiceStatus.ISSUED.getCode().equals(snapshot.getInvoiceStatus())) {
                taskStatusWriter.markSuccess(task.getId(), null);
                BossInvoiceIssueResult result = new BossInvoiceIssueResult();
                result.setBusNo(snapshot.getBusNo());
                result.setBillNo(snapshot.getHealthBillNo());
                return result;
            }
            // 退费/退号已作废的草稿：停止重试与补偿开具，避免对已退款业务重新出票。
            if (InvoiceStatus.CANCELLED.getCode().equals(snapshot.getInvoiceStatus())) {
                taskStatusWriter.markSuccess(task.getId(), "票据已作废，跳过开具");
                BossInvoiceIssueResult result = new BossInvoiceIssueResult();
                result.setBusNo(snapshot.getBusNo());
                return result;
            }
        }

        try {
            if (!commonIssue) {
                // initialize：非 ADJUSTED 重建明细（保证 std*数量=金额），ADJUSTED 保留人工调额
                document = documentInitializer.initialize(task.getInvoiceId());
                // 重建后再取快照，出站 enrich 与落库明细同源（药名/甲乙丙/中文单位）
                snapshot = issueSnapshotContextLoader.loadForInvoice(task.getInvoiceId());
            }
            List<EinvBossLine> lines = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
            List<BigDecimal> lineAmounts = lines.stream().map(EinvBossLine::getAmount).collect(Collectors.toList());
            amountValidator.validate(document.getTotalAmt(), lineAmounts);

            if (!commonIssue) {
                BossInvoiceIssueResult recovered = tryRecoverAndFinish(task, document, snapshot);
                if (recovered != null) {
                    return recovered;
                }
            }

            IssueInvokeOutcome outcome =
                    issueOutboundAccess.invokeIssue(task, document, lines, snapshot);
            documentResultAccess.persistSplitIssueResults(document, outcome);
            if (!commonIssue) {
                invoiceWritebackLoader.issueElectronicInvoice(
                        task.getInvoiceId(), outcome.issueResult(), snapshot.getOperatorId());
            }
            taskStatusWriter.markSuccess(task.getId(), null);
            return outcome.issueResult();
        } catch (BusinessException ex) {
            if (EinvBossErrorCodes.EINV_BOSS_REJECT.equals(ex.getErrorCode())) {
                taskStatusWriter.failTask(task.getId(), ex.getErrorCode(), ex.getMessage());
            } else if (EinvBossErrorCodes.EINV_AMOUNT_MISMATCH.equals(ex.getErrorCode())) {
                taskStatusWriter.failTask(task.getId(), ex.getErrorCode(), ex.getMessage());
            } else if (!EinvBossErrorCodes.EINV_TASK_CONCURRENT.equals(ex.getErrorCode())) {
                taskStatusWriter.failTask(task.getId(), ex.getErrorCode(), ex.getMessage());
            }
            throw ex;
        } catch (Exception ex) {
            if (!commonIssue) {
                BossInvoiceIssueResult recovered = tryRecoverAndFinish(task, document, snapshot);
                if (recovered != null) {
                    return recovered;
                }
            }
            taskStatusWriter.failTask(task.getId(), EinvBossErrorCodes.EINV_BOSS_TIMEOUT, ex.getMessage());
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_TIMEOUT, "调用博思异常: " + ex.getMessage(), ex);
        }
    }

    /**
     * 将当前线程租户覆盖为单据租户；已是同一租户则不标记为已绑定。
     *
     * @param documentTenantId 单据租户；空则不改
     * @return true 表示本方法改写了租户，调用方须 {@link #restoreTenant}
     */
    private boolean bindDocumentTenant(Long documentTenantId) {
        if (documentTenantId == null) {
            return false;
        }
        Long current = TenantUtils.getCurrentTenantId();
        if (documentTenantId.equals(current)) {
            return false;
        }
        tenantContext.setCurrentTenantId(documentTenantId);
        return true;
    }

    /**
     * 恢复 {@link #bindDocumentTenant} 之前的租户（无则 clear）。
     */
    private void restoreTenant(boolean tenantBound, Long previousTenantId) {
        if (!tenantBound) {
            return;
        }
        if (previousTenantId == null) {
            tenantContext.clear();
        } else {
            tenantContext.setCurrentTenantId(previousTenantId);
        }
    }

    /**
     * 开具前按 busNo 查票：若博思侧已有票则补偿落库并完成任务，避免重复开具。
     *
     * @param task 当前任务
     * @param document boss 单据（尚无 bossBillNo）
     * @param snapshot health 快照
     * @return 查票成功并完成回写时的结果；无需补偿或失败时 {@code null}
     */
    private BossInvoiceIssueResult tryRecoverAndFinish(
            EinvBossIssueTask task, EinvBossDocument document, BossIssueSnapshot snapshot) {
        if (StringUtils.isNotBlank(document.getBossBillNo()) || StringUtils.isBlank(document.getBusNo())) {
            return null;
        }
        try {
            return recoverService.recoverByInvoiceId(task.getInvoiceId())
                    .map(recovered -> finishRecoveredIssue(task, document, snapshot, recovered))
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Recover by busNo failed before issue, invoiceId={}", task.getInvoiceId(), ex);
            return null;
        }
    }

    /**
     * 查票补偿成功后的收尾：持久化票面、回写 health、标记任务 SUCCESS。
     *
     * @param task 当前任务
     * @param document boss 单据
     * @param snapshot health 快照（取 operatorId）
     * @param recovered 博思查票结果
     * @return 与 {@code recovered} 相同
     */
    private BossInvoiceIssueResult finishRecoveredIssue(
            EinvBossIssueTask task,
            EinvBossDocument document,
            BossIssueSnapshot snapshot,
            BossInvoiceIssueResult recovered) {
        log.info("Recovered existing boss bill, invoiceId={}, billNo={}", task.getInvoiceId(), recovered.getBillNo());
        documentResultAccess.persistRecoveredBill(document, recovered);
        invoiceWritebackLoader.issueElectronicInvoice(task.getInvoiceId(), recovered, snapshot.getOperatorId());
        taskStatusWriter.markSuccess(task.getId(), "查票补偿成功");
        return recovered;
    }

    /**
     * 本地已有蓝票号时的幂等完成：可选补回写 health、标记任务 SUCCESS。
     *
     * @param task 当前任务
     * @param document 已含 bossBillNo 的单据
     * @param commonIssue 是否公共开票（COM 不回写 health）
     * @return 由 document 还原的票面结果
     */
    private BossInvoiceIssueResult finishAlreadyIssued(
            EinvBossIssueTask task, EinvBossDocument document, boolean commonIssue) {
        BossInvoiceIssueResult result = documentResultAccess.fromDocument(document);
        if (!commonIssue) {
            BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(task.getInvoiceId());
            if (!InvoiceStatus.ISSUED.getCode().equals(snapshot.getInvoiceStatus())) {
                invoiceWritebackLoader.issueElectronicInvoice(task.getInvoiceId(), result, snapshot.getOperatorId());
            }
        }
        if (!TaskStatus.SUCCESS.getCode().equals(task.getStatus())) {
            taskStatusWriter.markSuccess(task.getId(), "幂等：本地已有蓝票号");
        }
        return result;
    }
}
