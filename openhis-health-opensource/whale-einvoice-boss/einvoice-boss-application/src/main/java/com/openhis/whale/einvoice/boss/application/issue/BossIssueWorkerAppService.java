package com.openhis.whale.einvoice.boss.application.issue;

import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.application.boss.BossInvokeResult;
import com.openhis.whale.einvoice.boss.application.boss.BossPayloadBuilder;
import com.openhis.whale.einvoice.boss.application.boss.BossResponseParser;
import com.openhis.whale.einvoice.boss.application.boss.BossSignClient;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.application.common.BossCommonSnapshotSupport;
import com.openhis.whale.einvoice.boss.contracts.enums.IssueKind;
import com.openhis.whale.einvoice.boss.contracts.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.contracts.reader.HealthInvoiceSnapshotReader;
import com.openhis.whale.einvoice.boss.contracts.writer.HealthInvoiceWritebackWriter;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import com.openhis.whale.einvoice.boss.domain.service.BossInvoiceAmountValidator;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.task.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 异步开票 Worker AppService：抢锁 → 校验 → 调博思 → 回写 health。
 * <p>由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 管理 UoW（含 {@link BossIssueScheduler} 定时调用）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BossIssueWorkerAppService {

    private static final int MAX_BACKOFF_MINUTES = 60;

    private final EinvBossIssueTaskRepository taskRepository;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossLineRepository lineRepository;
    private final HealthInvoiceSnapshotReader healthInvoiceSnapshotReader;
    private final HealthInvoiceWritebackWriter healthInvoiceWritebackWriter;
    private final BossInvoiceAmountValidator amountValidator;
    private final BossPayloadBuilder payloadBuilder;
    private final BossSignClient bossSignClient;
    private final BossResponseParser responseParser;
    private final BossInvokeLogWriter invokeLogWriter;
    private final BossCommonSnapshotSupport commonSnapshotSupport;

    /**
     * 消费指定任务一次。
     *
     * @param taskId 任务主键
     * @return 开具结果（已开具或成功开票）
     */
    public BossInvoiceIssueResult processTask(Long taskId) {
        EinvBossIssueTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "任务不存在"));
        return processLockedTask(task);
    }

    /**
     * 按 invoiceId 消费活跃任务。
     *
     * @param invoiceId 票据主键
     * @return 开具结果
     */
    public BossInvoiceIssueResult processByInvoiceId(Long invoiceId) {
        EinvBossIssueTask task = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "无活跃开票任务"));
        return processLockedTask(task);
    }

    private BossInvoiceIssueResult processLockedTask(EinvBossIssueTask task) {
        // Gate：仅 PENDING/FAILED 可消费
        if (!TaskStatus.PENDING.getCode().equals(task.getStatus())
                && !TaskStatus.FAILED.getCode().equals(task.getStatus())) {
            throw new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "任务状态不可消费: " + task.getStatus());
        }
        task.setStatus(TaskStatus.PROCESSING.getCode());
        task.setAttemptCount(task.getAttemptCount() + 1);
        taskRepository.save(task);

        EinvBossDocument document = documentRepository.findById(task.getDocumentId())
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据不存在"));
        boolean commonIssue = IssueKind.COM.getCode().equals(document.getIssueKind());

        BossIssueSnapshot snapshot;
        if (commonIssue) {
            snapshot = commonSnapshotSupport.build(document);
        } else {
            snapshot = healthInvoiceSnapshotReader.loadForInvoice(task.getInvoiceId());
            // 分支：health 已开具则对齐任务为 SUCCESS
            if (InvoiceStatus.ISSUED.getCode().equals(snapshot.getInvoiceStatus())) {
                markSuccess(task, null);
                BossInvoiceIssueResult result = new BossInvoiceIssueResult();
                result.setBusNo(snapshot.getBusNo());
                return result;
            }
        }

        try {
            List<EinvBossLine> lines = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
            List<BigDecimal> lineAmounts = lines.stream().map(EinvBossLine::getAmount).collect(Collectors.toList());
            // 金额硬校验：不等则不调博思
            amountValidator.validate(document.getTotalAmt(), lineAmounts);

            String serviceId = payloadBuilder.resolveServiceId(document.getIssueKind());
            String payload = payloadBuilder.build(document, lines, snapshot);
            long started = System.currentTimeMillis();
            String responseBody = bossSignClient.invoke(serviceId, payload);
            int durationMs = (int) (System.currentTimeMillis() - started);
            BossInvokeResult invokeResult = responseParser.parse(responseBody);
            invokeLogWriter.write(task, serviceId, document.getBusNo(), invokeResult, durationMs, payload, responseBody);

            if (!invokeResult.isSuccess()) {
                failTask(task, EinvBossErrorCodes.EINV_BOSS_REJECT, invokeResult.getResultCode());
                throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "博思返回: " + invokeResult.getResultCode());
            }

            BossInvoiceIssueResult issueResult = responseParser.toIssueResult(invokeResult, document.getBusNo(), document.getTotalAmt());
            updateDocument(document, issueResult, payload);
            if (!commonIssue) {
                healthInvoiceWritebackWriter.issueElectronicInvoice(task.getInvoiceId(), issueResult, snapshot.getOperatorId());
            }
            markSuccess(task, null);
            return issueResult;
        } catch (BusinessException ex) {
            if (EinvBossErrorCodes.EINV_AMOUNT_MISMATCH.equals(ex.getErrorCode())) {
                failTask(task, ex.getErrorCode(), ex.getMessage());
            } else if (!EinvBossErrorCodes.EINV_BOSS_REJECT.equals(ex.getErrorCode())) {
                failTask(task, ex.getErrorCode(), ex.getMessage());
            }
            throw ex;
        } catch (Exception ex) {
            failTask(task, EinvBossErrorCodes.EINV_BOSS_TIMEOUT, ex.getMessage());
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_TIMEOUT, "调用博思异常: " + ex.getMessage(), ex);
        }
    }

    private void updateDocument(EinvBossDocument document, BossInvoiceIssueResult result, String payload) {
        document.setBossBillNo(result.getBillNo());
        document.setBossBatchCode(result.getBatchCode());
        document.setRandom(result.getRandom());
        document.setBillQrCode(result.getBillQrCode());
        document.setPictureUrl(result.getPictureUrl());
        document.setPictureNetUrl(result.getPictureNetUrl());
        document.setPayloadJson(payload);
        documentRepository.save(document);
    }

    private void markSuccess(EinvBossIssueTask task, String message) {
        task.setStatus(TaskStatus.SUCCESS.getCode());
        task.setLastErrorCode(null);
        task.setLastErrorMsg(message);
        task.setNextRetryAt(null);
        taskRepository.save(task);
    }

    private void failTask(EinvBossIssueTask task, String errorCode, String errorMsg) {
        task.setStatus(TaskStatus.FAILED.getCode());
        task.setLastErrorCode(errorCode);
        task.setLastErrorMsg(errorMsg);
        int backoff = Math.min(task.getAttemptCount() * 2, MAX_BACKOFF_MINUTES);
        task.setNextRetryAt(LocalDateTime.now().plusMinutes(backoff));
        taskRepository.save(task);
    }
}
