/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.job;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 AppService 方法可被定时任务页面直接绑定。
 *
 * <p>处理器 key 与 HTTP 路由一致：{@code {serviceName}/{methodName}}，
 * 例如 {@code nurseStationInpatientBilling/executeInpatientAutoRollJob}。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchedulableJob {

    /**
     * 运维可见描述（出现在「任务处理器」下拉）。
     */
    String value() default "";
}
