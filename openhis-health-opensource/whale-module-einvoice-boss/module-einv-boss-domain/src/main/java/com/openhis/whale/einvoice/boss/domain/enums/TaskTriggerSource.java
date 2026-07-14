/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.enums;

/**
 * 开票任务触发来源（einv_boss_issue_task.trigger_source）。
 */
public enum TaskTriggerSource {

    /** 支付完成后领域事件自动入队。 */
    EVENT("EVENT"),

    /** 运维/财务在待补偿页手工重试。 */
    MANUAL("MANUAL"),

    /** 定时扫描 DRAFT 无任务兜底补入队。 */
    SCANNER("SCANNER");

    private final String code;

    TaskTriggerSource(String code) {
        this.code = code;
    }

    /**
     * 落库使用的来源码。
     *
     * @return trigger_source 字符串
     */
    public String getCode() {
        return code;
    }
}
