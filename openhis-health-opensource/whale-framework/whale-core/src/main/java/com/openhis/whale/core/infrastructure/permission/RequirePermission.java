/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限检查注解
 * 标注在方法上，自动检查用户是否拥有指定权限
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 权限名称
     * 如果为空，则自动生成：模块.实体.操作
     */
    String value() default "";

    /**
     * 是否允许宿主用户（租户ID为null的用户）
     */
    boolean allowHost() default false;
}
