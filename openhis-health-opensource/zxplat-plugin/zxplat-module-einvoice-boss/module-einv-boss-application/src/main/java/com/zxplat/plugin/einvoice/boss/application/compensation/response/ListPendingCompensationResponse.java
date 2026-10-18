/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.compensation.response;

import com.zxplat.plugin.einvoice.boss.domain.reconcile.result.BossCompensationRow;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 待补偿列表响应。
 */
@Data
public class ListPendingCompensationResponse {

    /** 统计摘要。 */
    private BossPendingCompensationStats stats = new BossPendingCompensationStats();

    /** 当前页行。 */
    private List<BossCompensationRow> rows = new ArrayList<>();

    /** 过滤后总数。 */
    private long total;
}
