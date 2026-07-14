/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.domain.writeoff.access.BossWriteOffOutboundAccess;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.domain.enums.WriteOffStatus;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.writeoff.result.WriteOffInvokeOutcome;
import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 博思冲红业务服务（writeOffEBill）：冲红状态机 + 委托 {@link BossWriteOffOutboundAccess} 出站。
 *
 * <p>状态流转：SUCCESS 幂等返回 → claim PROCESSING → 出站 → SUCCESS/FAILED/SKIPPED。
 * 红票号优先取 {@code eScarletBillNo}，回退 {@code billNo}。</p>
 */
@Service
@RequiredArgsConstructor
public class BossWriteOffService {

    private final BossWriteOffOutboundAccess writeOffOutboundAccess;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final EinvBossDocumentRepository documentRepository;

    /**
     * 同步冲红入口。
     *
     * @param invoiceId 票据主键
     * @param reason 冲红原因（写入博思报文）
     * @return 冲红票结果 DTO
     */
    public BossInvoiceIssueResult writeOff(Long invoiceId, String reason) {
        EinvBossDocument document = documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "无电子票据单据"));
        if (!StringUtils.isNotBlank(document.getBossBillNo())) {
            markSkipped(document, "未开出蓝票");
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "未开出蓝票，无需博思冲红");
        }
        if (WriteOffStatus.SUCCESS.getCode().equals(document.getWriteOffStatus())
                && StringUtils.isNotBlank(document.getWriteOffBillNo())) {
            return toWriteOffResult(document);
        }
        int claimed = documentRepository.claimWriteOffProcessing(
                document.getId(), WriteOffStatus.PROCESSING.getCode(), LocalDateTime.now());
        if (claimed == 0) {
            EinvBossDocument latest = documentRepository.findById(document.getId()).orElse(document);
            if (WriteOffStatus.SUCCESS.getCode().equals(latest.getWriteOffStatus())) {
                return toWriteOffResult(latest);
            }
            throw new BusinessException(EinvBossErrorCodes.EINV_WRITE_OFF_CONCURRENT, "冲红正在被其他节点处理");
        }
        document = documentRepository.findById(document.getId()).orElse(document);

        BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(invoiceId);
        try {
            WriteOffInvokeOutcome outcome =
                    writeOffOutboundAccess.invokeWriteOff(document, snapshot, reason);
            markSuccess(document, outcome.invokeResult());
            return outcome.issueResult();
        } catch (BusinessException ex) {
            if (EinvBossErrorCodes.EINV_BOSS_REJECT.equals(ex.getErrorCode())) {
                markFailed(document, ex.getMessage());
            }
            throw ex;
        } catch (Exception ex) {
            markFailed(document, ex.getMessage());
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_TIMEOUT, "调用博思冲红异常: " + ex.getMessage(), ex);
        }
    }

    /**
     * 从 document 冲红列组装幂等返回结果（不再次调用博思）。
     *
     * @param document 冲红状态已为 SUCCESS 的单据
     * @return billNo 为红票号的结果 DTO
     */
    private BossInvoiceIssueResult toWriteOffResult(EinvBossDocument document) {
        BossInvoiceIssueResult result = new BossInvoiceIssueResult();
        result.setBusNo(document.getBusNo());
        result.setBillNo(document.getWriteOffBillNo());
        result.setBatchCode(document.getWriteOffBatchCode());
        result.setTotalAmt(document.getTotalAmt());
        return result;
    }

    /**
     * 冲红成功：写入红票号/批次、清空错误信息。
     *
     * <p>红票字段映射规则：优先 {@code invokeResult.eScarlet*}，缺失时回退蓝票 {@code billNo/billBatchCode}。</p>
     *
     * @param document 处于 PROCESSING 的单据
     * @param invokeResult 博思冲红原始响应（含 eScarlet 与蓝票字段）
     */
    private void markSuccess(EinvBossDocument document, BossInvokeResult invokeResult) {
        document.setWriteOffStatus(WriteOffStatus.SUCCESS.getCode());
        document.setWriteOffBillNo(StringUtils.defaultIfBlank(
                invokeResult.getEScarletBillNo(), invokeResult.getBillNo()));
        document.setWriteOffBatchCode(StringUtils.defaultIfBlank(
                invokeResult.getEScarletBillBatchCode(), invokeResult.getBillBatchCode()));
        document.setWriteOffAt(LocalDateTime.now());
        document.setWriteOffMessage(null);
        documentRepository.save(document);
    }

    /**
     * 冲红失败：记录 FAILED 与截断后的博思/异常信息（最多 500 字符）。
     *
     * @param document 单据
     * @param message 失败原因
     */
    private void markFailed(EinvBossDocument document, String message) {
        document.setWriteOffStatus(WriteOffStatus.FAILED.getCode());
        document.setWriteOffAt(LocalDateTime.now());
        document.setWriteOffMessage(StringUtils.left(message, 500));
        documentRepository.save(document);
    }

    /**
     * 冲红跳过：无蓝票等前置不满足时标记 SKIPPED（非异常路径的审计记录）。
     *
     * @param document 单据
     * @param message 跳过原因
     */
    private void markSkipped(EinvBossDocument document, String message) {
        document.setWriteOffStatus(WriteOffStatus.SKIPPED.getCode());
        document.setWriteOffAt(LocalDateTime.now());
        document.setWriteOffMessage(message);
        documentRepository.save(document);
    }
}
