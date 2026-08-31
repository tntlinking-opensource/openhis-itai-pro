/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.support;

import com.openhis.whale.einvoice.boss.domain.issue.access.BossIssueDocumentResultAccess;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.IssueInvokeOutcome;
import com.openhis.whale.einvoice.boss.domain.issue.result.IssuePartOutcome;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.parser.IssueOutputParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地 boss 单据与 {@link BossInvoiceIssueResult} 的双向映射与持久化。
 *
 * <p>委托 {@link IssueOutputParser} 做字段映射；一结算一票落一条 {@code einv_boss_document}。
 * 历史拆票分片落库路径保留，便于兼容旧数据重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BossIssueDocumentResultSupport implements BossIssueDocumentResultAccess {

    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossLineRepository lineRepository;
    private final IssueOutputParser issueOutputParser;

    /**
     * 从已落库单据还原票面（幂等短路、finishAlreadyIssued）。
     *
     * @param document 含 bossBillNo 的单据
     * @return 票面 DTO
     */
    @Override
    public BossInvoiceIssueResult fromDocument(EinvBossDocument document) {
        return issueOutputParser.fromDocument(document);
    }

    /**
     * 开具成功后写回单据并保存请求 JSON。
     *
     * @param document 单据
     * @param result 博思蓝票结果
     * @param payloadJson 本次 issueEBill 请求体
     */
    @Override
    public void persistIssueResult(EinvBossDocument document, BossInvoiceIssueResult result, String payloadJson) {
        issueOutputParser.applyToDocument(document, result, payloadJson);
        documentRepository.save(document);
    }

    /**
     * 拆票落库：首张写主单据金额/票面；续票新建单据；明细按分片改挂 documentId。
     *
     * @param primaryDocument 主单据
     * @param outcome 全部分片
     */
    @Override
    public void persistSplitIssueResults(EinvBossDocument primaryDocument, IssueInvokeOutcome outcome) {
        if (outcome == null || outcome.parts() == null || outcome.parts().isEmpty()) {
            return;
        }
        if (outcome.parts().size() == 1) {
            IssuePartOutcome only = outcome.parts().get(0);
            if (only.totalAmt() != null) {
                primaryDocument.setTotalAmt(only.totalAmt());
                primaryDocument.setPaymentAmt(only.totalAmt());
            }
            persistIssueResult(primaryDocument, only.issueResult(), only.payloadJson());
            reassignLines(primaryDocument.getId(), only.lines());
            return;
        }

        softDeleteExistingSplitChildren(primaryDocument);

        for (IssuePartOutcome part : outcome.parts()) {
            EinvBossDocument target;
            if (part.partIndex() == 0) {
                target = primaryDocument;
                target.setTotalAmt(part.totalAmt());
                target.setPaymentAmt(part.totalAmt());
            } else {
                target = newSplitChildDocument(primaryDocument, part);
                target = documentRepository.saveAndFlush(target);
            }
            reassignLines(target.getId(), part.lines());
            issueOutputParser.applyToDocument(target, part.issueResult(), part.payloadJson());
            documentRepository.save(target);
        }
        log.info(
                "Persisted split einvoice documents, primaryBusNo={}, parts={}",
                primaryDocument.getBusNo(),
                outcome.parts().size());
    }

    /**
     * 查票补偿落库（不覆盖 payloadJson）。
     *
     * @param document 单据
     * @param recovered 查票得到的票面
     */
    @Override
    public void persistRecoveredBill(EinvBossDocument document, BossInvoiceIssueResult recovered) {
        issueOutputParser.applyToDocument(document, recovered, null);
        documentRepository.save(document);
    }

    /**
     * 将分片明细挂到目标单据并重排 lineNo。
     */
    private void reassignLines(Long documentId, List<EinvBossLine> lines) {
        if (documentId == null || lines == null || lines.isEmpty()) {
            return;
        }
        int lineNo = 1;
        List<EinvBossLine> toSave = new ArrayList<>();
        for (EinvBossLine line : lines) {
            if (line == null) {
                continue;
            }
            line.setDocumentId(documentId);
            line.setLineNo(lineNo);
            lineNo++;
            toSave.add(line);
        }
        if (!toSave.isEmpty()) {
            lineRepository.saveAll(toSave);
        }
    }

    /**
     * 软删同 invoice 下已有续票单据（busNo 形如 {@code 主票号-2}），便于重试幂等。
     */
    private void softDeleteExistingSplitChildren(EinvBossDocument primary) {
        if (primary.getInvoiceId() == null || !StringUtils.hasText(primary.getBusNo())) {
            return;
        }
        String prefix = primary.getBusNo() + "-";
        List<EinvBossDocument> siblings =
                documentRepository.findAllByInvoiceIdAndIsDeletedFalseOrderByIdAsc(primary.getInvoiceId());
        LocalDateTime now = LocalDateTime.now();
        for (EinvBossDocument sibling : siblings) {
            if (sibling.getId() == null || sibling.getId().equals(primary.getId())) {
                continue;
            }
            if (!StringUtils.hasText(sibling.getBusNo()) || !sibling.getBusNo().startsWith(prefix)) {
                continue;
            }
            sibling.setIsDeleted(true);
            sibling.setDeletedAt(now);
            documentRepository.save(sibling);
            List<EinvBossLine> childLines =
                    lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(sibling.getId());
            for (EinvBossLine line : childLines) {
                line.setIsDeleted(true);
                line.setDeletedAt(now);
            }
            if (!childLines.isEmpty()) {
                lineRepository.saveAll(childLines);
            }
        }
    }

    /**
     * 复制主单据头字段，生成续票单据（尚无蓝票号）。
     */
    private EinvBossDocument newSplitChildDocument(EinvBossDocument primary, IssuePartOutcome part) {
        EinvBossDocument child = new EinvBossDocument();
        child.setTenantId(primary.getTenantId());
        child.setInvoiceId(primary.getInvoiceId());
        child.setPaymentId(primary.getPaymentId());
        child.setIssueKind(primary.getIssueKind());
        child.setBusNo(part.busNo());
        child.setBusType(primary.getBusType());
        child.setTotalAmt(part.totalAmt());
        child.setPaymentAmt(part.totalAmt());
        child.setAmountStatus(primary.getAmountStatus());
        child.setPlatformProfile(primary.getPlatformProfile());
        child.setPayerName(primary.getPayerName());
        child.setPayerPhone(primary.getPayerPhone());
        child.setPayerIdCard(primary.getPayerIdCard());
        child.setPayerType(primary.getPayerType());
        child.setPayerSex(primary.getPayerSex());
        child.setPayerAge(primary.getPayerAge());
        child.setRemark(primary.getRemark());
        child.setOperatorName(primary.getOperatorName());
        child.setIsDeleted(false);
        return child;
    }
}
