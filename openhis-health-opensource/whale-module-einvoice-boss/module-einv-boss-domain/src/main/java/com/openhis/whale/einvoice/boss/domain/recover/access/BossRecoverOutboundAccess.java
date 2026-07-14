/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.recover.access;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;

import java.time.LocalDateTime;
import java.util.Optional;

/** 查票出站端口（bridge 实现）。 */
public interface BossRecoverOutboundAccess {

    Optional<BossInvoiceIssueResult> invokeBySnapshot(
            BossIssueSnapshot snapshot, Optional<EinvBossDocument> document);

    Optional<BossInvoiceIssueResult> invokeByBusNo(
            String busNo, LocalDateTime busDateTime, Optional<EinvBossDocument> document);
}
