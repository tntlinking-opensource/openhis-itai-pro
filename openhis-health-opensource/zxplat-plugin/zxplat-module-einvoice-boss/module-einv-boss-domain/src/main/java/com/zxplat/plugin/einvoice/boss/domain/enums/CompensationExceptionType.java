/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.enums;

/**
 * 待补偿异常类型（与 UI 标签对齐）。
 */
public enum CompensationExceptionType {

    /** 事件丢失：DRAFT 且无 task。 */
    LOST("LOST"),

    /** 金额待调整：FAILED + EINV_AMOUNT_MISMATCH。 */
    AMOUNT("AMOUNT"),

    /** 开票失败：FAILED + 其他错误。 */
    FAILED("FAILED"),

    /** 超时待查：DRAFT 超 staleMinutes 未 ISSUED。 */
    STALE("STALE");

    private final String code;

    CompensationExceptionType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
