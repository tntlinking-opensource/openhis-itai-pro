/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.module;

import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 可选扩展模块与 AppService 路由前缀映射（始终可用，不依赖模块 classpath）。
 *
 * <p>路由表来自各 Module {@code META-INF/zxplat/module-metadata.json}。</p>
 */
public final class ExtensionModuleAppServiceRegistry {

    public static final String ERROR_CODE_MODULE_NOT_AVAILABLE = "MODULE_NOT_AVAILABLE";

    private record Route(
            String moduleId,
            String displayName,
            String enabledProperty,
            String autoConfigurationClass,
            Set<String> appServiceNames) {
    }

    private static final List<Route> ROUTES = ZxplatModuleMetadataCatalog.entries().stream()
            .map(meta -> new Route(
                    meta.moduleId(),
                    meta.displayName(),
                    meta.enabledProperty(),
                    meta.autoConfigurationClass(),
                    meta.appServiceNames()))
            .toList();

    private ExtensionModuleAppServiceRegistry() {
    }

    public static Optional<Route> findRoute(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return Optional.empty();
        }
        String normalized = serviceName.trim();
        return ROUTES.stream()
                .filter(route -> route.appServiceNames().contains(normalized))
                .findFirst();
    }

    public static boolean isOptionalModuleAppService(String serviceName) {
        return findRoute(serviceName).isPresent();
    }

    public static String buildUnavailableMessage(
            String serviceName, String methodName, Environment environment, ClassLoader classLoader) {
        String api = serviceName + "/" + methodName;
        Optional<Route> routeOptional = findRoute(serviceName);
        if (routeOptional.isEmpty()) {
            return "接口不存在: /" + api;
        }

        Route route = routeOptional.get();
        boolean onClasspath = ClassUtils.isPresent(route.autoConfigurationClass(), classLoader);
        // 方案 A：未配置时跟随 classpath；仅显式 false 视为关闭
        Boolean configured = environment.getProperty(route.enabledProperty(), Boolean.class);
        boolean enabled;
        if (configured == null) {
            enabled = onClasspath;
        } else {
            enabled = configured.booleanValue();
        }

        if (!onClasspath) {
            return String.format(
                    Locale.ROOT,
                    "接口不可用（/%s）：对应【%s】扩展模块，当前环境可能未购买或未部署该模块（请添加 %s-starter 或 -Pwith-%s），请联系管理员开通。",
                    api,
                    route.displayName(),
                    route.moduleId(),
                    route.moduleId());
        }
        if (!enabled) {
            return String.format(
                    Locale.ROOT,
                    "接口不可用（/%s）：对应【%s】扩展模块，模块已安装但未启用（%s=false），请联系管理员检查配置。",
                    api,
                    route.displayName(),
                    route.enabledProperty());
        }
        return String.format(
                Locale.ROOT,
                "接口不可用（/%s）：对应【%s】扩展模块，模块已启用但服务未注册，请联系管理员检查部署与 Spring 装配。",
                api,
                route.displayName());
    }
}
