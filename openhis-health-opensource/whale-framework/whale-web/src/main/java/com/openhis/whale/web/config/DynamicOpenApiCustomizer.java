/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 动态 OpenAPI 自定义器
 * 为动态生成的 AppService 接口生成 Swagger 文档
 */
@Component
@Order(0)  // 使用 Order(0) 确保在其他自定义器之前执行，这样可以在 SpringDoc 自动扫描的路径基础上添加动态路径
@SuppressWarnings({"rawtypes", "unchecked"})
public class DynamicOpenApiCustomizer implements OpenApiCustomizer {

    private static final Logger logger = LoggerFactory.getLogger(DynamicOpenApiCustomizer.class);

    @Autowired
    private ApplicationContext applicationContext;
    // 是否启用 Swagger 文档生成 生产改为false
    private boolean swaggerEnabled=true;

    private OpenAPI currentOpenApi;
    private Set<String> registeredSchemas = new HashSet<>();

    /**
     * 构造函数 - 用于确认 Bean 是否被创建
     */
    public DynamicOpenApiCustomizer() {
        logger.info("========== DynamicOpenApiCustomizer 构造函数被调用 ==========");
    }

    /**
     * PostConstruct 方法 - 用于确认 Bean 是否被初始化
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        logger.info("========== DynamicOpenApiCustomizer @PostConstruct 方法被调用 ==========");
        logger.info("DynamicOpenApiCustomizer Bean 已成功创建和初始化");
        logger.info("ApplicationContext: {}", applicationContext != null ? "已注入" : "未注入");
        logger.info("提示：customise() 方法将在首次访问 /v3/api-docs 或 Swagger UI 时被 SpringDoc 调用");
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (!swaggerEnabled) {
            logger.info("Swagger 文档生成已禁用");
            return;
        }
        logger.info("========== DynamicOpenApiCustomizer.customise() 开始执行 ==========");

        try {
            this.currentOpenApi = openApi;
            this.registeredSchemas.clear();

            if (openApi.getComponents() == null) {
                openApi.setComponents(new io.swagger.v3.oas.models.Components());
            }
            if (openApi.getComponents().getSchemas() == null) {
                openApi.getComponents().setSchemas(new HashMap<>());
            }

            // 确保 paths 不为 null
            if (openApi.getPaths() == null) {
                openApi.setPaths(new io.swagger.v3.oas.models.Paths());
                logger.info("初始化 OpenAPI paths 对象");
            } else {
                int existingPaths = openApi.getPaths().size();
                logger.info("OpenAPI paths 已存在，当前路径数量: {} (这些是 SpringDoc 自动扫描到的 Controller 路径)", existingPaths);
                // 列出已存在的路径，用于调试
                if (logger.isDebugEnabled() && existingPaths > 0) {
                    openApi.getPaths().keySet().forEach(path ->
                        logger.debug("已存在的路径: {}", path)
                    );
                }
            }

            logger.info("准备获取所有 Service Bean...");
            Map<String, Object> appServices;
            try {
                appServices = applicationContext.getBeansWithAnnotation(Service.class);
                logger.info("成功获取 Service Bean，共找到 {} 个", appServices.size());
            } catch (Exception e) {
                logger.error("获取 Service Bean 时发生错误，将跳过动态 API 文档生成", e);
                return;
            }

            // 详细日志：列出所有找到的 Service Bean
            if (logger.isDebugEnabled()) {
                for (Map.Entry<String, Object> entry : appServices.entrySet()) {
                    logger.debug("找到 Service Bean: beanName={}, className={}, simpleName={}",
                        entry.getKey(),
                        entry.getValue().getClass().getName(),
                        entry.getValue().getClass().getSimpleName());
                }
            }

            int appServiceCount = 0;
            int methodCount = 0;
            int skippedCount = 0;

            for (Map.Entry<String, Object> entry : appServices.entrySet()) {
                try {
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

                    appServiceCount++;

                    // 获取服务名称（去掉AppService后缀，首字母小写）
                    String serviceName = getServiceName(serviceClass.getSimpleName());
                    String tagName = serviceName + "-app-service";

                    logger.info("处理 AppService: {} (serviceName: {})", serviceClass.getSimpleName(), serviceName);

                    // 确保标签存在
                    if (openApi.getTags() == null) {
                        openApi.setTags(new java.util.ArrayList<>());
                    }
                    if (openApi.getTags().stream()
                            .noneMatch(tag -> tag.getName().equals(tagName))) {
                        openApi.addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                                .name(tagName)
                                .description(serviceClass.getSimpleName() + " 接口"));
                    }

                    // 扫描所有公共方法
                    for (Method method : serviceClass.getMethods()) {
                        try {
                            // 跳过Object类的方法和静态方法
                            if (method.getDeclaringClass() == Object.class ||
                                java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                                continue;
                            }

                            // 检查是否标注了 @Hidden 注解，如果有则跳过
                            if (method.isAnnotationPresent(io.swagger.v3.oas.annotations.Hidden.class)) {
                                logger.debug("方法 {} 标注了 @Hidden，跳过文档生成", method.getName());
                                continue;
                            }

                            String methodName = method.getName();
                            String path = "/api/app/" + serviceName + "/" + methodName;

                            logger.debug("为 {} 生成 Swagger 文档路径: {}", serviceClass.getSimpleName(), path);

                            // 创建路径项
                            PathItem pathItem = new PathItem();
                            Operation operation = new Operation();
                            operation.setOperationId(serviceName + "_" + methodName);

                            // 检查是否有 @Operation 注解
                            if (method.isAnnotationPresent(io.swagger.v3.oas.annotations.Operation.class)) {
                                io.swagger.v3.oas.annotations.Operation opAnnotation =
                                    method.getAnnotation(io.swagger.v3.oas.annotations.Operation.class);
                                if (!opAnnotation.summary().isEmpty()) {
                                    operation.setSummary(opAnnotation.summary());
                                } else {
                                    operation.setSummary(methodName);
                                }
                                if (!opAnnotation.description().isEmpty()) {
                                    operation.setDescription(opAnnotation.description());
                                } else {
                                    operation.setDescription("调用 " + serviceClass.getSimpleName() + "." + methodName + " 方法");
                                }
                            } else {
                                operation.setSummary(methodName);
                                operation.setDescription("调用 " + serviceClass.getSimpleName() + "." + methodName + " 方法");
                            }

                            operation.addTagsItem(tagName);

                            // 检查方法参数是否有注解定义
                            boolean hasParameterAnnotations = hasSwaggerParameterAnnotations(method);

                            // 所有请求都使用 RequestBody（即使没有参数也设置，但设为可选）
                            io.swagger.v3.oas.models.parameters.RequestBody requestBody =
                                new io.swagger.v3.oas.models.parameters.RequestBody();
                            Content content = new Content();
                            MediaType mediaType = new MediaType();

                            if (method.getParameterCount() > 0) {
                                if (hasParameterAnnotations) {
                                    // 如果有注解定义，使用注解构建 Schema
                                    Schema<?> schema = buildSchemaFromAnnotations(method);
                                    mediaType.setSchema(schema);
                                    requestBody.setRequired(true);
                                    logger.debug("方法 {} 使用注解定义的参数 Schema", method.getName());
                                } else {
                                    // 否则使用自动映射
                                    requestBody.setRequired(true);
                                    Schema<?> schema = buildParameterSchema(method);
                                    mediaType.setSchema(schema);
                                }
                            } else {
                                requestBody.setRequired(false);
                                // 无参数时使用空对象
                                Schema<?> schema = new Schema<>();
                                schema.setType("object");
                                schema.setDescription("无参数方法，请求体可以为空");
                                mediaType.setSchema(schema);
                            }

                            content.addMediaType("application/json", mediaType);
                            requestBody.setContent(content);
                            operation.setRequestBody(requestBody);

                            // 设置响应
                            ApiResponses responses = new ApiResponses();
                            ApiResponse successResponse = new ApiResponse();
                            successResponse.setDescription("调用成功");
                            Content responseContent = new Content();
                            MediaType responseMediaType = new MediaType();

                            // 检查是否有 @ApiResponse 注解定义返回值
                            if (method.isAnnotationPresent(io.swagger.v3.oas.annotations.responses.ApiResponse.class) ||
                                method.isAnnotationPresent(io.swagger.v3.oas.annotations.responses.ApiResponses.class)) {
                                // 如果有注解定义，SpringDoc 会自动处理，这里不设置 schema
                                logger.debug("方法 {} 使用注解定义的响应 Schema", method.getName());
                            } else {
                                // 否则使用自动映射
                                Schema<?> responseSchema = buildResponseSchema(method.getReturnType());
                                responseMediaType.setSchema(responseSchema);
                                responseContent.addMediaType("application/json", responseMediaType);
                                successResponse.setContent(responseContent);
                            }

                            responses.addApiResponse("200", successResponse);

                            ApiResponse errorResponse = new ApiResponse();
                            errorResponse.setDescription("调用失败");
                            responses.addApiResponse("500", errorResponse);

                            operation.setResponses(responses);

                            // 添加安全要求：需要 Bearer Token 认证
                            // 排除登录相关的公开接口
                            if (!isPublicEndpoint(serviceName, methodName)) {
                                SecurityRequirement securityRequirement = new SecurityRequirement();
                                securityRequirement.addList("Bearer Authentication");
                                operation.addSecurityItem(securityRequirement);
                            }

                            // 强制使用 POST 方法
                            pathItem.setPost(operation);

                            // 添加到 OpenAPI
                            openApi.getPaths().addPathItem(path, pathItem);
                            methodCount++;
                        } catch (Exception e) {
                            logger.warn("为方法 {} 生成 Swagger 文档时发生错误，跳过该方法",
                                serviceClass.getSimpleName() + "." + method.getName(), e);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("处理 AppService {} 时发生错误，跳过该服务", entry.getKey(), e);
                }
            }

            logger.info("动态 AppService 接口 Swagger 文档生成完成: 共找到 {} 个 AppService Bean，跳过 {} 个非 AppService，共处理 {} 个方法",
                appServiceCount, skippedCount, methodCount);

            // 验证生成的路径
            int addedPaths = 0;
            for (String path : openApi.getPaths().keySet()) {
                if (path.startsWith("/api/app/") && !path.contains("{")) {
                    addedPaths++;
                    logger.debug("已添加动态接口路径: {}", path);
                }
            }
            logger.info("验证：共添加 {} 个动态路径到 OpenAPI", addedPaths);

            logger.info("========== DynamicOpenApiCustomizer.customise() 执行完成 ==========");
        } catch (Exception e) {
            logger.error("DynamicOpenApiCustomizer.customise() 执行失败", e);
            // 不抛出异常，允许应用继续运行
        }
    }

    /**
     * 构建参数 Schema
     * 所有参数都通过 RequestBody 传递，格式为 JSON 数组
     */
    private Schema<?> buildParameterSchema(Method method) {
        Parameter[] parameters = method.getParameters();

        if (parameters.length == 0) {
            return new Schema<>().type("object");
        }

        if (parameters.length == 1) {
            Class<?> paramType = parameters[0].getType();
            if (isSimpleType(paramType)) {
                Schema<?> wrapperSchema = new Schema<>();
                wrapperSchema.setType("object");
                wrapperSchema.setDescription("参数对象");
                Map<String, Schema> properties = new HashMap<>();
                Schema<?> valueSchema = buildSchemaForType(paramType);
                properties.put("value", valueSchema);
                wrapperSchema.setProperties(properties);
                return wrapperSchema;
            } else {
                registerSchemaDefinition(paramType);
                Schema<?> schema = new Schema<>();
                schema.set$ref("#/components/schemas/" + paramType.getSimpleName());
                return schema;
            }
        }

        Schema<?> objectSchema = new Schema<>();
        objectSchema.setType("object");
        objectSchema.setDescription("方法参数对象，按顺序传递参数");

        Map<String, Schema> properties = new HashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String paramName = param.getName();
            if (paramName == null || paramName.isEmpty()) {
                paramName = "arg" + i;
            }
            Class<?> paramType = param.getType();
            if (!isSimpleType(paramType) && !paramType.isEnum() &&
                paramType != LocalDateTime.class && paramType != LocalDate.class &&
                !List.class.isAssignableFrom(paramType)) {
                registerSchemaDefinition(paramType);
            }
            properties.put(paramName, buildSchemaForType(paramType));
        }
        objectSchema.setProperties(properties);

        return objectSchema;
    }

    private void registerSchemaDefinition(Class<?> type) {
        if (type == null || isSimpleType(type) || type.isEnum() ||
            type == LocalDateTime.class || type == LocalDate.class) {
            return;
        }

        String schemaName = type.getSimpleName();
        if (registeredSchemas.contains(schemaName)) {
            return;
        }

        registeredSchemas.add(schemaName);

        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setDescription(type.getSimpleName());

        Map<String, Schema> properties = new HashMap<>();
        List<String> requiredFields = new ArrayList<>();

        // 收集当前类及所有父类的字段
        List<Field> allFields = new ArrayList<>();
        Class<?> currentClass = type;
        while (currentClass != null && currentClass != Object.class) {
            Field[] declaredFields = currentClass.getDeclaredFields();
            for (Field field : declaredFields) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    allFields.add(field);
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        // 处理所有字段（包括继承的字段）
        for (Field field : allFields) {
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();

            if (!isSimpleType(fieldType) && !fieldType.isEnum() &&
                fieldType != LocalDateTime.class && fieldType != LocalDate.class) {
                if (List.class.isAssignableFrom(fieldType)) {
                    Type genericType = field.getGenericType();
                    if (genericType instanceof ParameterizedType) {
                        ParameterizedType parameterizedType = (ParameterizedType) genericType;
                        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                        if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                            Class<?> itemType = (Class<?>) actualTypeArguments[0];
                            if (!isSimpleType(itemType) && !itemType.isEnum() &&
                                itemType != LocalDateTime.class && itemType != LocalDate.class) {
                                registerSchemaDefinition(itemType);
                            }
                        }
                    }
                } else {
                    registerSchemaDefinition(fieldType);
                }
            }

            Schema<?> fieldSchema = buildFieldSchema(fieldType, field.getGenericType());

            if (field.isAnnotationPresent(jakarta.validation.constraints.NotNull.class) ||
                field.isAnnotationPresent(jakarta.validation.constraints.NotBlank.class) ||
                field.isAnnotationPresent(jakarta.validation.constraints.NotEmpty.class)) {
                requiredFields.add(fieldName);
            }

            if (field.isAnnotationPresent(io.swagger.v3.oas.annotations.media.Schema.class)) {
                io.swagger.v3.oas.annotations.media.Schema schemaAnnotation =
                    field.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
                if (!schemaAnnotation.description().isEmpty()) {
                    fieldSchema.setDescription(schemaAnnotation.description());
                }
                if (!schemaAnnotation.example().isEmpty()) {
                    fieldSchema.setExample(schemaAnnotation.example());
                }
            }

            properties.put(fieldName, fieldSchema);
        }

        if (!properties.isEmpty()) {
            schema.setProperties(properties);
        }
        if (!requiredFields.isEmpty()) {
            schema.setRequired(requiredFields);
        }

        currentOpenApi.getComponents().addSchemas(schemaName, schema);
        logger.debug("Registered schema: {} (包含 {} 个字段，其中继承字段: {})",
            schemaName, allFields.size(), allFields.size() - type.getDeclaredFields().length);
    }

    /**
     * 判断是否为简单类型
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Long.class || type == long.class ||
               type == Integer.class || type == int.class ||
               type == Boolean.class || type == boolean.class ||
               type == Double.class || type == double.class ||
               type == Float.class || type == float.class ||
               type == Short.class || type == short.class ||
               type == Byte.class || type == byte.class ||
               type == Character.class || type == char.class||
               type == BigDecimal.class;
    }

    /**
     * 构建响应 Schema
     */
    private Schema<?> buildResponseSchema(Class<?> returnType) {
        // 返回类型通常是 Result<T>
        Schema<?> schema = new Schema<>();
        schema.setType("object");
        schema.setDescription("Result 包装的响应");

        // 这里可以进一步解析 Result<T> 的泛型类型
        // 简化处理，直接返回通用对象
        return schema;
    }

    private Schema<?> buildSchemaForType(Class<?> type) {
        Schema<?> schema = new Schema<>();

        if (type == String.class) {
            schema.setType("string");
        } else if (type == Long.class || type == long.class) {
            schema.setType("integer");
            schema.setFormat("int64");
        } else if (type == Integer.class || type == int.class) {
            schema.setType("integer");
            schema.setFormat("int32");
        } else if (type == Boolean.class || type == boolean.class) {
            schema.setType("boolean");
        } else if (type == Double.class || type == double.class) {
            schema.setType("number");
            schema.setFormat("double");
        } else if (type == Float.class || type == float.class) {
            schema.setType("number");
            schema.setFormat("float");
        } else if (type == Short.class || type == short.class) {
            schema.setType("integer");
            schema.setFormat("int32");
        } else if (type == Byte.class || type == byte.class) {
            schema.setType("integer");
            schema.setFormat("int32");
        } else if (type == Character.class || type == char.class) {
            schema.setType("string");
        } else if (type == LocalDateTime.class || type == LocalDate.class) {
            schema.setType("string");
//            schema.setFormat("date-time");
        }  else if (type == BigDecimal.class) {
            schema.setType("number");
            schema.setFormat("double");
        }  else if (type.isEnum()) {
            schema.setType("string");
            List enumValues = new ArrayList();
            for (Object enumConstant : type.getEnumConstants()) {
                enumValues.add(enumConstant.toString());
            }
            schema.setEnum(enumValues);
        } else if (List.class.isAssignableFrom(type)) {
            schema.setType("array");
            schema.setItems(new Schema<>().type("object"));
        } else {
            schema.set$ref("#/components/schemas/" + type.getSimpleName());
        }

        return schema;
    }

    private Schema<?> buildFieldSchema(Class<?> fieldType, Type genericType) {
        Schema<?> fieldSchema = new Schema<>();

        if (fieldType == String.class) {
            fieldSchema.setType("string");
        } else if (fieldType == Long.class || fieldType == long.class) {
            fieldSchema.setType("integer");
            fieldSchema.setFormat("int64");
        } else if (fieldType == Integer.class || fieldType == int.class) {
            fieldSchema.setType("integer");
            fieldSchema.setFormat("int32");
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
            fieldSchema.setType("boolean");
        } else if (fieldType == Double.class || fieldType == double.class) {
            fieldSchema.setType("number");
            fieldSchema.setFormat("double");
        } else if (fieldType == Float.class || fieldType == float.class) {
            fieldSchema.setType("number");
            fieldSchema.setFormat("float");
        } else if (fieldType == LocalDateTime.class) {
            fieldSchema.setType("string");
            fieldSchema.setFormat("date-time");
        } else if (fieldType == LocalDate.class) {
            fieldSchema.setType("string");
            fieldSchema.setFormat("date");
        } else if (fieldType == BigDecimal.class) {
            fieldSchema.setType("number");
            fieldSchema.setFormat("double");
        } else if (fieldType.isEnum()) {
            fieldSchema.setType("string");
            List enumValues = new ArrayList();
            for (Object enumConstant : fieldType.getEnumConstants()) {
                enumValues.add(enumConstant.toString());
            }
            fieldSchema.setEnum(enumValues);
        } else if (List.class.isAssignableFrom(fieldType)) {
            fieldSchema.setType("array");
            if (genericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                    Class<?> itemType = (Class<?>) actualTypeArguments[0];
                    if (isSimpleType(itemType) || itemType.isEnum() ||
                        itemType == LocalDateTime.class || itemType == LocalDate.class) {
                        Schema<?> itemSchema = new Schema<>();
                        if (itemType == String.class) {
                            itemSchema.setType("string");
                        } else if (itemType == Long.class || itemType == long.class) {
                            itemSchema.setType("integer");
                            itemSchema.setFormat("int64");
                        } else if (itemType == Integer.class || itemType == int.class) {
                            itemSchema.setType("integer");
                            itemSchema.setFormat("int32");
                        } else if (itemType == Boolean.class || itemType == boolean.class) {
                            itemSchema.setType("boolean");
                        } else if (itemType == Double.class || itemType == double.class) {
                            itemSchema.setType("number");
                            itemSchema.setFormat("double");
                        } else if (itemType == Float.class || itemType == float.class) {
                            itemSchema.setType("number");
                            itemSchema.setFormat("float");
                        } else if (itemType == LocalDateTime.class || itemType == LocalDate.class) {
                            itemSchema.setType("string");
                            itemSchema.setFormat("date-time");
                        }  else if (fieldType == BigDecimal.class) {
            fieldSchema.setType("number");
            fieldSchema.setFormat("double");
        } else if (itemType.isEnum()) {
                            itemSchema.setType("string");
                            List enumValues = new ArrayList();
                            for (Object enumConstant : itemType.getEnumConstants()) {
                                enumValues.add(enumConstant.toString());
                            }
                            itemSchema.setEnum(enumValues);
                        }
                        fieldSchema.setItems(itemSchema);
                    } else {
                        fieldSchema.setItems(new Schema<>().$ref("#/components/schemas/" + itemType.getSimpleName()));
                    }
                } else {
                    fieldSchema.setItems(new Schema<>().type("object"));
                }
            } else {
                fieldSchema.setItems(new Schema<>().type("object"));
            }
        } else {
            fieldSchema.set$ref("#/components/schemas/" + fieldType.getSimpleName());
        }

        return fieldSchema;
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
     * 判断是否为公开接口（不需要认证）
     */
    private boolean isPublicEndpoint(String serviceName, String methodName) {
        // auth 服务的登录、刷新token等接口不需要认证
        if ("auth".equalsIgnoreCase(serviceName)) {
            return methodName.equals("login") ||
                methodName.equals("refreshToken") ||
                methodName.equals("logout");
        }

        // applicationConfiguration 服务的接口可能不需要认证（根据实际需求调整）
        if ("applicationConfiguration".equalsIgnoreCase(serviceName)) {
            return true;
        }

        // 默认需要认证
        return false;
    }

    /**
     * 检查方法参数是否有 Swagger 注解定义
     */
    private boolean hasSwaggerParameterAnnotations(Method method) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (java.lang.reflect.Parameter param : parameters) {
            if (param.isAnnotationPresent(io.swagger.v3.oas.annotations.Parameter.class) ||
                param.isAnnotationPresent(io.swagger.v3.oas.annotations.parameters.RequestBody.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从注解构建参数 Schema
     */
    private Schema<?> buildSchemaFromAnnotations(Method method) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();

        if (parameters.length == 1) {
            java.lang.reflect.Parameter param = parameters[0];

            // 检查 @Parameter 注解
            if (param.isAnnotationPresent(io.swagger.v3.oas.annotations.Parameter.class)) {
                io.swagger.v3.oas.annotations.Parameter paramAnnotation =
                    param.getAnnotation(io.swagger.v3.oas.annotations.Parameter.class);

                Schema<?> wrapperSchema = new Schema<>();
                wrapperSchema.setType("object");
                wrapperSchema.setDescription("参数对象");

                Map<String, Schema> properties = new HashMap<>();

                // 从注解构建 Schema
                Schema<?> valueSchema = buildSchemaFromParameterAnnotation(paramAnnotation, param);
                properties.put("value", valueSchema);
                wrapperSchema.setProperties(properties);
                return wrapperSchema;
            }
        }

        // 多参数情况
        Schema<?> objectSchema = new Schema<>();
        objectSchema.setType("object");
        objectSchema.setDescription("方法参数对象");

        Map<String, Schema> properties = new HashMap<>();

        for (int i = 0; i < parameters.length; i++) {
            java.lang.reflect.Parameter param = parameters[i];
            String paramName = param.getName();
            if (paramName == null || paramName.isEmpty()) {
                paramName = "arg" + i;
            }

            if (param.isAnnotationPresent(io.swagger.v3.oas.annotations.Parameter.class)) {
                io.swagger.v3.oas.annotations.Parameter paramAnnotation =
                    param.getAnnotation(io.swagger.v3.oas.annotations.Parameter.class);
                Schema<?> paramSchema = buildSchemaFromParameterAnnotation(paramAnnotation, param);

                // 如果注解指定了名称，使用注解的名称
                if (!paramAnnotation.name().isEmpty()) {
                    paramName = paramAnnotation.name();
                }

                properties.put(paramName, paramSchema);
            } else {
                // 没有注解的参数使用自动映射
                Class<?> paramType = param.getType();
                Type genericType = param.getParameterizedType();
                properties.put(paramName, buildFieldSchema(paramType, genericType));
            }
        }

        objectSchema.setProperties(properties);
        return objectSchema;
    }

    /**
     * 从 @Parameter 注解构建 Schema
     */
    private Schema<?> buildSchemaFromParameterAnnotation(
        io.swagger.v3.oas.annotations.Parameter paramAnnotation,
        java.lang.reflect.Parameter param) {

        Schema<?> schema = new Schema<>();

        // 检查是否有 array 定义
        if (paramAnnotation.array() != null &&
            paramAnnotation.array().schema() != null &&
            !paramAnnotation.array().schema().type().isEmpty()) {

            schema.setType("array");

            // 从 @ArraySchema 注解构建 items
            io.swagger.v3.oas.annotations.media.Schema itemSchemaAnnotation = paramAnnotation.array().schema();
            Schema<?> itemSchema = new Schema<>();
            itemSchema.setType(itemSchemaAnnotation.type());

            if (!itemSchemaAnnotation.format().isEmpty()) {
                itemSchema.setFormat(itemSchemaAnnotation.format());
            }

            schema.setItems(itemSchema);

            if (!paramAnnotation.description().isEmpty()) {
                schema.setDescription(paramAnnotation.description());
            }

        } else if (paramAnnotation.schema() != null &&
            !paramAnnotation.schema().type().isEmpty()) {
            // 从 @Schema 注解构建
            io.swagger.v3.oas.annotations.media.Schema schemaAnnotation = paramAnnotation.schema();
            schema.setType(schemaAnnotation.type());

            if (!schemaAnnotation.format().isEmpty()) {
                schema.setFormat(schemaAnnotation.format());
            }

            if (!paramAnnotation.description().isEmpty()) {
                schema.setDescription(paramAnnotation.description());
            }
        } else {
            // 没有明确的 schema 定义，使用类型推断
            Class<?> paramType = param.getType();
            Type genericType = param.getParameterizedType();
            schema = buildFieldSchema(paramType, genericType);

            if (!paramAnnotation.description().isEmpty()) {
                schema.setDescription(paramAnnotation.description());
            }
        }

        return schema;
    }
}
