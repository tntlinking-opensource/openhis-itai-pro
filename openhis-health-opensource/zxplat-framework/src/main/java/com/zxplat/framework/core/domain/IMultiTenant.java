/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.domain;

/**
 * 多租户接口
 * 实现此接口的实体支持多租户隔离
 */
public interface IMultiTenant {

    /**
     * 获取租户ID
     * @return 租户ID，宿主用户和系统数据为null
     */
    Long getTenantId();

    /**
     * 设置租户ID
     * @param tenantId 租户ID
     */
    void setTenantId(Long tenantId);
}
