/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.multitenancy;

/**
 * 当前登录 Token 在租户下已选医院的跨请求持久化（HIS {@code adm_organization.id}）。
 *
 * <p>键为 JWT 登录会话实例，同一用户多 Token 互不覆盖。</p>
 */
public interface HospitalSessionStore {

    /**
     * 读取当前登录会话已选医院。
     *
     * @param userId   用户主键
     * @param tenantId 租户主键
     * @return 医院组织 id
     */
    Long get(Long userId, Long tenantId);

    /**
     * 保存当前登录会话选择的医院。
     *
     * @param userId                 用户主键
     * @param tenantId               租户主键
     * @param hospitalOrganizationId 医院组织 id
     */
    void put(Long userId, Long tenantId, Long hospitalOrganizationId);

    /**
     * 清除当前登录会话的医院选择。
     *
     * @param userId   用户主键
     * @param tenantId 租户主键
     */
    void remove(Long userId, Long tenantId);
}
