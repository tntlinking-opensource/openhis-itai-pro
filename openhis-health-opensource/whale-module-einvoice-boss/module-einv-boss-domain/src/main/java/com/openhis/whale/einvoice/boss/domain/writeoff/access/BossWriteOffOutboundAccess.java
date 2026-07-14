/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.writeoff.access;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.writeoff.result.WriteOffInvokeOutcome;

/** 冲红出站端口（bridge 实现）。 */
public interface BossWriteOffOutboundAccess {

    WriteOffInvokeOutcome invokeWriteOff(EinvBossDocument document, BossIssueSnapshot snapshot, String reason);
}
