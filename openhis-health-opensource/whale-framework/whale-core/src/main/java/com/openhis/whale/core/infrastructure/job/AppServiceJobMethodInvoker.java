/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 在 Quartz 线程中调用 {@link SchedulableJob} 标记的 AppService 方法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppServiceJobMethodInvoker {

    private final AppServiceJobMethodRegistry appServiceJobMethodRegistry;

    private final ObjectMapper objectMapper;

    private final TenantContext tenantContext;

    /**
     * @param handlerKey {@code serviceName/methodName}
     * @param context    Quartz 任务上下文
     */
    public void invoke(String handlerKey, JobContext context) throws Exception {
        AppServiceJobMethodDescriptor descriptor = appServiceJobMethodRegistry.getRequired(handlerKey);
        Map<String, Object> businessParameters = JobExecutionParameterFilter.toBusinessParameters(context.getParameters());
        Long tenantId = context.getTenantId();
        boolean tenantBound = tenantId != null;
        if (tenantBound) {
            tenantContext.setCurrentTenantId(tenantId);
        }
        try {
            Object[] args = buildArguments(descriptor.getMethod(), businessParameters);
            log.info("Invoking AppService job handler: {} tenantId={}", handlerKey, tenantId);
            try {
                // 须经代理 Method 调用，否则 CGLIB 会绕过 AppServiceAspect（含 openAppServiceTransaction=false）
                Method invocable = resolveInvocableMethod(descriptor);
                invocable.invoke(descriptor.getServiceBean(), args);
            } catch (InvocationTargetException ex) {
                // 反射包装会吞掉业务异常 message，导致执行日志 error_message 为空、排障困难
                Throwable cause = ex.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw ex;
            }
        } finally {
            if (tenantBound) {
                tenantContext.clear();
            }
        }
    }

    /**
     * 解析可经 Spring 代理拦截的 Method（避免目标类 Method.invoke 绕过 AOP）。
     *
     * @param descriptor 任务处理器描述
     * @return 代理或目标上同签名方法
     */
    private Method resolveInvocableMethod(AppServiceJobMethodDescriptor descriptor) throws NoSuchMethodException {
        Method declared = descriptor.getMethod();
        Object serviceBean = descriptor.getServiceBean();
        if (serviceBean == null) {
            return declared;
        }
        return serviceBean.getClass().getMethod(declared.getName(), declared.getParameterTypes());
    }

    private Object[] buildArguments(Method method, Map<String, Object> businessParameters) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }
        Object[] args = new Object[parameters.length];
        if (parameters.length == 1) {
            args[0] = convertSingleArgument(parameters[0], businessParameters, method.getName());
            return args;
        }
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String paramName = parameter.getName();
            if (businessParameters.containsKey(paramName)) {
                args[i] = objectMapper.convertValue(businessParameters.get(paramName), parameter.getType());
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private Object convertSingleArgument(Parameter parameter, Map<String, Object> businessParameters, String methodName) {
        Class<?> parameterType = parameter.getType();
        if (businessParameters.isEmpty()) {
            return null;
        }
        String paramName = parameter.getName();
        if (paramName != null && businessParameters.containsKey(paramName)) {
            return objectMapper.convertValue(businessParameters.get(paramName), parameterType);
        }
        if (isSimpleType(parameterType) && businessParameters.size() == 1) {
            Object firstValue = businessParameters.values().iterator().next();
            return objectMapper.convertValue(firstValue, parameterType);
        }
        return objectMapper.convertValue(businessParameters, parameterType);
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || String.class == type
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || type.isEnum();
    }
}
