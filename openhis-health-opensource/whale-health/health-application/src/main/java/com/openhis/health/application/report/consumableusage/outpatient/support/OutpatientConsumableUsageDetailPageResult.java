/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.support;

import java.util.List;

import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageDetailRowDto;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊耗材使用报表明细分页编排结果（support 内部；非 HTTP Response）。
 */
@Getter
@Setter
public class OutpatientConsumableUsageDetailPageResult {

    /** 当前页明细。 */
    private List<OutpatientConsumableUsageDetailRowDto> records;

    /** 总行数。 */
    private long total;

    /** 页码（1 基）。 */
    private int pageNo;

    /** 页大小。 */
    private int pageSize;
}
