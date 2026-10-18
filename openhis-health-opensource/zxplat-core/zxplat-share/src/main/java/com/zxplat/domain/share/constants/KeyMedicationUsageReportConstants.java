/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

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

    /**
     * 历史「全局合计」就诊类型码（不再写入 {@code encounterClassCode}；保留常量避免外部引用断裂）。
     *
     * <p>分组合计改用 {@link #SUMMARY_KEY_DRUG_TYPE_TOTAL} 写在 {@code keyDrugTypeCode}。</p>
     */
    String SUMMARY_ENCOUNTER_CLASS_TOTAL = "TOTAL";

    /**
     * 「合计」展示名（历史字段名；与 {@link #SUMMARY_KEY_DRUG_TYPE_TOTAL_DISPLAY} 同文案）。
     */
    String SUMMARY_ENCOUNTER_CLASS_TOTAL_DISPLAY = "合计";

    /**
     * 分组「合计」行药品类型码（写在 {@code keyDrugTypeCode}；{@code encounterClassCode} 仍为 AMB/IMP）。
     */
    String SUMMARY_KEY_DRUG_TYPE_TOTAL = "TOTAL";

    /** 分组「合计」行药品类型展示名。 */
    String SUMMARY_KEY_DRUG_TYPE_TOTAL_DISPLAY = "合计";

    /** 使用占比百分比小数位（例 12.34 表示 12.34%）。 */
    int USAGE_RATIO_PERCENT_SCALE = 2;

    /** 页初始化默认回溯自然月数（BR-RPT-KDU-001：近一个月 = 今日往前 1 月～今日）。 */
    int DEFAULT_LOOKBACK_MONTHS = 1;

    /**
     * 兼容旧名：同 {@link #USAGE_RATIO_PERCENT_SCALE}。
     *
     * @deprecated 请使用 {@link #USAGE_RATIO_PERCENT_SCALE}
     */
    @Deprecated
    int BASIC_DRUG_RATIO_PERCENT_SCALE = USAGE_RATIO_PERCENT_SCALE;

    /** 初始化失败（IF-RPT-KDU-01）。 */
    String EX_RPT_KDU_101 = "EX-RPT-KDU-101";

    /** 日期或筛选项非法（IF-RPT-KDU-02/03）。 */
    String EX_RPT_KDU_102 = "EX-RPT-KDU-102";

    /** 查询失败（IF-RPT-KDU-02）。 */
    String EX_RPT_KDU_103 = "EX-RPT-KDU-103";

    /** 导出失败（IF-RPT-KDU-03）。 */
    String EX_RPT_KDU_104 = "EX-RPT-KDU-104";
}
