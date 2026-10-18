/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.web.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.framework.core.application.dto.Result;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.infrastructure.web.ApiAccessLogInterceptor;
import com.zxplat.framework.core.infrastructure.web.SilentPollHttpHeaders;
import com.zxplat.framework.core.module.ExtensionModuleAppServiceRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AppService自动注册为REST API
 * 扫描所有以AppService结尾的类，自动创建Controller代理
 */
@RestController
@RequestMapping("/api/app")
@Tag(name = "AppService", description = "应用服务接口 - 动态调用所有 AppService 方法")
public class AppServiceControllerRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(AppServiceControllerRegistrar.class);

    private static final Logger accessLogger = LoggerFactory.getLogger(ApiAccessLogInterceptor.ACCESS_LOGGER_NAME);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, ServiceMethod> serviceMethods = new HashMap<>();

    // 懒加载标志，使用 volatile 确保可见性
    private volatile boolean initialized = false;
    // 初始化锁，确保线程安全
    private final Object initLock = new Object();
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${app.logging.api-params-enabled:true}")
    private boolean apiParamsLogEnabled;

    @Value("${app.logging.api-params-max-length:2000}")
    private int apiParamsMaxLength;

    /**
     * 初始化：扫描所有AppService并注册方法
     * 优化：改为懒加载，在首次请求时初始化，减少启动时间
     */
    private void init() {
        try {
            Map<String, Object> appServices = applicationContext.getBeansWithAnnotation(Service.class);
            logger.info("开始注册 AppService 方法，找到 {} 个 Service Bean", appServices.size());

            // 详细日志：列出所有找到的 Service Bean
            if (logger.isDebugEnabled()) {
                for (Map.Entry<String, Object> entry : appServices.entrySet()) {
                    logger.debug("找到 Service Bean: beanName={}, className={}, simpleName={}",
                        entry.getKey(),
                        entry.getValue().getClass().getName(),
                        entry.getValue().getClass().getSimpleName());
                }
            }

            int registeredCount = 0;
            int appServiceBeanCount = 0;
            int skippedCount = 0;

            for (Map.Entry<String, Object> entry : appServices.entrySet()) {
                Object service = entry.getValue();
                // 处理 Spring AOP 代理：获取实际的目标类
                @SuppressWarnings("null")
                Class<?> serviceClass = AopUtils.getTargetClass(service);
                String simpleName = serviceClass.getSimpleName();
                String className = serviceClass.getName();

                logger.debug("检查 Service Bean: beanName={}, className={}, simpleName={}, isProxy={}",
                    entry.getKey(), className, simpleName, AopUtils.isAopProxy(service));

                // 只处理以AppService结尾的类
                if (!simpleName.endsWith("AppService")) {
                    skippedCount++;
                    logger.debug("跳过非 AppService: {}", simpleName);
                    continue;
                }

                appServiceBeanCount++;
                logger.info("发现 AppService: {} (beanName: {})", simpleName, entry.getKey());

                // Gate：health @Primary 装饰器统一暴露 /applicationConfiguration；跳过框架同名实现避免双注册
                if ("ApplicationConfigurationAppService".equals(simpleName)
                    && "com.zxplat.framework.web.application.ApplicationConfigurationAppService".equals(className)) {
                    skippedCount++;
                    logger.info("跳过框架 ApplicationConfigurationAppService 的 HTTP 路由，由 health 模块 @Primary 实现暴露");
                    continue;
                }

                // 获取服务名称（去掉AppService后缀，首字母小写）
                String serviceName = getServiceName(simpleName);
                // Pre：健康装饰器对外仍映射为 applicationConfiguration，与前端约定路径一致
                if ("HealthApplicationConfigurationAppService".equals(simpleName)) {
                    serviceName = "applicationConfiguration";
                }

                // 扫描所有公共方法
                Method[] methods = serviceClass.getMethods();
                logger.debug("{} 共有 {} 个方法", simpleName, methods.length);

                int methodRegisteredForThisService = 0;
                for (Method method : methods) {
                    // 跳过Object类的方法和静态方法
                    if (method.getDeclaringClass() == Object.class) {
                        logger.debug("跳过 Object 方法: {}", method.getName());
                        continue;
                    }
                    if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        logger.debug("跳过静态方法: {}", method.getName());
                        continue;
                    }

                    String methodName = method.getName();
                    String route = "/" + serviceName + "/" + methodName;

                    serviceMethods.put(route, new ServiceMethod(service, method));
                    registeredCount++;
                    methodRegisteredForThisService++;
                    logger.debug("注册AppService方法: POST /api/app{} ({}#{})", route, simpleName, methodName);
                }

                logger.info("{} 注册了 {} 个方法", simpleName, methodRegisteredForThisService);
            }

            logger.info("AppService 方法注册完成: 共找到 {} 个 AppService Bean，跳过 {} 个非 AppService，共注册 {} 个方法",
                appServiceBeanCount, skippedCount, registeredCount);
        } catch (Exception e) {
            logger.error("注册 AppService 方法时发生错误", e);
            // 不抛出异常，允许应用继续启动
        }
    }

    /**
     * 通用POST处理器
     * 处理/api/app/{serviceName}/{methodName}路径的请求
     *
     * 示例：
     * - POST /api/app/user/getById - 调用 UserAppService.getById(id)
     * - POST /api/app/user/getList - 调用 UserAppService.getList(medicationrequest)
     * - POST /api/app/user/create - 调用 UserAppService.create(medicationrequest)
     * - POST /api/app/user/update - 调用 UserAppService.update(id, medicationrequest)
     * - POST /api/app/user/delete - 调用 UserAppService.delete(id)
     */
    @PostMapping("/{serviceName}/{methodName}")
    @Operation(
        summary = "调用 AppService 方法",
        description = "动态调用指定 AppService 的方法。serviceName 为服务名（如 user、role），methodName 为方法名（如 getById、create）。" +
                      "请求体应为 JSON 数组格式，包含方法的参数。",
        responses = {
            @ApiResponse(responseCode = "200", description = "调用成功"),
            @ApiResponse(responseCode = "404", description = "接口不存在"),
            @ApiResponse(responseCode = "500", description = "内部错误")
        }
    )
    public Result<Object> handleRequest(
            @io.swagger.v3.oas.annotations.Parameter(description = "服务名称，如 user、role", example = "user")
            @PathVariable String serviceName,
            @io.swagger.v3.oas.annotations.Parameter(description = "方法名称，如 getById、create、update", example = "getById")
            @PathVariable String methodName,
            @io.swagger.v3.oas.annotations.Parameter(description = "方法参数，JSON 对象格式。单个参数：{\"id\": 1}；多个参数：{\"id\": 1, \"medicationrequest\": {...}}")
            @RequestBody(required = false) Object body) {

        // 懒加载初始化：确保在首次请求时初始化
        ensureInitialized();

        String route = "/" + serviceName + "/" + methodName;

        ServiceMethod serviceMethod = serviceMethods.get(route);
        if (serviceMethod == null) {
            if (ExtensionModuleAppServiceRegistry.isOptionalModuleAppService(serviceName)) {
                String message = ExtensionModuleAppServiceRegistry.buildUnavailableMessage(
                        serviceName,
                        methodName,
                        environment,
                        applicationContext.getClassLoader());
                return Result.error(ExtensionModuleAppServiceRegistry.ERROR_CODE_MODULE_NOT_AVAILABLE, message);
            }
            return Result.error("NOT_FOUND", "接口不存在: " + route);
        }

        try {
            logRequestParams(route, body);

            // 解析参数
            Object[] args = parseArguments(serviceMethod.getMethod(), body);

            // 调用方法
            Object result = serviceMethod.getMethod().invoke(serviceMethod.getService(), args);

            // 如果返回void，返回成功结果
            if (result == null && serviceMethod.getMethod().getReturnType() == void.class) {
                return Result.success();
            }

            return Result.success(result);
        } catch (Exception e) {
            logger.error("调用AppService方法失败: {}", route, e);

            // 获取实际异常原因
            Throwable cause = unwrapException(e);

            // 检查是否是自定义异常类型，让 GlobalExceptionHandler 处理
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            if (cause instanceof ValidationException validationException) {
                throw validationException;
            }
            if (cause instanceof EntityNotFoundException entityNotFoundException) {
                throw entityNotFoundException;
            }
            if (cause instanceof com.zxplat.framework.core.application.exception.UnauthorizedException unauthorizedException) {
                throw unauthorizedException;
            }
            if (cause instanceof IllegalArgumentException) {
                // 参数错误直接返回给前端
                return Result.error("INVALID_ARGUMENT", cause.getMessage());
            }

            // 其他异常按原有逻辑处理
            // 开发环境返回详细异常信息，生产环境只返回简要信息
            // if (isDevelopmentMode()) {
            //     String errorMessage = buildDetailedErrorMessage(cause);
            throw new BusinessException("INTERNAL_ERROR", cause.getMessage(), cause);
//                return Result.error("INTERNAL_ERROR", cause.getMessage());
                // return Result.error("INTERNAL_ERROR", errorMessage);
            // } else {

            // }
        }
    }

    private Throwable unwrapException(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void logRequestParams(String route, Object body) {
        if (!apiParamsLogEnabled) {
            return;
        }
        // 静默轮询：跳过入参日志，避免高频刷屏
        if (SilentPollHttpHeaders.isCurrentRequestSilentPoll()) {
            return;
        }
        try {
            String params = formatParamsForLog(body);
            accessLogger.info("POST /api/app{} params={}", route, params);
        } catch (Exception e) {
            accessLogger.info("POST /api/app{} params=<serialize failed: {}>", route, e.getMessage());
        }
    }

    private String formatParamsForLog(Object body) throws JsonProcessingException {
        if (body == null) {
            return "(empty)";
        }
        Object sanitized = sanitizeSensitiveFields(body);
        String json = objectMapper.writeValueAsString(sanitized);
        if (json.length() <= apiParamsMaxLength) {
            return json;
        }
        return json.substring(0, apiParamsMaxLength) + "...(truncated)";
    }

    private Object sanitizeSensitiveFields(Object body) {
        if (!(body instanceof Map<?, ?> source)) {
            return body;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (isSensitiveParamKey(key)) {
                sanitized.put(key, "***");
            } else {
                sanitized.put(key, entry.getValue());
            }
        }
        return sanitized;
    }

    private static boolean isSensitiveParamKey(String key) {
        String normalized = key.toLowerCase();
        return normalized.contains("password")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("credential");
    }

    /**
     * 判断是否为开发模式
     */
    private boolean isDevelopmentMode() {
        return "dev".equals(activeProfile) || "local".equals(activeProfile);
    }

    /**
     * 构建详细的错误信息（仅在开发环境使用）
     */
    private String buildDetailedErrorMessage(Throwable cause) {
        StringBuilder sb = new StringBuilder();
        sb.append("异常类型: ").append(cause.getClass().getName()).append("\n");
        sb.append("异常消息: ").append(cause.getMessage()).append("\n\n");
        sb.append("堆栈跟踪:\n");

        StackTraceElement[] stackTrace = cause.getStackTrace();
        int maxLines = Math.min(10, stackTrace.length); // 只显示前10行堆栈
        for (int i = 0; i < maxLines; i++) {
            sb.append("  at ").append(stackTrace[i].toString()).append("\n");
        }

        if (stackTrace.length > maxLines) {
            sb.append("  ... 还有 ").append(stackTrace.length - maxLines).append(" 行\n");
        }

        // 如果有内部异常，也显示
        if (cause.getCause() != null) {
            sb.append("\n原因: ").append(cause.getCause().getClass().getName())
                .append(": ").append(cause.getCause().getMessage());
        }

        return sb.toString();
    }

    /**
     * 解析方法参数
     * 从JSON格式的RequestBody中解析参数
     * 支持两种格式：
     * 1. 单个参数：直接传递参数对象，如 {"id": 1} 或 {"userName": "admin"}
     * 2. 多个参数：使用对象格式，属性名为参数名，如 {"id": 1, "medicationrequest": {...}}
     * 3. 数组格式（兼容旧格式）：[1] 或 [{"userName":"admin"}]
     */
    private Object[] parseArguments(Method method, Object body) throws Exception {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }

        Object[] args = new Object[parameters.length];

        if (body == null) {
            for (int i = 0; i < parameters.length; i++) {
                args[i] = getDefaultValue(parameters[i].getType());
            }
            return args;
        }

        if (body instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = (Map<String, Object>) body;

            if (parameters.length == 1) {
                Parameter param = parameters[0];
                String paramName = param.getName();

                // { "value": "..." } 仅对简单类型参数解包；复杂 DTO（如 IdValueRequest）需保留整包
                if (bodyMap.containsKey("value") && bodyMap.size() == 1 && isSimpleType(param.getType())) {
                    args[0] = convertValue(bodyMap.get("value"), param.getType(), param.getParameterizedType(),
                        method.getName(), "value");
                } else if (paramName != null && !paramName.isEmpty() && bodyMap.containsKey(paramName)) {
                    args[0] = convertValue(bodyMap.get(paramName), param.getType(), param.getParameterizedType(),
                        method.getName(), paramName);
                } else if (isSimpleType(param.getType()) && bodyMap.size() == 1) {
                    Object firstValue = bodyMap.values().iterator().next();
                    args[0] = convertValue(firstValue, param.getType(), param.getParameterizedType(),
                        method.getName(), paramName != null && !paramName.isEmpty() ? paramName : "value");
                } else {
                    args[0] = convertValue(body, param.getType(), param.getParameterizedType(),
                        method.getName(), paramName);
                }
            } else {
                for (int i = 0; i < parameters.length; i++) {
                    Parameter param = parameters[i];
                    String paramName = param.getName();
                    if (paramName == null || paramName.isEmpty()) {
                        paramName = "arg" + i;
                    }

                    if (bodyMap.containsKey(paramName)) {
                        args[i] = convertValue(bodyMap.get(paramName), param.getType(), param.getParameterizedType(),
                            method.getName(), paramName);
                    } else {
                        args[i] = getDefaultValue(param.getType());
                    }
                }
            }
            return args;
        }

        if (body instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> bodyList = (List<Object>) body;

            // 判断：如果只有一个参数且该参数类型是 List，则整个 List 作为该参数
            if (parameters.length == 1 && List.class.isAssignableFrom(parameters[0].getType())) {
                logger.debug("整个List作为单个参数传入，方法: {}, 参数类型: {}, List大小: {}",
                    method.getName(), parameters[0].getType().getSimpleName(), bodyList.size());
                args[0] = convertValue(body, parameters[0].getType(), parameters[0].getParameterizedType(),
                    method.getName(), parameters[0].getName());
                return args;
            }

            // 否则按旧逻辑处理：List 的每个元素对应一个参数（兼容旧格式）
            logger.debug("List按元素拆分为多个参数，方法: {}, 参数个数: {}, List大小: {}",
                method.getName(), parameters.length, bodyList.size());
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                Class<?> parameterType = parameter.getType();

                if (i < bodyList.size()) {
                    Object argValue = bodyList.get(i);
                    args[i] = convertValue(argValue, parameterType, parameter.getParameterizedType(),
                        method.getName(), "arg" + i);
                } else {
                    args[i] = getDefaultValue(parameterType);
                }
            }
            return args;
        }

        if (parameters.length == 1) {
            args[0] = convertValue(body, parameters[0].getType(), parameters[0].getParameterizedType(),
                method.getName(), parameters[0].getName());
        } else {
            for (int i = 0; i < parameters.length; i++) {
                args[i] = getDefaultValue(parameters[i].getType());
            }
        }

        return args;
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
            || String.class == type
            || Number.class.isAssignableFrom(type)
            || Boolean.class == type
            || Character.class == type
            || type.isEnum()
            || java.util.Date.class.isAssignableFrom(type)
            || java.time.temporal.Temporal.class.isAssignableFrom(type)
            || Collection.class.isAssignableFrom(type)
            || Map.class.isAssignableFrom(type);
    }

    private Object convertValue(Object value, Class<?> targetType, Type genericType, String methodName, String paramName) {
        try {
            if (value == null) {
                return null;
            }

            if (value instanceof String) {
                String strValue = (String) value;

                if (targetType == Long.class || targetType == long.class) {
                    if (strValue.trim().isEmpty()) {
                        return targetType == long.class ? 0L : null;
                    }
                    try {
                        return Long.parseLong(strValue.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                            String.format("字段类型不符: 参数 %s 期望 Long 类型，但无法将字符串 '%s' 转换为数字",
                                paramName, strValue));
                    }
                }

                if (targetType == Integer.class || targetType == int.class) {
                    if (strValue.trim().isEmpty()) {
                        return targetType == int.class ? 0 : null;
                    }
                    try {
                        return Integer.parseInt(strValue.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                            String.format("字段类型不符: 参数 %s 期望 Integer 类型，但无法将字符串 '%s' 转换为数字",
                                paramName, strValue));
                    }
                }

                if (List.class.isAssignableFrom(targetType)) {
                    Class<?> elementType = extractListElementType(genericType);

                    strValue = strValue.trim();
                    if (strValue.startsWith("[") && strValue.endsWith("]")) {
                        try {
                            return objectMapper.readValue(strValue,
                                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
                        } catch (Exception e) {
                            throw new IllegalArgumentException(
                                String.format("字段类型不符: 参数 %s 期望 List<%s> 类型，但无法解析 JSON 字符串: %s",
                                    paramName, elementType.getSimpleName(), strValue), e);
                        }
                    }
                    // 如果是逗号分隔的字符串，分割后转换为List
                    else if (strValue.contains(",")) {
                        logger.debug("逗号分隔字符串转换为List，方法: {}, 参数: {}, 元素类型: {}, 值: {}",
                            methodName, paramName, elementType.getSimpleName(), strValue);
                        String[] items = strValue.split(",");
                        List<Object> resultList = new ArrayList<>();
                        for (String item : items) {
                            String trimmedItem = item.trim();
                            if (!trimmedItem.isEmpty()) {
                                Object convertedItem = convertListElement(trimmedItem, elementType, methodName, paramName);
                                resultList.add(convertedItem);
                            }
                        }
                        return resultList;
                    }
                    // 单个字符串值转换为单元素List
                    else {
                        logger.debug("单个字符串值转换为单元素List，方法: {}, 参数: {}, 元素类型: {}, 值: {}",
                            methodName, paramName, elementType.getSimpleName(), strValue);
                        List<Object> singleItemList = new ArrayList<>();
                        Object convertedItem = convertListElement(strValue, elementType, methodName, paramName);
                        singleItemList.add(convertedItem);
                        return singleItemList;
                    }
                }
            }

            if (List.class.isAssignableFrom(targetType)) {
                Class<?> elementType = extractListElementType(genericType);

                if (!(value instanceof List)) {
                    logger.debug("单个值转换为单元素List，方法: {}, 参数: {}, 元素类型: {}, 值类型: {}",
                        methodName, paramName, elementType.getSimpleName(), value.getClass().getSimpleName());
                    List<Object> singleItemList = new ArrayList<>();
                    Object convertedItem = convertListElement(value, elementType, methodName, paramName);
                    singleItemList.add(convertedItem);
                    return singleItemList;
                }

                @SuppressWarnings("unchecked")
                List<Object> sourceList = (List<Object>) value;

                logger.debug("转换List，方法: {}, 参数: {}, 元素类型: {}, 列表大小: {}",
                    methodName, paramName, elementType.getSimpleName(), sourceList.size());

                List<Object> targetList = new ArrayList<>();
                for (int i = 0; i < sourceList.size(); i++) {
                    Object item = sourceList.get(i);
                    try {
                        Object convertedItem = convertListElement(item, elementType, methodName, paramName);
                        targetList.add(convertedItem);
                        logger.debug("List元素转换成功 - 索引: {}, 原始值: {}, 原始类型: {}, 目标类型: {}, 转换后值: {}",
                            i, item, item != null ? item.getClass().getSimpleName() : "null",
                            elementType.getSimpleName(), convertedItem);
                    } catch (Exception e) {
                        throw new IllegalArgumentException(
                            String.format("字段类型不符: 参数 %s 的第 %d 个元素无法转换为 %s 类型，值: %s",
                                paramName, i, elementType.getSimpleName(), item), e);
                    }
                }
                logger.debug("List转换完成 - 方法: {}, 参数: {}, 原始大小: {}, 转换后大小: {}",
                    methodName, paramName, sourceList.size(), targetList.size());
                return targetList;
            }

            if ((targetType == Long.class || targetType == long.class) && value instanceof Number) {
                return ((Number) value).longValue();
            }

            if ((targetType == Integer.class || targetType == int.class) && value instanceof Number) {
                return ((Number) value).intValue();
            }

            return objectMapper.convertValue(value, targetType);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("参数转换失败 - 方法: {}, 参数: {}, 期望类型: {}, 实际值类型: {}, 实际值: {}",
                methodName, paramName, targetType.getName(),
                value != null ? value.getClass().getName() : "null", value);
            throw new IllegalArgumentException(
                String.format("字段类型不符: 参数 %s 期望 %s 类型，实际收到 %s 类型，值: %s",
                    paramName, targetType.getSimpleName(),
                    value != null ? value.getClass().getSimpleName() : "null", value), e);
        }
    }
    private Class<?> extractListElementType(Type genericType) {
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                return (Class<?>) actualTypeArguments[0];
            }
        }
        return Object.class;
    }

    private Object convertListElement(Object item, Class<?> elementType, String methodName, String paramName) {
        if (item == null) {
            return null;
        }

        if (elementType == Long.class || elementType == long.class) {
            if (item instanceof String) {
                String strItem = ((String) item).trim();
                if (strItem.isEmpty()) {
                    return elementType == long.class ? 0L : null;
                }
                try {
                    return Long.parseLong(strItem);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        String.format("无法将字符串 '%s' 转换为 Long 类型", strItem), e);
                }
            } else if (item instanceof Number) {
                return ((Number) item).longValue();
            } else {
                throw new IllegalArgumentException(
                    String.format("无法将 %s 类型的值 '%s' 转换为 Long 类型",
                        item.getClass().getSimpleName(), item));
            }
        }

        if (elementType == Integer.class || elementType == int.class) {
            if (item instanceof String) {
                String strItem = ((String) item).trim();
                if (strItem.isEmpty()) {
                    return elementType == int.class ? 0 : null;
                }
                try {
                    return Integer.parseInt(strItem);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        String.format("无法将字符串 '%s' 转换为 Integer 类型", strItem), e);
                }
            } else if (item instanceof Number) {
                return ((Number) item).intValue();
            } else {
                throw new IllegalArgumentException(
                    String.format("无法将 %s 类型的值 '%s' 转换为 Integer 类型",
                        item.getClass().getSimpleName(), item));
            }
        }

        if (elementType == String.class) {
            return item.toString();
        }

        if (elementType == Boolean.class || elementType == boolean.class) {
            if (item instanceof String) {
                String strItem = ((String) item).trim().toLowerCase();
                if ("true".equals(strItem) || "1".equals(strItem)) {
                    return true;
                } else if ("false".equals(strItem) || "0".equals(strItem)) {
                    return false;
                } else {
                    throw new IllegalArgumentException(
                        String.format("无法将字符串 '%s' 转换为 Boolean 类型", item));
                }
            } else if (item instanceof Boolean) {
                return item;
            } else if (item instanceof Number) {
                return ((Number) item).intValue() != 0;
            }
        }

        try {
            return objectMapper.convertValue(item, elementType);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                String.format("无法将 %s 类型的值转换为 %s 类型，值: %s",
                    item.getClass().getSimpleName(), elementType.getSimpleName(), item), e);
        }
    }

    /**
     * 确保初始化完成（懒加载）
     * 使用双重检查锁定模式，确保线程安全
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    init();
                    initialized = true;
                }
            }
        }
    }

    /**
     * 获取默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0;
            if (type == char.class) return '\u0000';
        }
        return null;
    }

    /**
     * 获取服务名称
     * 去掉AppService后缀，首字母小写
     */
    private String getServiceName(String className) {
        String name = className;
        if (name.endsWith("AppService")) {
            name = name.substring(0, name.length() - "AppService".length());
        }
        if (name.length() > 0) {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    /**
     * 服务方法封装
     */
    private static class ServiceMethod {
        private final Object service;
        private final Method method;

        public ServiceMethod(Object service, Method method) {
            this.service = service;
            this.method = method;
        }

        public Object getService() {
            return service;
        }

        public Method getMethod() {
            return method;
        }
    }
}
