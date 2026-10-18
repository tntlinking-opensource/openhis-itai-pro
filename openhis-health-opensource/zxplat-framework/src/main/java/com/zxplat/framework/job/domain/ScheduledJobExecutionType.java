/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.domain;

/**
 * 任务触发方式（存库与接口均使用 {@link #getCode()}）。
 */
public enum ScheduledJobExecutionType {

    /** Cron 按计划自动触发 */
    SCHEDULED("SCHEDULED", "计划执行"),

    /** 页面「立即执行」手动触发 */
    MANUAL("MANUAL", "手动执行");

    private final String code;
    private final String label;

    ScheduledJobExecutionType(String code, String label) {
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
    public static ScheduledJobExecutionType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ScheduledJobExecutionType item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
