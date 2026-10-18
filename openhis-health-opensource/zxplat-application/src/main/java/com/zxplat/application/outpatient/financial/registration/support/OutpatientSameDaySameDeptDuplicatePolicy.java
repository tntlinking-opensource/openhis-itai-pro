/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.support;

import org.springframework.stereotype.Component;

/**
 * 门诊同日同科室防重策略（IF-REG-06 · BR-REG-011）。
 *
 * <p>产品默认启用防重。院点在启动时以代码写死注入放宽（如国基
 * {@code H22010202493OutpatientRegistrationConfiguration}），<strong>禁止</strong>靠 yml 开关。</p>
 */
@Component
public class OutpatientSameDaySameDeptDuplicatePolicy {

    /**
     * 为 true 时跳过同日同科室占名额防重（应用层 REG-BIZ-409 与领域 assert）。
     *
     * <p>默认 false；仅院点 Configuration {@code @PostConstruct} 可改写。</p>
     */
    private boolean allowSameDaySameDeptMultiple;

    /**
     * 是否允许同一患者在同一挂号科室、同一自然日开立多条门诊就诊。
     *
     * @return true 表示放宽 BR-REG-011 / BR-ENC-011；false 表示仍执行防重
     */
    public boolean allowsSameDaySameDeptMultiple() {
        return allowSameDaySameDeptMultiple;
    }

    /**
     * 院点启动注入放宽标志（产品壳默认 false，禁止业务代码运行中随意改）。
     *
     * @param allowSameDaySameDeptMultiple true 允许同日同科多挂
     */
    public void setAllowSameDaySameDeptMultiple(boolean allowSameDaySameDeptMultiple) {
        this.allowSameDaySameDeptMultiple = allowSameDaySameDeptMultiple;
    }
}
