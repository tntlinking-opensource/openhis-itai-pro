/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 打印审计日志结果状态（DM-PRT-002 §4.1 · {@code prt_print_event_log.status}）。
 */
@Getter
public enum PrintLogStatus implements EnumInterface {

    /** confirmPrint 成功（渲染 + 提交打印闭环完成）。 */
    SUCCESS(0L, "SUCCESS", "成功", "", 1L),

    /** 渲染、Host 打印或 confirm 前失败。 */
    FAILED(0L, "FAILED", "失败", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PrintLogStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 判断状态码是否与当前枚举值匹配。
     *
     * @param statusCode 库表 {@code status} 列值
     * @return 匹配返回 {@code true}
     */
    public boolean sameAs(String statusCode) {
        return this.code.equals(statusCode);
    }

    /**
     * 是否为合法落库状态码。
     *
     * @param statusCode 待校验状态码
     * @return 合法返回 {@code true}
     */
    public static boolean isValidCode(String statusCode) {
        if (statusCode == null) {
            return false;
        }
        for (PrintLogStatus value : values()) {
            if (value.sameAs(statusCode)) {
                return true;
            }
        }
        return false;
    }
}
