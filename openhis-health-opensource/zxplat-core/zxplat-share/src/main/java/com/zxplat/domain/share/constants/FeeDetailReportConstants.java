/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 费用明细报表汇总维度常量（AS-RPT-008 / AS-RPT-009 共用）。
 *
 * <p>汇总 Tab 支持按费用类型 / 开立科室 / 费用大类 / 品规分桶；默认 {@link #SUMMARY_DIMENSION_FIN_TYPE}。</p>
 */
public interface FeeDetailReportConstants {

    /** 汇总维度：按财务类型（fin_type）。 */
    String SUMMARY_DIMENSION_FIN_TYPE = "FIN_TYPE";

    /** 汇总维展示：按费用类型。 */
    String SUMMARY_DIMENSION_FIN_TYPE_LABEL = "按费用类型";

    /** 汇总维度：按开立科室（ChargeItem.requestingOrgId）。 */
    String SUMMARY_DIMENSION_DEPARTMENT = "DEPARTMENT";

    /** 汇总维展示：按科室。 */
    String SUMMARY_DIMENSION_DEPARTMENT_LABEL = "按科室";

    /** 汇总维度：按费用大类（service_table → MEDICATION/ACTIVITY/DEVICE/REGISTER）。 */
    String SUMMARY_DIMENSION_FEE_CATEGORY = "FEE_CATEGORY";

    /** 汇总维展示：按费用大类。 */
    String SUMMARY_DIMENSION_FEE_CATEGORY_LABEL = "按费用大类";

    /** 汇总维度：按品规/项目（definitionId）。 */
    String SUMMARY_DIMENSION_ITEM = "ITEM";

    /** 汇总维展示：按品规。 */
    String SUMMARY_DIMENSION_ITEM_LABEL = "按品规";

    /** 默认汇总维度（费用类型）。 */
    String DEFAULT_SUMMARY_DIMENSION = SUMMARY_DIMENSION_FIN_TYPE;

    /** 维度键回落：开立科室为空或费用大类无法归类。 */
    String UNKNOWN_DIMENSION_KEY = "UNKNOWN";

    /** 开立科室为空时的展示名。 */
    String UNKNOWN_DEPARTMENT_DISPLAY = "未指定科室";

    /** service_table 无法归类时的展示名。 */
    String UNKNOWN_FEE_CATEGORY_DISPLAY = "未分类";
}
