/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.shared.model;

/**
 * 吉林工伤平台报文默认值（V1.3 / 10-P0）。
 */
public final class WiPlatformConstants {

    /** 门诊工伤默认就诊类别（院点确认 med_type=12）。 */
    public static final String DEFAULT_MED_TYPE = "12";

    /** 2301 默认审批类型（院点日志多为 12）。 */
    public static final String DEFAULT_APPROVE_TYPE = "12";

    /** 2206/2207 门诊出院原因默认。 */
    public static final String DEFAULT_DSCG_TRT_RSLT = "01";

    /** 2204 默认三大目录类别（手册：1药品/2诊疗/3材料；诊疗项目）。 */
    public static final String DEFAULT_LIST_TYPE = "2";

    /** 2204 list_type：药品。 */
    public static final String LIST_TYPE_DRUG = "1";

    /** 2204 list_type：诊疗项目。 */
    public static final String LIST_TYPE_SERVICE = "2";

    /** 2204 list_type：材料。 */
    public static final String LIST_TYPE_MATERIAL = "3";

    /** 2204 是否最小计量单位：否。 */
    public static final String MIN_UNIT_NO = "0";

    /** 2204 单次最多明细条数（手册）。 */
    public static final int FEE_UPLOAD_BATCH_SIZE = 50;

    /** 2208 是否保留处方：不保留（退费默认整单撤销）。 */
    public static final String KEEP_TP_TYPE_NO = "0";

    /** 2208 是否保留处方：保留。 */
    public static final String KEEP_TP_TYPE_YES = "1";

    /** 9102 查询窗口：开始日期 = 今天 - 年数（与挂号预览对齐，院点审批跨度常超 30 天）。 */
    public static final int QUERY_START_YEARS_BEFORE = 3;

    /** 2301 endDate 空时默认 +30 天。 */
    public static final int APPROVAL_DEFAULT_DAYS = 30;

    /** 联系电话缺省（手册允许传 0）。 */
    public static final String DEFAULT_TEL = "0";

    /** 1320/1321/1322 统筹区（院点默认 220199）。 */
    public static final String DEFAULT_SOCIAL_POOL_CODE = "220199";

    /** 1320 是否包含退费：是。 */
    public static final String REFD_SETL_FLAG_YES = "1";

    /** 1320 省内/全国异地标志：否。 */
    public static final String YD_SIGN_NO = "0";

    /** 1320 省内/全国异地标志：是。 */
    public static final String YD_SIGN_YES = "1";

    private WiPlatformConstants() {
    }
}
