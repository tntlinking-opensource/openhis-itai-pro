/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用配置DTO
 * 参考ABP的ApplicationConfigurationDto，包含前端需要的所有配置信息
 */
@Data
public class ApplicationConfigurationDto {

    /**
     * 认证配置（权限策略）
     */
    private ApplicationAuthConfigurationDto auth;

    /**
     * 当前用户信息
     */
    private CurrentUserDto currentUser;

    /**
     * 当前租户信息
     */
    private CurrentTenantDto currentTenant;

    /**
     * 多租户配置
     */
    private MultiTenancyInfoDto multiTenancy;

    /**
     * 设置配置
     */
    private ApplicationSettingConfigurationDto setting;

    /**
     * 扩展属性
     */
    private Map<String, Object> extraProperties = new HashMap<>();
}
