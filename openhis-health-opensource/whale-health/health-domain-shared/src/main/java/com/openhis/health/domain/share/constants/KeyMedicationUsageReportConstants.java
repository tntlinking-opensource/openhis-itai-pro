/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 重点药品使用统计报表常量（AS-RPT-011）。
 */
public interface KeyMedicationUsageReportConstants {

    /** 导出 xlsx MIME。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出 sheet 名。 */
    String EXPORT_SHEET_NAME = "重点药品使用统计";

    /** 导出文件名前缀。 */
    String EXPORT_FILE_PREFIX = "重点药品使用统计_";

    /** 初始化失败（IF-RPT-KDU-01）。 */
    String EX_RPT_KDU_101 = "EX-RPT-KDU-101";

    /** 日期或筛选项非法（IF-RPT-KDU-02/03）。 */
    String EX_RPT_KDU_102 = "EX-RPT-KDU-102";

    /** 查询失败（IF-RPT-KDU-02）。 */
    String EX_RPT_KDU_103 = "EX-RPT-KDU-103";

    /** 导出失败（IF-RPT-KDU-03）。 */
    String EX_RPT_KDU_104 = "EX-RPT-KDU-104";
}
