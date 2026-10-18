/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.multitenancy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 租户感知注解
 * 标注在方法或类上，表示该方法或类需要租户上下文
 * 可以用于明确标识需要多租户支持的操作
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantAware {

    /**
     * 是否允许宿主用户（租户ID为null的用户）访问
     * @return true表示允许宿主用户访问
     */
    boolean allowHost() default true;
}
