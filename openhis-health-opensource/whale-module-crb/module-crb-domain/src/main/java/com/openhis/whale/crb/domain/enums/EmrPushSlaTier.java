/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.enums;

/**
 * EMR 推送 SLA 分档（与国标 receive 时效要求对齐）。
 */
public enum EmrPushSlaTier {

    /** 实时档：诊断/患者等，目标 3 分钟内 POST。 */
    REALTIME("REALTIME"),

    /** 2 小时档：检验报告等。 */
    TWO_HOUR("TWO_HOUR"),

    /** T+0 档：文书/医嘱等，当日 23:59:59 前发出。 */
    T_PLUS_0("T_PLUS_0");

    private final String code;

    EmrPushSlaTier(String code) {
        this.code = code;
    }

    /**
     * 持久化与 API 使用的字符串身份。
     *
     * @return SLA 档 code
     */
    public String getCode() {
        return code;
    }
}
