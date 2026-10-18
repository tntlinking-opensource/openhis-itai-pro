/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.compensation.response;

import lombok.Data;

/**
 * 待补偿统计（角标/顶栏/列表摘要）。
 */
@Data
public class BossPendingCompensationStats {

    /** 待处理总数。 */
    private long pendingCount;

    /** 事件丢失。 */
    private long lostCount;

    /** 金额待调整。 */
    private long amountCount;

    /** 开票失败。 */
    private long failedCount;

    /** 超时待查。 */
    private long staleCount;

    /** 扫描候选数达到上限时为 true。 */
    private boolean scanTruncated;
}
