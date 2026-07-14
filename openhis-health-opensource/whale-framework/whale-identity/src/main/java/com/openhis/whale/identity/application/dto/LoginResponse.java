/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import com.openhis.whale.core.application.dto.ApplicationConfigurationDto;
import lombok.Data;

/**
 * 登录响应
 */
@Data
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserDto user;

    /** 登录方式：PASSWORD 或 CA */
    private String loginMethod = "PASSWORD";

    /**
     * 应用配置信息（参考ABP实现）
     * 包含前端需要的所有配置：权限、用户信息、租户信息等
     */
    private ApplicationConfigurationDto configuration;
}
