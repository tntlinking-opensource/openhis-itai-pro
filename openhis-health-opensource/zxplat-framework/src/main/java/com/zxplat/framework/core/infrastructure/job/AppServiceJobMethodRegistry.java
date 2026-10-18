/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫描带 {@link SchedulableJob} 的 AppService 方法，供定时任务页面绑定。
 */
@Slf4j
@Component
public class AppServiceJobMethodRegistry {

    private final ApplicationContext applicationContext;

    private volatile boolean initialized;

    private final Object initLock = new Object();

    private final Map<String, AppServiceJobMethodDescriptor> handlers = new ConcurrentHashMap<>();

    public AppServiceJobMethodRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * @return 全部可调度 AppService 方法（按 handlerKey 排序）
     */
    public List<AppServiceJobMethodDescriptor> listHandlers() {
        ensureInitialized();
        List<AppServiceJobMethodDescriptor> result = new ArrayList<>(handlers.values());
        result.sort(Comparator.comparing(AppServiceJobMethodDescriptor::getHandlerKey));
        return result;
    }

    /**
     * @param handlerKey {@code serviceName/methodName}
     */
    public boolean exists(String handlerKey) {
        if (handlerKey == null || handlerKey.trim().isEmpty()) {
            return false;
        }
        ensureInitialized();
        return handlers.containsKey(normalizeHandlerKey(handlerKey));
    }

    /**
     * @param handlerKey {@code serviceName/methodName}
     */
    public AppServiceJobMethodDescriptor getRequired(String handlerKey) {
        ensureInitialized();
        AppServiceJobMethodDescriptor descriptor = handlers.get(normalizeHandlerKey(handlerKey));
        if (descriptor == null) {
            throw new IllegalArgumentException("AppService 定时任务处理器不存在: " + handlerKey);
        }
        return descriptor;
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    scan();
                    initialized = true;
                }
            }
        }
    }

    private void scan() {
        Map<String, Object> services = applicationContext.getBeansWithAnnotation(Service.class);
        int count = 0;
        for (Map.Entry<String, Object> entry : services.entrySet()) {
            Object service = entry.getValue();
            Class<?> serviceClass = AopUtils.getTargetClass(service);
            String simpleName = serviceClass.getSimpleName();
            if (!simpleName.endsWith("AppService")) {
                continue;
            }
            String serviceName = toServiceName(simpleName);
            for (Method method : serviceClass.getMethods()) {
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                SchedulableJob marker = method.getAnnotation(SchedulableJob.class);
                if (marker == null) {
                    continue;
                }
                String methodName = method.getName();
                String handlerKey = serviceName + "/" + methodName;
                String description = marker.value();
                if (description == null || description.isBlank()) {
                    description = simpleName + "#" + methodName;
                }
                handlers.put(handlerKey, new AppServiceJobMethodDescriptor(
                        handlerKey,
                        serviceName,
                        methodName,
                        simpleName,
                        description,
                        service,
                        method));
                count++;
                log.info("注册 AppService 定时任务处理器: {} ({})", handlerKey, description);
            }
        }
        log.info("AppService 定时任务处理器扫描完成，共 {} 个", count);
    }

    public static String normalizeHandlerKey(String handlerKey) {
        return handlerKey.trim().replace('.', '/');
    }

    static String toServiceName(String classSimpleName) {
        String name = classSimpleName;
        if (name.endsWith("AppService")) {
            name = name.substring(0, name.length() - "AppService".length());
        }
        if (!name.isEmpty()) {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }
}
