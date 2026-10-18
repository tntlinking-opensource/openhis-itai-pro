/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.web.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自定义 Swagger UI 索引页面转换器
 * 确保 Swagger UI 加载正确的 API 文档 URL
 * <p>prod 关闭 springdoc 时本配置不加载，避免强依赖 SwaggerUiConfigProperties 导致启动失败。</p>
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class CustomSwaggerIndexTransformer {

    private static final Logger logger = LoggerFactory.getLogger(CustomSwaggerIndexTransformer.class);

    @Autowired
    private SwaggerUiConfigProperties swaggerUiConfigProperties;

    @Autowired
    private SwaggerUiOAuthProperties swaggerUiOAuthProperties;

    @Autowired
    private SwaggerWelcomeCommon swaggerWelcomeCommon;

    @Autowired
    private ObjectMapperProvider objectMapperProvider;

    /**
     * 创建自定义的 SwaggerIndexTransformer
     * 确保 Swagger UI 加载我们的 API 文档而不是默认的 Petstore
     */
    @Bean
    @Primary
    public SwaggerIndexPageTransformer swaggerIndexTransformer() {
        return new SwaggerIndexPageTransformer(
                swaggerUiConfigProperties,
                swaggerUiOAuthProperties,
                swaggerWelcomeCommon,
                objectMapperProvider) {
            @Override
            public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain transformerChain) {
                try {
                    Resource transformed = super.transform(request, resource, transformerChain);

                    // 如果资源是 index.html 或 swagger-initializer.js，进行自定义转换
                    String filename = resource.getFilename();
                    if (filename != null && (filename.contains("index.html") || filename.contains("swagger-initializer.js"))) {
                        try {
                            // 读取资源内容
                            byte[] bytes;
                            try (java.io.InputStream inputStream = transformed.getInputStream()) {
                                bytes = inputStream.readAllBytes();
                            }
                            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

                            logger.debug("开始转换 Swagger UI 资源: {}，原始内容长度: {}", filename, content.length());

                            // 确保 URL 指向我们的 API 文档
                            // 替换任何默认的 Petstore URL
                            if (content.contains("https://petstore.swagger.io")) {
                                content = content.replace("https://petstore.swagger.io/v2/swagger.json", "/v3/api-docs");
                                logger.debug("已替换 Petstore URL");
                            }

                            // 确保 configUrl 正确
                            if (content.contains("configUrl") && !content.contains("\"configUrl\" : \"/v3/api-docs/swagger-config\"")) {
                                content = content.replaceAll("\"configUrl\"\\s*:\\s*\"[^\"]*\"", "\"configUrl\" : \"/v3/api-docs/swagger-config\"");
                                logger.debug("已更新 configUrl");
                            }

                            // 查找并替换 url 配置
                            // 在 swagger-initializer.js 中，格式是: url: "",
                            // 需要替换为: url: "/v3/api-docs",

                            // 首先替换空的 url: ""
                            if (content.contains("url: \"\"")) {
                                content = content.replace("url: \"\"", "url: \"/v3/api-docs\"");
                                logger.debug("已替换空的 url 配置");
                            }

                            // 替换 url: undefined
                            if (content.contains("url: undefined")) {
                                content = content.replace("url: undefined", "url: \"/v3/api-docs\"");
                                logger.debug("已替换 undefined url 配置");
                            }

                            // 如果还是没有 url 配置，在 SwaggerUIBundle 配置对象中添加
                            if (!content.contains("url: \"/v3/api-docs\"")) {
                                // 查找 SwaggerUIBundle({ 并在第一个配置项位置添加 url
                                content = content.replaceFirst("(SwaggerUIBundle\\(\\{)", "$1url: \"/v3/api-docs\",");
                                logger.debug("已在 SwaggerUIBundle 配置开始处添加 url");
                            }

                            // 如果使用 urls 数组格式，也需要处理
                            if (content.contains("urls:") && !content.contains("url: \"/v3/api-docs\"")) {
                                // 替换 urls 数组为空，改为使用单个 url
                                content = content.replaceAll("urls:\\s*\\[[^\\]]*\\]", "url: \"/v3/api-docs\"");
                            }

                            logger.debug("转换完成，最终内容长度: {}", content.length());
                            logger.debug("是否包含 /v3/api-docs: {}", content.contains("/v3/api-docs"));

                            // 创建新的资源，重写 lastModified 方法以避免 FileNotFoundException
                            final String finalFilename = filename;
                            @SuppressWarnings("null")
                            byte[] contentBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            return new org.springframework.core.io.ByteArrayResource(contentBytes) {
                                @Override
                                public String getFilename() {
                                    return finalFilename;
                                }

                                @Override
                                public long lastModified() throws IOException {
                                    // 返回当前时间，避免需要文件路径
                                    return System.currentTimeMillis();
                                }

                                @Override
                                @SuppressWarnings("null")
                                public java.io.File getFile() throws IOException {
                                    // 抛出异常，表示这不是一个文件资源
                                    throw new java.io.FileNotFoundException("Byte array resource cannot be resolved to file");
                                }
                            };
                        } catch (IOException e) {
                            // 如果转换失败，记录日志并返回原始资源
                            logger.warn("转换 Swagger UI index.html 时发生错误: {}", e.getMessage());
                            return transformed;
                        } catch (Exception e) {
                            // 捕获其他异常
                            logger.error("转换 Swagger UI index.html 时发生未知错误", e);
                            return transformed;
                        }
                    }

                    return transformed;
                } catch (IOException e) {
                    // 如果父类转换失败，记录日志并返回原始资源
                    logger.warn("父类转换 Swagger UI 资源时发生错误: {}", e.getMessage());
                    return resource;
                } catch (Exception e) {
                    // 捕获其他异常
                    logger.error("处理 Swagger UI 资源时发生未知错误", e);
                    return resource;
                }
            }
        };
    }
}
