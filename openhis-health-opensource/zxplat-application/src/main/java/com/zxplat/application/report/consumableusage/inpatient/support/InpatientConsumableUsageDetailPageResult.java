/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.consumableusage.inpatient.support;

import java.util.List;

import com.zxplat.application.report.consumableusage.inpatient.dto.InpatientConsumableUsageDetailRowDto;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院耗材使用报表明细分页编排结果（support 内部；非 HTTP Response）。
 */
@Getter
@Setter
public class InpatientConsumableUsageDetailPageResult {

    /** 当前页明细。 */
    private List<InpatientConsumableUsageDetailRowDto> records;

    /** 总行数。 */
    private long total;

    /** 页码（1 基）。 */
    private int pageNo;

    /** 页大小。 */
    private int pageSize;
}
