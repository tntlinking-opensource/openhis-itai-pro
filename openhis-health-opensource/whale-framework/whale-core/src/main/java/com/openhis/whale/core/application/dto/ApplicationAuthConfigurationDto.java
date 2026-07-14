/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用认证配置DTO
 * 包含用户拥有的权限策略
 */
@Data
public class ApplicationAuthConfigurationDto {

    /**
     * 已授予的权限策略
     * Key: 权限名称，Value: true表示已授予
     */
    private Map<String, Boolean> grantedPolicies = new HashMap<>();
}
