/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.access;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;

/** 本地单据与票面结果映射端口（bridge 实现）。 */
public interface BossIssueDocumentResultAccess {

    BossInvoiceIssueResult fromDocument(EinvBossDocument document);

    void persistIssueResult(EinvBossDocument document, BossInvoiceIssueResult result, String payloadJson);

    void persistRecoveredBill(EinvBossDocument document, BossInvoiceIssueResult recovered);
}
