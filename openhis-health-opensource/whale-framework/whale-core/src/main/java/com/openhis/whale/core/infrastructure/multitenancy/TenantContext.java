/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

/**
 * 租户上下文接口
 */
public interface TenantContext {

    /**
     * 获取当前租户ID
     * @return 租户ID，宿主用户和系统数据为null
     */
    Long getCurrentTenantId();

    /**
     * 设置当前租户ID
     * @param tenantId 租户ID
     */
    void setCurrentTenantId(Long tenantId);

    /**
     * 清除当前租户ID
     */
    void clear();
}
