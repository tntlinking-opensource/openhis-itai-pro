/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.support;

import com.openhis.whale.einvoice.boss.domain.issue.access.BossIssueDocumentResultAccess;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.parser.IssueOutputParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 本地 boss 单据与 {@link BossInvoiceIssueResult} 的双向映射与持久化。
 *
 * <p>委托 {@link IssueOutputParser} 做字段映射，本类负责 {@link EinvBossDocumentRepository#save}。</p>
 */
@Component
@RequiredArgsConstructor
public class BossIssueDocumentResultSupport implements BossIssueDocumentResultAccess {

    private final EinvBossDocumentRepository documentRepository;
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
}
