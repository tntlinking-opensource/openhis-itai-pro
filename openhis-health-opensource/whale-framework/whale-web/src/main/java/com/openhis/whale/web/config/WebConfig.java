/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.web.config;

import com.openhis.whale.core.infrastructure.multitenancy.RequireTenantContextInterceptor;
import com.openhis.whale.core.infrastructure.multitenancy.TenantInterceptor;
import com.openhis.whale.core.infrastructure.security.OrganizationUnitContextInterceptor;
import com.openhis.whale.core.infrastructure.web.ApiAccessLogInterceptor;
import com.openhis.whale.core.infrastructure.web.ApiVersionInterceptor;
import com.openhis.whale.core.infrastructure.web.RateLimitInterceptor;
import com.openhis.whale.core.infrastructure.web.RequestIdInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TenantInterceptor tenantInterceptor;

    @Autowired
    private RequireTenantContextInterceptor requireTenantContextInterceptor;

    @Autowired
    private OrganizationUnitContextInterceptor organizationUnitContextInterceptor;

    @Autowired
    private RequestIdInterceptor requestIdInterceptor;

    @Autowired
    private ApiAccessLogInterceptor apiAccessLogInterceptor;

    @Autowired(required = false)
    private RateLimitInterceptor rateLimitInterceptor;

    @Autowired(required = false)
    private ApiVersionInterceptor apiVersionInterceptor;

    @Autowired(required = false)
    private SwaggerDiagnosticInterceptor swaggerDiagnosticInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 请求ID拦截器（最先执行）
        registry.addInterceptor(requestIdInterceptor)
                .addPathPatterns("/**")
                .order(0);

        // API版本拦截器（可选）
        if (apiVersionInterceptor != null) {
            registry.addInterceptor(apiVersionInterceptor)
                    .addPathPatterns("/api/**")
                    .order(1);
        }

        // 限流拦截器（可选，生产环境建议启用）
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns(
                        "/api/auth/**",
                        "/api/app/auth/**",
                        // 长任务进度轮询：避免 500ms/2s 轮询打满每分钟配额返回 429
                        "/**/getApplyNoticeDocProgress",
                        "/**/getScanNoticeCanonicalDirProgress",
                        "/**/getConfirmNoticeScanImportProgress",
                        "/**/getCatalogDownloadProgress",
                        "/**/getCatalogImportProgress",
                        "/**/getImportMedicationCatalogProgress"
                    )
                    .order(2);
        }

        // 租户拦截器（只排除不需要租户的认证接口）
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/health",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/app/auth/login",
                    "/api/app/auth/refreshToken",
                    "/api/app/auth/logout",
                    "/api/app/auth/stopImpersonating",
                    "/api/app/auth/getAvailableTenants",  // 获取租户列表不需要租户ID
                    "/api/app/auth/selectTenant",       // 选择租户写入会话，本身不依赖已有租户
                    "/api/app/tenant/**"                // 租户管理：列表/创建/更新/删除租户不依赖当前租户上下文
                )
                .order(3);

        // 科室上下文：在租户与用户身份就绪后，每请求从 SessionStore 注入并在结束时清理
        registry.addInterceptor(organizationUnitContextInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/health",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/app/auth/login",
                    "/api/app/auth/refreshToken",
                    "/api/app/auth/logout",
                    "/api/app/auth/stopImpersonating",
                    "/api/app/auth/getAvailableTenants",
                    "/api/app/auth/selectTenant",
                    "/api/app/tenant/**"
                )
                .order(4);

        // 业务 API 必须携带有效租户上下文（在 TenantInterceptor 之后执行）
        registry.addInterceptor(requireTenantContextInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/health",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/app/auth/login",
                    "/api/app/auth/refreshToken",
                    "/api/app/auth/logout",
                    "/api/app/auth/stopImpersonating",
                    "/api/app/auth/getAvailableTenants",
                    "/api/app/auth/selectTenant",
                    "/api/app/tenant/**"
                )
                .order(5);

        // API 访问摘要（须在租户/用户上下文就绪后执行）
        registry.addInterceptor(apiAccessLogInterceptor)
                .addPathPatterns("/api/**")
                .order(6);

        // Swagger诊断拦截器
        if (swaggerDiagnosticInterceptor != null) {
            registry.addInterceptor(swaggerDiagnosticInterceptor)
                    .addPathPatterns("/swagger-ui/**", "/v3/api-docs/**", "/swagger-config")
                    .order(4);
        }
    }

    // 注意：不需要手动添加重定向
    // SpringDoc 会自动处理 /swagger-ui.html 到 /swagger-ui/index.html 的重定向
    // 如果添加反向重定向会导致循环重定向错误
}
