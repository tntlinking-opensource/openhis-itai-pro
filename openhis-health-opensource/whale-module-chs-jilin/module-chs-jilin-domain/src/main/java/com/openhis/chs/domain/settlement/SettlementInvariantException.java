/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement;

/**
 * 结算领域不变量违反（状态跃迁非法、金额恒等式失败等） */
public class SettlementInvariantException extends RuntimeException {

    private final String errorCode;

    public SettlementInvariantException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
