/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.shared.model;

/**
 * invoke 会话状态（{@code yb_wi_invoke_session.status}）。
 */
public enum WiInvokeSessionStatus {

    /** 进行中，可 prepare/submit。 */
    ACTIVE,
    /** 本 phase 全部 step 完成。 */
    COMPLETED,
    /** Win/DLL 失败或业务校验失败。 */
    FAILED;

    public String getCode() {
        return name();
    }

    /**
     * 从持久化字符串解析会话状态。
     *
     * @param code status 码
     * @return 枚举；非法时 null
     */
    public static WiInvokeSessionStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return valueOf(code.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
