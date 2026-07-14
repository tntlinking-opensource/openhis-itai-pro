/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.permission;

import com.openhis.whale.core.application.exception.UnauthorizedException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限检查AOP切面
 */
@Aspect
@Component
@Order(2)
public class PermissionAspect {

    @Autowired
    private IPermissionChecker permissionChecker;

    @Before("@annotation(com.openhis.whale.core.infrastructure.permission.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        if (annotation == null) {
            return;
        }

        String permissionName = annotation.value();

        // 如果权限名称为空，自动生成
        if (permissionName == null || permissionName.trim().isEmpty()) {
            permissionName = generatePermissionName(joinPoint);
        }

        // 检查是否为宿主用户
        if (annotation.allowHost() && TenantUtils.isHost()) {
            return;  // 宿主用户允许访问
        }

        // 检查权限
        if (!permissionChecker.isGranted(permissionName)) {
            throw new UnauthorizedException("没有权限: " + permissionName);
        }
    }

    /**
     * 自动生成权限名称
     * 格式：模块.实体.操作
     * 例如：Identity.Users.Create
     */
    private String generatePermissionName(JoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // 去掉AppService后缀
        if (className.endsWith("AppService")) {
            className = className.substring(0, className.length() - "AppService".length());
        }

        // 提取实体名（假设类名为UserAppService，实体名为User）
        String entityName = className;

        // 提取操作名（getList -> List, create -> Create, update -> Update, delete -> Delete）
        String operation = capitalizeFirst(methodName);
        if (operation.startsWith("Get")) {
            operation = operation.substring(3);
        }

        // 生成权限名称
        return "Identity." + entityName + "s." + operation;
    }

    /**
     * 首字母大写
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
