/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.access;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.result.IssueInvokeOutcome;

import java.util.List;

/** 开具出站端口（bridge 实现）。 */
public interface BossIssueOutboundAccess {

    IssueInvokeOutcome invokeIssue(
            EinvBossIssueTask task,
            EinvBossDocument document,
            List<EinvBossLine> lines,
            BossIssueSnapshot snapshot);
}
