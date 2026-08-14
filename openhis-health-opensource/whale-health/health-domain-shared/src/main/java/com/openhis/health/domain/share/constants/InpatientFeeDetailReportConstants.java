/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院费用明细报表常量（AS-RPT-009 IF-RPT-IFD-01～04）。
 */
public interface InpatientFeeDetailReportConstants {

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

    /** 结算状态筛：全部（已结 + 待结算）。 */
    String SETTLEMENT_STATUS_ALL = "ALL";

    /** 结算状态筛：已结算。 */
    String SETTLEMENT_STATUS_SETTLED = "SETTLED";

    /** 结算状态筛：待结算。 */
    String SETTLEMENT_STATUS_PENDING = "PENDING_SETTLEMENT";

    /** 结算状态筛文案：全部。 */
    String SETTLEMENT_STATUS_ALL_LABEL = "全部";

    /** 结算状态筛文案：已结算。 */
    String SETTLEMENT_STATUS_SETTLED_LABEL = "已结算";

    /** 结算状态筛文案：待结算。 */
    String SETTLEMENT_STATUS_PENDING_LABEL = "待结算";

    /** 导出行数上限。 */
    int EXPORT_MAX_ROWS = 50000;

    /** 导出 xlsx MIME 类型。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 明细导出工作表名。 */
    String EXPORT_SHEET_NAME_DETAIL = "住院费用明细";

    /** 汇总导出工作表名。 */
    String EXPORT_SHEET_NAME_SUMMARY = "住院费用汇总";

    /** 收费员下拉预加载条数上限。 */
    int CASHIER_OPTION_PAGE_SIZE = 500;

    /** 全量扫描就诊块时的分页尺寸。 */
    int ENCOUNTER_BLOCK_SCAN_PAGE_SIZE = 1000;

    /** 未登录或主数据加载失败（IF-RPT-IFD-01）。 */
    String EX_RPT_IFD_101 = "EX-RPT-IFD-101";

    /** 日期区间或分页/Tab 参数非法（IF-RPT-IFD-02～04）。 */
    String EX_RPT_IFD_102 = "EX-RPT-IFD-102";

    /** 查询失败或数据不完整（IF-RPT-IFD-02/03）。 */
    String EX_RPT_IFD_103 = "EX-RPT-IFD-103";

    /** 导出失败（IF-RPT-IFD-04）。 */
    String EX_RPT_IFD_104 = "EX-RPT-IFD-104";
}
