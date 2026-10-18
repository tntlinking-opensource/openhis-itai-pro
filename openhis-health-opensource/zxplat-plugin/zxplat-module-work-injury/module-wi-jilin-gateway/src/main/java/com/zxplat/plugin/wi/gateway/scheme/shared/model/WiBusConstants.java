/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.shared.model;

/**
 * 吉林工伤出站报文定点常量（院方确认 Q2）。
 */
public final class WiBusConstants {

    /** DLL 出站 fixmedins_code（与院内 H22010402403 展示码不同）。 */
    public static final String FIXMEDINS_CODE = "JL2201992002";

    /** 接收方系统编码。 */
    public static final String RECER_SYS_CODE = "LNYTH";

    /** fin_contract bus_no（工伤契约）。 */
    public static final String CONTRACT_BUS_NO = "H22010402403-GS";

    /** 方案码写入 yb_invoke_log / yb_settlement_record。 */
    public static final String SCHEME_CODE = "WORK_INJURY";

    private WiBusConstants() {
    }
}
