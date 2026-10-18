/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 门诊处方点评应用编排语义码与导出常量（AS-OP-PHA-007 §5）。
 */
public interface OutpatientPharmacyPrescriptionReviewConstants {

    /** 未登录或初始化失败（EX-PRV-101）。 */
    String EX_PRV_101 = "EX-PRV-101";

    /** 分页或时间窗非法（EX-PRV-102）。 */
    String EX_PRV_102 = "EX-PRV-102";

    /** 查询失败（EX-PRV-103）。 */
    String EX_PRV_103 = "EX-PRV-103";

    /** 导出失败（EX-PRV-104）。 */
    String EX_PRV_104 = "EX-PRV-104";

    /** 写入口参数非法（EX-PRV-200）。 */
    String EX_PRV_200 = "EX-PRV-200";

    /** 处方未发药不可点评（EX-PRV-201）。 */
    String EX_PRV_201 = "EX-PRV-201";

    /** 已存在有效点评（EX-PRV-202）。 */
    String EX_PRV_202 = "EX-PRV-202";

    /** 不合理但未填原因（EX-PRV-203）。 */
    String EX_PRV_203 = "EX-PRV-203";

    /** 撤销目标不存在（EX-PRV-204）。 */
    String EX_PRV_204 = "EX-PRV-204";

    /** 自动点评写库失败（EX-PRV-205）。 */
    String EX_PRV_205 = "EX-PRV-205";

    /** 导出行数上限。 */
    int EXPORT_MAX_ROWS = 10000;

    /** Office Open XML 电子表格 MIME。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 处方点评记录 Excel 工作表名。 */
    String EXPORT_SHEET_NAME = "处方点评记录";

    /** 处方点评记录默认导出文件名。 */
    String EXPORT_DEFAULT_FILENAME = "处方点评记录.xlsx";

    /** 是否合理选项：合理。 */
    String REASONABLE_TRUE_LABEL = "合理";

    /** 是否合理选项：不合理。 */
    String REASONABLE_FALSE_LABEL = "不合理";

    /** 合理选项 value（字符串化 true）。 */
    String REASONABLE_TRUE_VALUE = "true";

    /** 不合理选项 value（字符串化 false）。 */
    String REASONABLE_FALSE_VALUE = "false";

    /** Gate 按处方号拉取已签发行的页大小（单方明细通常远小于此）。 */
    int GATE_PRESCRIPTION_LINE_PAGE_SIZE = 500;

    /** 批量映射有效点评建议上限（软提示口径，不强制截断）。 */
    int MAP_ACTIVE_SUGGESTED_MAX = 200;
}
