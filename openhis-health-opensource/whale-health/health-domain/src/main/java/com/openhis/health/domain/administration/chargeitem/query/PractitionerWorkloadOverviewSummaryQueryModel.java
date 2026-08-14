/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 工作量汇总页外显指标：接诊人数、处方/诊疗开立量（AS-RPT-001 IF-RPT-WKL-02）。
 */
@Getter
@Setter
public class PractitionerWorkloadOverviewSummaryQueryModel {

    /** 接诊人数（distinct encounter_id）。 */
    private Integer encounterCountTotal = 0;

    /**
     * 处方数量（按 {@code prescription_no} 去重；同方多行计 1；无处方号回落 request id）。
     */
    private Integer prescriptionCountTotal = 0;

    /** 诊疗项目总数量（distinct 诊疗 + 耗材 request）。 */
    private Integer activityItemCountTotal = 0;
}
