/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊挂号明细报表读常量（AS-RPT-007）。
 */
public interface OutpatientRegistrationReportConstants {

    /** 汇总视角：按挂号科室 × 号源类型（领域 {@code summaryDimension}）。 */
    String SUMMARY_DIMENSION_ORGANIZATION = "ORGANIZATION";

    /** 汇总视角：按挂号医生 × 号源类型。 */
    String SUMMARY_DIMENSION_DOCTOR = "DOCTOR";

    /**
     * 应用层汇总视角：按科室（AS-SDD {@code summaryView}；映射为 {@link #SUMMARY_DIMENSION_ORGANIZATION}）。
     */
    String SUMMARY_VIEW_DEPARTMENT = "DEPARTMENT";

    /** 应用层汇总视角：按医生（与 {@link #SUMMARY_DIMENSION_DOCTOR} 同码）。 */
    String SUMMARY_VIEW_DOCTOR = "DOCTOR";

    /** 导出 Tab：汇总。 */
    String PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /** 导出 Tab：明细。 */
    String PRESENTATION_MODE_DETAIL = "DETAIL";

    /** 明细默认页大小。 */
    int DEFAULT_PAGE_SIZE = 20;

    /** 明细分页/导出上限。 */
    int MAX_PAGE_SIZE = 100000;

    /** 导出行数上限。 */
    int EXPORT_MAX_ROWS = 50000;

    /** 导出 xlsx MIME。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 汇总导出 sheet 名。 */
    String EXPORT_SHEET_SUMMARY = "门诊挂号汇总";

    /** 明细导出 sheet 名。 */
    String EXPORT_SHEET_DETAIL = "门诊挂号明细";

    /** 汇总导出文件名前缀。 */
    String EXPORT_FILE_PREFIX_SUMMARY = "门诊挂号汇总_";

    /** 明细导出文件名前缀。 */
    String EXPORT_FILE_PREFIX_DETAIL = "门诊挂号明细_";

    /** 初始化失败（IF-RPT-ORG-01）。 */
    String EX_RPT_ORG_101 = "EX-RPT-ORG-101";

    /** 日期或筛选项非法（IF-RPT-ORG-02～04）。 */
    String EX_RPT_ORG_102 = "EX-RPT-ORG-102";

    /** 查询失败（IF-RPT-ORG-02/03）。 */
    String EX_RPT_ORG_103 = "EX-RPT-ORG-103";

    /** 导出失败（IF-RPT-ORG-04）。 */
    String EX_RPT_ORG_104 = "EX-RPT-ORG-104";
}
