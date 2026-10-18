/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 耗材使用报表常量（AS-RPT-005 / AS-RPT-006）。
 *
 * <p>汇总主维对齐发药报表：耗材房 / 品规 / 类型 / 科室；默认 {@link #SUMMARY_DIMENSION_LOCATION}。
 * 科室领用侧领域仍用 {@link DeptIssueReportConstants#SUMMARY_DIMENSION_PHARMACY}，
 * 应用层在 LOCATION 维下 remap 后统一对外 {@code LOCATION:} 前缀。</p>
 *
 * <p>科室领用侧见 {@link DeptIssueReportConstants}；应用层按单源编排，禁止 ALL 并集。</p>
 */
public interface ConsumableUsageReportConstants {

    /**
     * 汇总维度：按患者（遗留码；不入 init 下拉，默认不再回落此维）。
     *
     * @deprecated 产品已收敛为耗材房/品规/类型/科室主维；保留仅兼容旧请求
     */
    @Deprecated
    String SUMMARY_DIMENSION_PATIENT = "PATIENT";

    /** 汇总维度：按开单/就诊科室（应用层预解析 encounter→organization）。 */
    String SUMMARY_DIMENSION_DEPARTMENT = "DEPARTMENT";

    /** 汇总维度：按耗材房（发放/领用库位；默认维）。 */
    String SUMMARY_DIMENSION_LOCATION = "LOCATION";

    /** 汇总维度：按耗材品规。 */
    String SUMMARY_DIMENSION_ITEM = "ITEM";

    /** 汇总维度：按耗材类型（DeviceDefinition.categoryCode；应用层由 ITEM 再分桶）。 */
    String SUMMARY_DIMENSION_CATEGORY = "CATEGORY";

    /** 汇总维展示：按耗材房。 */
    String SUMMARY_DIMENSION_LOCATION_LABEL = "按耗材房";

    /** 汇总维展示：按品规。 */
    String SUMMARY_DIMENSION_ITEM_LABEL = "按品规";

    /** 汇总维展示：按类型。 */
    String SUMMARY_DIMENSION_CATEGORY_LABEL = "按类型";

    /** 汇总维展示：按科室。 */
    String SUMMARY_DIMENSION_DEPARTMENT_LABEL = "按科室";

    /** 明细来源：患者发放（DeviceDispense）。 */
    String SOURCE_CODE_PATIENT_DISPENSE = "PATIENT_DISPENSE";

    /** 明细来源：科室领用/退领（Supply DEPT_ISSUE / DEPT_ISSUE_RETURN）。 */
    String SOURCE_CODE_DEPT_ISSUE = "DEPT_ISSUE";

    /** 明细来源筛：两路并集（已禁止；Gate 遇此码抛「禁止全部来源混查」）。 */
    String SOURCE_CODE_ALL = "ALL";

    /**
     * 记录类型筛：全部（已发 ∪ 已退）；码值对齐发药报表 {@code PharmacyDispenseRecordConstants#RECORD_TYPE_ALL}。
     */
    String RECORD_TYPE_ALL = "ALL";

    /** 记录类型筛：仅发放（COMPLETED / PART_COMPLETED）。 */
    String RECORD_TYPE_DISPENSE = "DISPENSE";

    /** 记录类型筛：仅退发（REFUNDED）。 */
    String RECORD_TYPE_REFUND = "REFUND";

    /** 记录类型展示：全部。 */
    String RECORD_TYPE_ALL_LABEL = "全部";

    /** 记录类型展示：发放。 */
    String RECORD_TYPE_DISPENSE_LABEL = "发放";

    /** 记录类型展示：退发。 */
    String RECORD_TYPE_REFUND_LABEL = "退发";

    /**
     * 明细分页行合并模式：按发放行平铺（默认；不合并）。
     */
    String LINE_MERGE_MODE_LINE = "LINE";

    /**
     * 明细分页行合并模式：按就诊+耗材定义[+单位]合并净量（仅患者发放源生效）。
     *
     * <p>科室领用源传此模式时应用层按 LINE 平铺，不抛错。</p>
     */
    String LINE_MERGE_MODE_BY_DEFINITION = "BY_DEFINITION";

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
