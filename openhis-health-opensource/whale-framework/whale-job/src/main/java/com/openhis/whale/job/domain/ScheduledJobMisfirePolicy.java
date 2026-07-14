/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.domain;

/**
 * Cron 触发错过（Misfire）时的处理策略。
 */
public enum ScheduledJobMisfirePolicy {

    /** 使用 Quartz 默认策略 */
    DEFAULT("DEFAULT", "默认"),

    /** 立即补跑所有错过的触发 */
    IGNORE_MISFIRES("IGNORE_MISFIRES", "立即补跑"),

    /** 触发一次后继续按计划执行 */
    FIRE_AND_PROCEED("FIRE_AND_PROCEED", "触发一次"),

    /** 忽略错过的触发，等待下一次 */
    DO_NOTHING("DO_NOTHING", "放弃补跑");

    private final String code;
    private final String label;

    ScheduledJobMisfirePolicy(String code, String label) {
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
    public static ScheduledJobMisfirePolicy fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ScheduledJobMisfirePolicy item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
