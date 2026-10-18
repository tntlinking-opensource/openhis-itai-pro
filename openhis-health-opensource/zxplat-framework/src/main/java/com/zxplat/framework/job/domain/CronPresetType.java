/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.job.domain;

/**
 * 面向运维人员的 Cron 可视化预设类型。
 */
public enum CronPresetType {

    /** 每 N 分钟执行一次 */
    EVERY_N_MINUTES("EVERY_N_MINUTES", "每 N 分钟"),

    /** 每小时指定分钟执行 */
    HOURLY("HOURLY", "每小时"),

    /** 每天指定时刻执行 */
    DAILY("DAILY", "每天"),

    /** 每周指定星期与时刻执行 */
    WEEKLY("WEEKLY", "每周"),

    /** 每月指定日期与时刻执行 */
    MONTHLY("MONTHLY", "每月"),

    /** 直接填写 Cron 表达式 */
    CUSTOM("CUSTOM", "自定义 Cron");

    private final String code;
    private final String label;

    CronPresetType(String code, String label) {
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
    public static CronPresetType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CronPresetType item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
