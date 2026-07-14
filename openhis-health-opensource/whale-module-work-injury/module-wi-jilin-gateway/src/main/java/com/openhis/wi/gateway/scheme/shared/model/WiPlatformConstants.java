/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.shared.model;

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

    /** 2204 默认三大目录类别（医疗服务）。 */
    public static final String DEFAULT_LIST_TYPE = "201";

    /** 2204 是否最小计量单位：否。 */
    public static final String MIN_UNIT_NO = "0";

    /** 9102 查询窗口：开始日期 = 今天 -30 天。 */
    public static final int QUERY_START_DAYS_BEFORE = 30;

    /** 2301 endDate 空时默认 +30 天。 */
    public static final int APPROVAL_DEFAULT_DAYS = 30;

    /** 联系电话缺省（手册允许传 0）。 */
    public static final String DEFAULT_TEL = "0";

    /** 1320/1321/1322 统筹区（院点 NEUSI 220100）。 */
    public static final String DEFAULT_SOCIAL_POOL_CODE = "220100";

    private WiPlatformConstants() {
    }
}
