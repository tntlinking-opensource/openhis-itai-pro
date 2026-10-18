/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 门诊/住院医保费用明细报表共用常量（AS-RPT-012 / AS-RPT-013）。
 *
 * <p>时间格式复用 {@link GeneralSharedConstants#DATE_TIME_PATTERN}，本接口不重复定义。</p>
 */
public interface InsuranceFeeDetailReportConstants {

    /** 明细默认页大小。 */
    int DEFAULT_PAGE_SIZE = 20;

    /** 初始化默认结算时间窗：今日往前推的自然月数（近一个月）。 */
    int DEFAULT_LOOKBACK_MONTHS = 1;

    /** 姓名模糊：患者主数据命中上限（再反查就诊 id）。 */
    int PATIENT_NAME_MATCH_LIMIT = 500;

    /** 明细分页单页上限。 */
    int MAX_PAGE_SIZE = 100000;

    /** 导出行数上限。 */
    int EXPORT_MAX_ROWS = 50000;

    /** 导出 xlsx MIME。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 门诊导出 sheet 名。 */
    String EXPORT_SHEET_OUTPATIENT = "门诊医保费用明细";

    /** 住院导出 sheet 名。 */
    String EXPORT_SHEET_INPATIENT = "住院医保费用明细";

    /** 门诊导出文件名前缀。 */
    String EXPORT_FILE_PREFIX_OUTPATIENT = "门诊医保费用明细_";

    /** 住院导出文件名前缀。 */
    String EXPORT_FILE_PREFIX_INPATIENT = "住院医保费用明细_";

    /** 费用类别：省医保（{@code ContractCategory.PROVINCIAL_INSURANCE}）。 */
    String CONTRACT_CATEGORY_PROVINCIAL = "PROVINCIAL_INSURANCE";

    /** 费用类别：市医保（{@code ContractCategory.MUNICIPAL_INSURANCE}）。 */
    String CONTRACT_CATEGORY_MUNICIPAL = "MUNICIPAL_INSURANCE";

    /** 工伤结算模式（与 wi-jilin {@code WorkInjuryConstants.SCHEME_CODE} 同码 {@code WORK_INJURY}）。 */
    String SETTLE_MODE_WORK_INJURY = "WORK_INJURY";

    /** 职工/居民分组：全部（不收窄 insutype）。 */
    String INSUTYPE_GROUP_ALL = "ALL";

    /** 职工/居民分组：职工（平台 insutype ∈ 310/320/330）。 */
    String INSUTYPE_GROUP_EMPLOYEE = "EMPLOYEE";

    /** 职工/居民分组：居民（平台 insutype = 390）。 */
    String INSUTYPE_GROUP_RESIDENT = "RESIDENT";

    /** 职工险种：职工基本医疗。 */
    String INSUTYPE_EMPLOYEE_BASIC = "310";

    /** 职工险种：公务员医疗补助。 */
    String INSUTYPE_EMPLOYEE_CIVIL = "320";

    /** 职工险种：大额医疗。 */
    String INSUTYPE_EMPLOYEE_LARGE = "330";

    /** 居民险种：城乡居民基本医疗。 */
    String INSUTYPE_RESIDENT = "390";

    /** 门诊初始化失败（IF-RPT-OIF-01）。 */
    String EX_RPT_OIF_101 = "EX-RPT-OIF-101";

    /** 门诊时间窗/筛选项非法（IF-RPT-OIF-02/03）。 */
    String EX_RPT_OIF_102 = "EX-RPT-OIF-102";

    /** 门诊查询失败（IF-RPT-OIF-02）。 */
    String EX_RPT_OIF_103 = "EX-RPT-OIF-103";

    /** 门诊导出失败（IF-RPT-OIF-03）。 */
    String EX_RPT_OIF_104 = "EX-RPT-OIF-104";

    /** 住院初始化失败（IF-RPT-IIF-01）。 */
    String EX_RPT_IIF_101 = "EX-RPT-IIF-101";

    /** 住院时间窗/筛选项非法（IF-RPT-IIF-02/03）。 */
    String EX_RPT_IIF_102 = "EX-RPT-IIF-102";

    /** 住院查询失败（IF-RPT-IIF-02）。 */
    String EX_RPT_IIF_103 = "EX-RPT-IIF-103";

    /** 住院导出失败（IF-RPT-IIF-03）。 */
    String EX_RPT_IIF_104 = "EX-RPT-IIF-104";
}
