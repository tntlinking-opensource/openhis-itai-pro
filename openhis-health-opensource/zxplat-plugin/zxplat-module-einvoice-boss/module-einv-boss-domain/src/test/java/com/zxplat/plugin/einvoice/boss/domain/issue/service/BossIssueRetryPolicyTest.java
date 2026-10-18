/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.issue.service;

import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.share.constants.EinvBossPlatformCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BossIssueRetryPolicy} 永久拒收与可重试超时的分流。
 */
class BossIssueRetryPolicyTest {

    @Test
    void shouldAutoRetry_timeout() {
        assertTrue(BossIssueRetryPolicy.shouldAutoRetry(EinvBossErrorCodes.EINV_BOSS_TIMEOUT, "read timed out"));
    }

    @Test
    void shouldNotAutoRetry_auditCycleReject() {
        String msg = "博思返回: " + EinvBossPlatformCodes.EYD_COM_0303 + ":请先上报数据，才能继续开票";
        assertFalse(BossIssueRetryPolicy.shouldAutoRetry(EinvBossErrorCodes.EINV_BOSS_REJECT, msg));
    }

    @Test
    void shouldNotAutoRetry_amountMismatch() {
        assertFalse(BossIssueRetryPolicy.shouldAutoRetry(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH, "明细合计不一致"));
    }

    @Test
    void shouldNotAutoRetry_chargeDetailLimit() {
        assertFalse(BossIssueRetryPolicy.shouldAutoRetry(EinvBossErrorCodes.EINV_CHARGE_DETAIL_LIMIT, "大类超限"));
    }

    @Test
    void shouldAutoRetry_otherBossReject() {
        assertTrue(BossIssueRetryPolicy.shouldAutoRetry(EinvBossErrorCodes.EINV_BOSS_REJECT, "博思返回: E9999:系统繁忙"));
    }
}
