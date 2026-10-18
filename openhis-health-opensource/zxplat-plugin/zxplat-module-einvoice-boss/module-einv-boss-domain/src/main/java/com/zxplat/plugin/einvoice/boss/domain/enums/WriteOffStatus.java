/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.enums;

/**
 * 博思电子票据冲红状态（einv_boss_document.write_off_status）。
 */
public enum WriteOffStatus {

    /** 博思冲红成功。 */
    SUCCESS("SUCCESS"),

    /** 博思冲红失败。 */
    FAILED("FAILED"),

    /** 未开出蓝票，无需调博思冲红。 */
    SKIPPED("SKIPPED"),

    /** 冲红处理中（蓝绿并发门控）。 */
    PROCESSING("PROCESSING");

    private final String code;

    WriteOffStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
