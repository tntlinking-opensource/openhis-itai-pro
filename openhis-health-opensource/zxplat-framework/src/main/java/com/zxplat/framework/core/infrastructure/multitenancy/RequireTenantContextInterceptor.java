/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.multitenancy;

import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 业务 API 租户上下文校验：除白名单外，{@code /api/**} 必须已通过 {@link TenantInterceptor} 注入租户 ID。
 * <p>防止未选租户时写入 {@code tenant_id=null} 或读到全库数据。</p>
 */
@Component
public class RequireTenantContextInterceptor implements HandlerInterceptor {

    private static final String TENANT_REQUIRED_CODE = "TENANT_REQUIRED";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (TenantUtils.getCurrentTenantId() != null) {
            return true;
        }
        throw new BusinessException(TENANT_REQUIRED_CODE, "请先选择租户后再操作");
    }
}
