/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.job;

import lombok.Getter;

import java.lang.reflect.Method;

/**
 * 可被 Quartz 调度的 AppService 方法描述。
 */
@Getter
public class AppServiceJobMethodDescriptor {

    /** 与 {@code /api/app/{serviceName}/{methodName}} 一致的处理器 key。 */
    private final String handlerKey;

    private final String serviceName;

    private final String methodName;

    private final String appServiceClassName;

    private final String description;

    private final Object serviceBean;

    private final Method method;

    public AppServiceJobMethodDescriptor(
            String handlerKey,
            String serviceName,
            String methodName,
            String appServiceClassName,
            String description,
            Object serviceBean,
            Method method) {
        this.handlerKey = handlerKey;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.appServiceClassName = appServiceClassName;
        this.description = description;
        this.serviceBean = serviceBean;
        this.method = method;
    }
}
