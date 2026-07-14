/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 租户工具类
 */
@Component
public class TenantUtils {

    private static TenantContext tenantContext;

    @Autowired
    public void setTenantContext(TenantContext tenantContext) {
        TenantUtils.tenantContext = tenantContext;
    }

    /**
     * 获取当前租户ID
     * @return 租户ID，宿主用户和系统数据为null
     */
    public static Long getCurrentTenantId() {
        return tenantContext != null ? tenantContext.getCurrentTenantId() : null;
    }

    /**
     * 判断是否为宿主用户（租户ID为null）
     * @return true表示是宿主用户
     */
    public static boolean isHost() {
        return getCurrentTenantId() == null;
    }
}
