/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.audit.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 事前提醒统计分页 + 汇总。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DetailAuditStatsPageResult {

    private PagedResult<DetailAuditStatsHitRow> page;
    private long hitCount;
    private long mdtrtCount;
    private long doctorCount;
    private BigDecimal volaAmtSum;
}
