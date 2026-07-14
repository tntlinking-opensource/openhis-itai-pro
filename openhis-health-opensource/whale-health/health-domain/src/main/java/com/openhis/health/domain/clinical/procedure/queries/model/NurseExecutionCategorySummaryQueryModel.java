/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 护士诊疗执行工作量按 {@code ServiceRequest.category_enum} 汇总行（AS-RPT-002 IF-RPT-NWL-02）。
 */
@Getter
@Setter
public class NurseExecutionCategorySummaryQueryModel {

    /** 诊疗类目存储码（DM-WKF-004 §4.3；空表示 SR 未 enrich 到类目）。 */
    private String categoryEnum;

    /** 净 COMPLETED 执行次数（不含被 CANCEL 对冲行；BR-RPT-NWL-004）。 */
    private long executionCount;
}
