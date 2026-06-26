package com.openhis.chs.domain.platformdict;

/** 1901 平台字典常量（type 取自基线规范「字典表」章节括号内英文代码）。 */
public final class PlatformDictConstants {

    /** 医疗类别 — {@code ## 医疗类别(med_type)} */
    public static final String DICT_TYPE_MED_TYPE = "med_type";

    /** 险种类型 — {@code ## 险种类型(insutype)} */
    public static final String DICT_TYPE_INSUTYPE = "insutype";

    /** 人员类别 — {@code ## 人员类别(psn_type)} */
    public static final String DICT_TYPE_PSN_TYPE = "psn_type";

    /** 性别 — {@code ## 性别(gend)} */
    public static final String DICT_TYPE_GEND = "gend";

    /** 就诊凭证类型 — {@code ## 就诊凭证类型(mdtrt_cert_type)} */
    public static final String DICT_TYPE_MDTRT_CERT_TYPE = "mdtrt_cert_type";

    /** 支付地点 — {@code ## 支付地点(pay_loc)} */
    public static final String DICT_TYPE_PAY_LOC = "pay_loc";

    /** 清算类别 — {@code ## 清算类别(clr_type)} */
    public static final String DICT_TYPE_CLR_TYPE = "clr_type";

    /** 目录类别 — {@code ## 目录类别(list_type)} */
    public static final String DICT_TYPE_LIST_TYPE = "list_type";

    /** 诊断类别 — {@code ## 诊断类别(diag_type)} */
    public static final String DICT_TYPE_DIAG_TYPE = "diag_type";

    /** 1901 同步/查询默认 type。 */
    public static final String DEFAULT_DICT_TYPE = DICT_TYPE_MED_TYPE;

    /**
     * 本地参保地 label 查找桶（1101 字段 {@code insuplc_admdvs}；非国标字典表 1901 type）。
     * 国标字典表未定义医保区划 type，参保地展示依赖局点扩展或手工维护。
     */
    public static final String LOOKUP_INSUPLC_ADMDVS = "insuplc_admdvs";

    public static final int DEFAULT_PAGE_SIZE = 500;

    public static final String SYNC_STATUS_SUCCESS = "SUCCESS";
    public static final String SYNC_STATUS_FAILED = "FAILED";

    /** 1901 入参有效标志 — 基线规范字段名 {@code Vali_Flag}。 */
    public static final String VALI_FLAG_KEY_SPEC = "Vali_Flag";

    /** 部分局点实际接受小写 {@code vali_flag}。 */
    public static final String VALI_FLAG_KEY = "vali_flag";

    private PlatformDictConstants() {
    }
}
