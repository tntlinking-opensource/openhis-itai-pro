/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.shared.model;

/**
 * 吉林工伤出站报文定点常量（开源样例值，部署时按院点覆盖）。
 */
public final class WiBusConstants {

    /** DLL 出站 fixmedins_code（与院内 H00000000000 展示码不同）。 */
    public static final String FIXMEDINS_CODE = "JL0000000001";

    /** 接收方系统编码。 */
    public static final String RECER_SYS_CODE = "LNYTH";

    /** fin_contract bus_no（工伤契约）。 */
    public static final String CONTRACT_BUS_NO = "H00000000000-GS";

    /** 方案码写入 yb_invoke_log / yb_settlement_record。 */
    public static final String SCHEME_CODE = "WORK_INJURY";

    private WiBusConstants() {
    }
}

