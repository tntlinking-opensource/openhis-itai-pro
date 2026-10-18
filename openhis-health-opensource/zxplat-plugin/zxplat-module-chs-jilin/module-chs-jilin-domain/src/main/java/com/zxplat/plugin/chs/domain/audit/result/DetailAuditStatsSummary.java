/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.result;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 明细审核违规统计汇总条。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DetailAuditStatsSummary {

    private long hitCount;
    private long mdtrtCount;
    private long doctorCount;
    private BigDecimal volaAmtSum;
}
