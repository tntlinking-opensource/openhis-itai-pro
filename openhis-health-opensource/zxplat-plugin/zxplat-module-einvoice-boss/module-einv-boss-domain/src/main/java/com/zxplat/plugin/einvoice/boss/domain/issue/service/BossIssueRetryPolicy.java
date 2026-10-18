/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.issue.service;

import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.share.constants.EinvBossPlatformCodes;

/**
 * 开票任务失败后是否允许调度器自动重试。
 *
 * <p>超时、瞬时网络类可退避重试；金额不一致、大类超限、审核周期未上报等
 * 须人工处理后才能成功，自动重试只会打爆博思并拖垮补偿页。</p>
 */
public final class BossIssueRetryPolicy {

    private BossIssueRetryPolicy() {
    }

    /**
     * 是否允许 {@code BossIssueScheduler} 在 {@code nextRetryAt} 到期后再次出站。
     *
     * @param errorCode 任务 {@code last_error_code}（HIS 侧码）
     * @param errorMsg 任务 {@code last_error_msg}（可含博思原文码）
     * @return true 表示可自动重试；false 表示停重试、留补偿页
     */
    public static boolean shouldAutoRetry(String errorCode, String errorMsg) {
        if (EinvBossErrorCodes.EINV_AMOUNT_MISMATCH.equals(errorCode)) {
            // 明细与票面不一致，须调额后才能再开，重试无意义
            return false;
        }
        if (EinvBossErrorCodes.EINV_CHARGE_DETAIL_LIMIT.equals(errorCode)) {
            // 收费大类超院点上限，须改对照，重试仍会被本地拦截
            return false;
        }
        if (EinvBossErrorCodes.EINV_BOSS_REJECT.equals(errorCode)
                && containsPlatformCode(errorMsg, EinvBossPlatformCodes.EYD_COM_0303)) {
            // 博思要求先上报审核周期数据，日结前重试必然继续拒收
            return false;
        }
        return true;
    }

    /**
     * 错误信息是否包含指定博思平台码（兼容乱码前后的 ASCII 前缀）。
     *
     * @param errorMsg 拒收详情
     * @param platformCode 博思原文码
     * @return 包含则 true
     */
    private static boolean containsPlatformCode(String errorMsg, String platformCode) {
        if (errorMsg == null || errorMsg.isEmpty() || platformCode == null || platformCode.isEmpty()) {
            return false;
        }
        return errorMsg.contains(platformCode);
    }
}
