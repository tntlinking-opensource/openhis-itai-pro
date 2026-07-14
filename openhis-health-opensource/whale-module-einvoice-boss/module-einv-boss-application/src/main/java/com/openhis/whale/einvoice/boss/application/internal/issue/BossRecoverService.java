/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.issue;

import com.openhis.whale.einvoice.boss.domain.recover.access.BossRecoverOutboundAccess;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 博思查票补偿服务（getEBillByBusNo）：委托 {@link BossRecoverOutboundSupport} 出站 + 本地单据更新。
 *
 * <p>查票失败返回 {@link Optional#empty()}，不抛业务异常（与开具/冲红语义不同）。</p>
 */
@Service
@RequiredArgsConstructor
public class BossRecoverService {

    private final BossRecoverOutboundAccess recoverOutboundAccess;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final EinvBossDocumentRepository documentRepository;

    /**
     * 按 invoiceId 加载快照后向博思查票，成功时更新本地 document 蓝票字段。
     *
     * @param invoiceId 票据主键
     * @return 查票成功时的票面；博思无票或拒收时 empty
     */
    public Optional<BossInvoiceIssueResult> recoverByInvoiceId(Long invoiceId) {
        BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(invoiceId);
        Optional<EinvBossDocument> document = documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
        Optional<BossInvoiceIssueResult> recovered =
                recoverOutboundAccess.invokeBySnapshot(snapshot, document);
        recovered.ifPresent(result -> document.ifPresent(doc -> updateDocument(doc, result)));
        return recovered;
    }

    /**
     * 仅按 busNo（及可选业务时间）查票，不强制更新 document（由调用方决定是否持久化）。
     *
     * @param busNo 业务流水号
     * @param busDateTime 原业务发生时间；Orchestrator 直调时可 {@code null}
     * @return 查票结果
     */
    public Optional<BossInvoiceIssueResult> recoverByBusNo(String busNo, LocalDateTime busDateTime) {
        Optional<EinvBossDocument> document = documentRepository.findByBusNoAndIsDeletedFalse(busNo);
        return recoverOutboundAccess.invokeByBusNo(busNo, busDateTime, document);
    }

    /**
     * 将查票得到的蓝票信息写入 {@code einv_boss_document}（不含 payloadJson）。
     *
     * @param document 与 invoiceId 关联的 boss 单据
     * @param result 查票解析后的票面
     */
    private void updateDocument(EinvBossDocument document, BossInvoiceIssueResult result) {
        document.setBossBillNo(result.getBillNo());
        document.setBossBatchCode(result.getBatchCode());
        document.setRandom(result.getRandom());
        document.setBillQrCode(result.getBillQrCode());
        document.setPictureUrl(result.getPictureUrl());
        document.setPictureNetUrl(result.getPictureNetUrl());
        documentRepository.save(document);
    }
}
