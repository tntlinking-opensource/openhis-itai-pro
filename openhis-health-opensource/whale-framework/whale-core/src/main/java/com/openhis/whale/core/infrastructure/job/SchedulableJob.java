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

    /**
     * 是否由 {@code AppServiceAspect} 包一层外层读写事务。
     *
     * <p>默认 {@code true}（与普通 AppService 一致）。长批次且「单条失败不阻断整批」的任务
     * （如住院滚费）须设为 {@code false}，避免中途业务异常把整批标成 rollback-only，
     * 提交时 UnexpectedRollbackException 整单回滚，并与同分钟库存备份抢长事务。</p>
     */
    boolean openAppServiceTransaction() default true;
}
