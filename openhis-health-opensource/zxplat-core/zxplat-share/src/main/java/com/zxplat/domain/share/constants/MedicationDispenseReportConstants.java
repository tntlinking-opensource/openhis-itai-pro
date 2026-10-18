/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 发药报表（AS-RPT-003 / AS-RPT-004）汇总维度与来源筛选项。
 *
 * <p>患者侧发放净汇总维度；科室侧领用汇总见 {@link DeptIssueReportConstants}。</p>
 */
public interface MedicationDispenseReportConstants {

    /** 汇总维度：按发药药房（{@code location_id}）。 */
    String SUMMARY_DIMENSION_PHARMACY = "PHARMACY";

    /** 汇总维度：按药品品规（{@code medication_id}）。 */
    String SUMMARY_DIMENSION_ITEM = "ITEM";

    /** 汇总维度：按药品类型（应用层预解析 {@code categoryCodeByMedicationId}）。 */
    String SUMMARY_DIMENSION_CATEGORY = "CATEGORY";

    /** 来源：全部（已禁止；Gate 遇此码抛「禁止全部来源混查」）。 */
    String SOURCE_ALL = "ALL";

    /** 来源：患者发退药。 */
    String SOURCE_PATIENT_DISPENSE = "PATIENT_DISPENSE";

    /** 来源：科室领用。 */
    String SOURCE_DEPT_ISSUE = "DEPT_ISSUE";

    /** 汇总维度：按领用科室（应用层合并科室侧；与 {@link DeptIssueReportConstants#SUMMARY_DIMENSION_DEPARTMENT} 同码）。 */
    String SUMMARY_DIMENSION_DEPARTMENT = "DEPARTMENT";

    /** 来源展示：患者发药。 */
    String SOURCE_PATIENT_DISPENSE_LABEL = "患者发药";

    /** 来源展示：科室领用。 */
    String SOURCE_DEPT_ISSUE_LABEL = "科室领用";

    /** 来源展示：全部。 */
    String SOURCE_ALL_LABEL = "全部";

    /** 汇总维度展示：药房。 */
    String SUMMARY_DIMENSION_PHARMACY_LABEL = "按药房";

    /** 汇总维度展示：品规。 */
    String SUMMARY_DIMENSION_ITEM_LABEL = "按品规";

    /** 汇总维度展示：药品类型。 */
    String SUMMARY_DIMENSION_CATEGORY_LABEL = "按药品类型";

    /** 汇总维度展示：科室。 */
    String SUMMARY_DIMENSION_DEPARTMENT_LABEL = "按科室";

    /** 导出呈现：汇总 Tab。 */
    String PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /** 导出呈现：明细 Tab。 */
    String PRESENTATION_MODE_DETAIL = "DETAIL";
}
