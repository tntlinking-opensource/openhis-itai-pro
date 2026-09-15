/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 重点药品使用统计：单就诊类型块的一次遍历汇总（类型矩阵 + 全部药品合计）。
 */
@Getter
@Setter
public class KeyMedicationUsageSectionAggQueryModel {

    /** 重点药类型 code → 汇总。 */
    private Map<String, KeyMedicationUsageAggQueryModel> matrixByBucketKey = new LinkedHashMap<>();

    /** 全部药典药品合计（组内 TOTAL 与占比分母）。 */
    private KeyMedicationUsageAggQueryModel allMedicationAgg = new KeyMedicationUsageAggQueryModel();
}
