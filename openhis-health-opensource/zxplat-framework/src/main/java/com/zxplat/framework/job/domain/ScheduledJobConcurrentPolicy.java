/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.domain;

/**
 * 同一任务是否允许并发执行。
 */
public enum ScheduledJobConcurrentPolicy {

    /** 允许并发（多个触发可同时运行） */
    ALLOW("ALLOW", "允许并发"),

    /** 禁止并发（上一次未结束时跳过或等待，由 Quartz 互斥保证） */
    DISALLOW("DISALLOW", "禁止并发");

    private final String code;
    private final String label;

    ScheduledJobConcurrentPolicy(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按 code 解析枚举，未知值返回 null。
     */
    public static ScheduledJobConcurrentPolicy fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ScheduledJobConcurrentPolicy item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
