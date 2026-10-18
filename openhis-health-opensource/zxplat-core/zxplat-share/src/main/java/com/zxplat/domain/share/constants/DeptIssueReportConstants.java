/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 科室领用/退领报表读侧常量（AS-RPT-003～006 科室侧）。
 *
 * <p>行按 {@code item_table} 区分药品与耗材；状态仅已同意。</p>
 */
public interface DeptIssueReportConstants {

    /** 汇总维度：按领用科室（应用层预解析 {@code organizationIdByPurposeLocationId}）。 */
    String SUMMARY_DIMENSION_DEPARTMENT = "DEPARTMENT";

    /** 汇总维度：按品规（{@code item_id}）。 */
    String SUMMARY_DIMENSION_ITEM = "ITEM";

    /** 汇总维度：按科室药房（{@code purpose_location_id}）。 */
    String SUMMARY_DIMENSION_PHARMACY = "PHARMACY";

    /** 明细默认页大小。 */
    int DEFAULT_PAGE_SIZE = 20;

    /** 明细分页/导出上限（与发退药记录导出上限同级防护）。 */
    int MAX_PAGE_SIZE = 100000;
}
