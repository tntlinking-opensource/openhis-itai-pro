/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger 配置
 */
@Configuration
public class OpenApiConfig {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiConfig.class);

    @Bean
    public OpenAPI whaleOpenAPI() {
        logger.info("========== 初始化 OpenAPI 配置 ==========");

        // 配置安全方案：Bearer Token (JWT)
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer Token 认证。请先通过 /api/auth/login 接口获取 token，然后在右上角点击 'Authorize' 按钮输入 token。");


        SecurityScheme tenantIdHeader = new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-Tenant-Id")
            .description("租户ID。多租户系统必填项，通过此请求头指定当前操作的租户。\n\n" +
                "获取方式：\n" +
                "1. 登录后从用户信息中获取默认租户ID\n" +
                "2. 调用 /api/app/auth/getAvailableTenants 获取可用租户列表");

        Components components = new Components()
            .addSecuritySchemes("Bearer Authentication", bearerAuth)
            .addSecuritySchemes("X-Tenant-Id", tenantIdHeader);

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Whale Framework API")
                        .description("Whale 框架 RESTful API 文档\n\n" +
                                "## 认证说明\n" +
                                "大部分接口需要 JWT Bearer Token 认证。\n" +
                                "1. 使用 `/api/auth/login` 接口登录获取 token\n" +
                                "2. 在 Swagger UI 右上角点击 'Authorize' 按钮\n" +
                                "3. 输入 `Bearer {token}` 或直接输入 `{token}`\n" +
                                "4. 点击 'Authorize' 完成认证\n\n" +
                                "## 接口说明\n" +
                                "- `/api/app/*` 路径下的接口为动态生成的 AppService 接口\n" +
                                "- 其他路径为标准的 REST API 接口")
                        .version("2.0.1")
                        .contact(new Contact()
                                .name("Whale Framework")
                                .email("support@openhis.com"))
                        .license(new License()
                                .name("LGPL-3.0-or-later")
                                .url("https://www.gnu.org/licenses/lgpl-3.0.html")))
                .components(components);

        // 注意：不在这里添加全局安全要求，让各个接口自己决定是否需要认证
        // 这样登录接口等公开接口就不需要认证了

        logger.info("OpenAPI 配置初始化完成，已配置 Bearer Token 认证");
        return openAPI;
    }

    /**
     * 显式注册 DynamicOpenApiCustomizer 作为 OpenApiCustomizer Bean
     * 确保 SpringDoc 能够发现并调用它
     *
     * 注意：SpringDoc 会自动发现所有 OpenApiCustomizer 类型的 Bean。
     * 由于 DynamicOpenApiCustomizer 已经通过 @Component 注册，这里不需要再次注册。
     * 但为了确保优先级，我们使用 @Primary 注解。
     */
    // 注意：DynamicOpenApiCustomizer 已经通过 @Component 注册，不需要在这里再次注册
    // SpringDoc 会自动发现并调用所有 OpenApiCustomizer 类型的 Bean
}
