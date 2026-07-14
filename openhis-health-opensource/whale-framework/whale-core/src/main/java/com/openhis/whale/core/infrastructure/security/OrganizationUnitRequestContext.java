/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.security;

/**
 * 当前 HTTP 请求内的科室（组织单元）上下文。
 *
 * <p>仅在本请求有效，必须在 {@link OrganizationUnitContextInterceptor#afterCompletion} 中清理，
 * 禁止作为跨请求缓存使用。</p>
 */
public final class OrganizationUnitRequestContext {

    private static final ThreadLocal<Long> REQUEST_ORG_UNIT_ID = new ThreadLocal<>();

    private OrganizationUnitRequestContext() {
    }

    /**
     * 获取当前请求已注入的科室 ID。
     */
    public static Long getOrganizationUnitId() {
        return REQUEST_ORG_UNIT_ID.get();
    }

    /**
     * 为当前请求设置科室 ID（拦截器 hydrate 或 selectOrganizationUnit 写入）。
     */
    public static void setOrganizationUnitId(Long organizationUnitId) {
        if (organizationUnitId == null) {
            REQUEST_ORG_UNIT_ID.remove();
        } else {
            REQUEST_ORG_UNIT_ID.set(organizationUnitId);
        }
    }

    /**
     * 清除当前请求的科室上下文（请求结束必须调用）。
     */
    public static void clear() {
        REQUEST_ORG_UNIT_ID.remove();
    }
}
