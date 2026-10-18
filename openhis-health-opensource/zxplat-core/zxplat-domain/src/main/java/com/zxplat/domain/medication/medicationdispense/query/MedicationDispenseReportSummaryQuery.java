/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.query;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 发药报表患者侧净汇总查询（AS-RPT-003 / AS-RPT-004 · IF 汇总；仅 MedicationDispense）。
 *
 * <p>筛选口径与 {@link MedicationDispenseRecordPageQuery} 一致；跨聚合类型码由应用层写入
 * {@link #categoryCodeByMedicationId}。科室领用净汇总在 Supply 聚合，不在本 Query。</p>
 */
@Getter
@Setter
public class MedicationDispenseReportSummaryQuery extends MedicationDispenseRecordPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 汇总维度：{@link com.zxplat.domain.share.constants.MedicationDispenseReportConstants#SUMMARY_DIMENSION_PHARMACY}
     * / {@code ITEM} / {@code CATEGORY}；空则按药房。
     */
    private String summaryDimension;

    /**
     * 药品主键 → {@code medication_category_code}；维度为 CATEGORY 时必填（应用层预解析）。
     * <p>缺失映射的品规落入空类型桶，仍计入净量，避免静默丢行。</p>
     */
    private Map<Long, String> categoryCodeByMedicationId;
}
