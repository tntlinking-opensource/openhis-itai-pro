/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊费用明细报表常量（AS-RPT-008 IF-RPT-OFD-01～04）。
 */
public interface OutpatientFeeDetailReportConstants {

    /** 呈现模式：费用类型净汇总 Tab。 */
    String PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /** 呈现模式：就诊块明细 Tab。 */
    String PRESENTATION_MODE_DETAIL = "DETAIL";

    /** 汇总 Tab 展示文案。 */
    String PRESENTATION_MODE_SUMMARY_LABEL = "汇总";

    /** 明细 Tab 展示文案。 */
    String PRESENTATION_MODE_DETAIL_LABEL = "明细";

    /** 默认激活 Tab（汇总）。 */
    String DEFAULT_ACTIVE_TAB = PRESENTATION_MODE_SUMMARY;

    /** 默认分页大小（就诊块数/页）。 */
    int DEFAULT_PAGE_SIZE = 10;

    /** 导出行数上限。 */
    int EXPORT_MAX_ROWS = 50000;

    /** 导出 xlsx MIME 类型。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 明细导出工作表名。 */
    String EXPORT_SHEET_NAME_DETAIL = "门诊费用明细";

    /** 汇总导出工作表名。 */
    String EXPORT_SHEET_NAME_SUMMARY = "门诊费用汇总";

    /** 未登录或主数据加载失败（IF-RPT-OFD-01）。 */
    String EX_RPT_OFD_101 = "EX-RPT-OFD-101";

    /** 日期区间或分页/Tab 参数非法（IF-RPT-OFD-02～04）。 */
    String EX_RPT_OFD_102 = "EX-RPT-OFD-102";

    /** 查询失败或数据不完整（IF-RPT-OFD-02/03）。 */
    String EX_RPT_OFD_103 = "EX-RPT-OFD-103";

    /** 导出失败（IF-RPT-OFD-04）。 */
    String EX_RPT_OFD_104 = "EX-RPT-OFD-104";
}
