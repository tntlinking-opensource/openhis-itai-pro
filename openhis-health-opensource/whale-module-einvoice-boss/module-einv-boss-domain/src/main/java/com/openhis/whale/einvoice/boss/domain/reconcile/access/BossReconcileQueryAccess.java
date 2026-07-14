/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.reconcile.access;

import com.openhis.whale.einvoice.boss.domain.reconcile.result.BossReconcileTotalResult;

import java.time.LocalDate;

/** 日结核对出站查询端口（bridge 实现）。 */
public interface BossReconcileQueryAccess {

    BossReconcileTotalResult queryBossTotal(
            Long tenantId,
            LocalDate checkDate,
            String dateMode,
            String issueKind,
            int pageNo,
            int pageSize);
}
