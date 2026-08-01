/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 耗材使用报表常量（AS-RPT-005 / AS-RPT-006）。
 *
 * <p>科室领用侧见 {@link DeptIssueReportConstants}；两路并集由应用层编排。</p>
 */
public interface ConsumableUsageReportConstants {

    /** 汇总维度：按患者。 */
    String SUMMARY_DIMENSION_PATIENT = "PATIENT";

    /** 汇总维度：按开单/就诊科室（应用层预解析 encounter→organization）。 */
    String SUMMARY_DIMENSION_DEPARTMENT = "DEPARTMENT";

    /** 汇总维度：按发放库位。 */
    String SUMMARY_DIMENSION_LOCATION = "LOCATION";

    /** 汇总维度：按耗材品规。 */
    String SUMMARY_DIMENSION_ITEM = "ITEM";

    /** 明细来源：患者发放（DeviceDispense）。 */
    String SOURCE_CODE_PATIENT_DISPENSE = "PATIENT_DISPENSE";

    /** 明细来源：科室领用/退领（Supply DEPT_ISSUE / DEPT_ISSUE_RETURN）。 */
    String SOURCE_CODE_DEPT_ISSUE = "DEPT_ISSUE";

    /** 明细来源筛：两路并集。 */
    String SOURCE_CODE_ALL = "ALL";

    /** 导出 Tab：汇总。 */
    String EXPORT_TAB_SUMMARY = "SUMMARY";

    /** 导出 Tab：明细。 */
    String EXPORT_TAB_DETAIL = "DETAIL";

    /** 明细默认页大小。 */
    int DEFAULT_PAGE_SIZE = 20;

    /** 明细分页/导出上限。 */
    int MAX_PAGE_SIZE = 100000;

    /** 导出行数上限（与发退药记录同级防护）。 */
    int EXPORT_MAX_ROWS = 5000;

    /** Office Open XML 电子表格 MIME。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 门诊耗材使用明细 Excel 工作表名。 */
    String OUTPATIENT_SHEET_NAME = "门诊耗材使用明细";

    /** 住院耗材使用明细 Excel 工作表名。 */
    String INPATIENT_SHEET_NAME = "住院耗材使用明细";

    /** 门诊耗材使用明细默认导出文件名。 */
    String OUTPATIENT_EXPORT_DEFAULT_FILENAME = "门诊耗材使用明细.xlsx";

    /** 住院耗材使用明细默认导出文件名。 */
    String INPATIENT_EXPORT_DEFAULT_FILENAME = "住院耗材使用明细.xlsx";

    /** 门诊：页面初始化失败（EX-RPT-OCU-101）。 */
    String EX_RPT_OCU_101 = "EX-RPT-OCU-101";

    /** 门诊：日期非法（EX-RPT-OCU-102）。 */
    String EX_RPT_OCU_102 = "EX-RPT-OCU-102";

    /** 门诊：查询失败（EX-RPT-OCU-103）。 */
    String EX_RPT_OCU_103 = "EX-RPT-OCU-103";

    /** 门诊：导出失败（EX-RPT-OCU-104）。 */
    String EX_RPT_OCU_104 = "EX-RPT-OCU-104";

    /** 住院：页面初始化失败（EX-RPT-ICU-101）。 */
    String EX_RPT_ICU_101 = "EX-RPT-ICU-101";

    /** 住院：日期非法（EX-RPT-ICU-102）。 */
    String EX_RPT_ICU_102 = "EX-RPT-ICU-102";

    /** 住院：查询失败（EX-RPT-ICU-103）。 */
    String EX_RPT_ICU_103 = "EX-RPT-ICU-103";

    /** 住院：导出失败（EX-RPT-ICU-104）。 */
    String EX_RPT_ICU_104 = "EX-RPT-ICU-104";
}
