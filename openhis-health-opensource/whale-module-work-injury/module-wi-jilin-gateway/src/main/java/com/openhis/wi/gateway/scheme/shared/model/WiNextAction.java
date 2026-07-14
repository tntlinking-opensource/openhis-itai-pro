/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.shared.model;

/**
 * prepare/submit 返回的下一步动作（前端 invoke 循环驱动）。
 */
public enum WiNextAction {

    /** 调 OpenHIS.Win :5000 invoke。 */
    INVOKE_WIN,
    /** submit 后还有步，需再 prepare。 */
    PREPARE_NEXT,
    /** 本 phase 全部完成。 */
    COMPLETED,
    /** 失败终止。 */
    FAILED;

    public String getCode() {
        return name();
    }

    /**
     * 从响应字符串解析 nextAction。
     *
     * @param code nextAction 码
     * @return 枚举；非法时 null
     */
    public static WiNextAction fromCode(String code) {
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
