/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.issue.access;

import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.IssueInvokeOutcome;

/** 本地单据与票面结果映射端口（bridge 实现）。 */
public interface BossIssueDocumentResultAccess {

    BossInvoiceIssueResult fromDocument(EinvBossDocument document);

    /**
     * 单张开具成功后写回主单据。
     *
     * @param document 单据
     * @param result 博思蓝票结果
     * @param payloadJson 本次 issueEBill 请求体
     */
    void persistIssueResult(EinvBossDocument document, BossInvoiceIssueResult result, String payloadJson);

    /**
     * 拆票（或多张）开具成功后：主票更新 + 续票各落一条单据，并按分片归属明细行。
     *
     * @param primaryDocument 主单据（busNo 与 health 对齐）
     * @param outcome 全部分片出站结果
     */
    void persistSplitIssueResults(EinvBossDocument primaryDocument, IssueInvokeOutcome outcome);

    void persistRecoveredBill(EinvBossDocument document, BossInvoiceIssueResult recovered);
}
