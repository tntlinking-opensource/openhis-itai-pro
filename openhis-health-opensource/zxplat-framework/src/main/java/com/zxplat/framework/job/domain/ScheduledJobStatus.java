/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.domain;

/**
 * 定时任务运行状态（存库与接口均使用 {@link #getCode()}）。
 */
public enum ScheduledJobStatus {

    /** 正常运行，按 Cron 调度 */
    NORMAL("NORMAL", "运行中"),

    /** 已暂停，不再触发 */
    PAUSE("PAUSE", "已暂停");

    private final String code;
    private final String label;

    ScheduledJobStatus(String code, String label) {
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
    public static ScheduledJobStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ScheduledJobStatus item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
