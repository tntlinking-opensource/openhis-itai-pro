/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院患者入出明细报表读常量（AS-RPT-010）。
 */
public interface InpatientInOutDetailReportConstants {

    /** 入出状态：全部（区间内入院 ∪ 区间内出院）。 */
    String MOVEMENT_STATUS_ALL = "ALL";

    /** 入出状态：仅入院（入院日 ∈ 窗）。 */
    String MOVEMENT_STATUS_ADMISSION = "ADMISSION";

    /** 入出状态：仅出院（出院日 ∈ 窗）。 */
    String MOVEMENT_STATUS_DISCHARGE = "DISCHARGE";

    /** 明细分页默认页大小。 */
    int DEFAULT_PAGE_SIZE = 20;

    /**
     * 默认业务日期回看月数：初始化日起 = 今日往前 N 个自然月（含今日，闭区间）。
     * <p>例：今日 2026-08-14 且本值为 1 → 默认窗 2026-07-14～2026-08-14。</p>
     */
    int DEFAULT_LOOKBACK_MONTHS = 1;

    /** 明细分页上限。 */
    int MAX_PAGE_SIZE = 100000;

    /** 导出行数上限。 */
    int EXPORT_MAX_ROWS = 50000;

    /** 导出批次页大小。 */
    int EXPORT_PAGE_SIZE = 500;

    /** 导出 xlsx MIME。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出 sheet 名。 */
    String EXPORT_SHEET_NAME = "住院患者入出明细";

    /** 导出文件名前缀。 */
    String EXPORT_FILE_PREFIX = "住院患者入出明细_";

    /** 初始化/病区选项失败（IF-RPT-IOE-01/01a）。 */
    String EX_RPT_IOE_101 = "EX-RPT-IOE-101";

    /** 日期或筛选项非法（IF-RPT-IOE-02/03）。 */
    String EX_RPT_IOE_102 = "EX-RPT-IOE-102";

    /** 查询失败（IF-RPT-IOE-02）。 */
    String EX_RPT_IOE_103 = "EX-RPT-IOE-103";

    /** 导出失败（IF-RPT-IOE-03）。 */
    String EX_RPT_IOE_104 = "EX-RPT-IOE-104";
}
