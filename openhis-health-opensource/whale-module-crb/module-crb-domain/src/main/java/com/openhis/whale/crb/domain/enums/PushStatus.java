/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.enums;

/**
 * 推送任务队列状态。
 */
public enum PushStatus {

    /** 待消费。 */
    PENDING("PENDING"),

    /** 已成功 POST/DELETE。 */
    SENT("SENT"),

    /** 失败待重试。 */
    FAILED("FAILED"),

    /** 超过重试上限，需人工介入。 */
    DEAD("DEAD");

    private final String code;

    PushStatus(String code) {
        this.code = code;
    }

    /**
     * 持久化使用的字符串身份。
     *
     * @return 状态 code
     */
    public String getCode() {
        return code;
    }
}
