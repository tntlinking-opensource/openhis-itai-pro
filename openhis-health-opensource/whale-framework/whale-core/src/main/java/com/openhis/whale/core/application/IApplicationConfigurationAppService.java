/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application;

import com.openhis.whale.core.application.dto.ApplicationConfigurationDto;
import com.openhis.whale.core.application.dto.CurrentUserDto;

/**
 * 应用配置服务接口
 * 提供前端需要的所有配置信息
 */
public interface IApplicationConfigurationAppService {

    /**
     * 获取应用配置
     * 包含前端需要的所有配置信息：权限、用户信息、租户信息等
     */
    ApplicationConfigurationDto get();

    /**
     * 获取应用配置（指定用户ID和租户ID）
     * 用于登录时获取配置，此时用户还未设置到SecurityContext
     */
    ApplicationConfigurationDto get(Long userId, Long tenantId);

    /**
     * 向 currentUser 写入代理者信息（登录响应尚未切换 SecurityContext 时使用）。
     *
     * @param userDto 应用配置中的当前用户
     * @param impersonatorUserId 代理者用户 ID
     * @param impersonatorUserName 代理者用户名
     * @param impersonatorTenantId 代理者租户 ID，可为 null
     */
    void applyImpersonatorFields(
            CurrentUserDto userDto,
            Long impersonatorUserId,
            String impersonatorUserName,
            Long impersonatorTenantId);
}
