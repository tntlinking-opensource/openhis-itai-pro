/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import com.zxplat.framework.core.application.dto.ApplicationConfigurationDto;
import lombok.Data;

/**
 * 代理登录响应
 */
@Data
public class ImpersonateResponse {

    /**
     * 访问Token
     */
    private String accessToken;

    /**
     * 刷新Token
     */
    private String refreshToken;

    /**
     * Token过期时间（秒）
     */
    private Long expiresIn;

    /**
     * 被代理的用户信息
     */
    private UserDto user;

    /**
     * 原始用户信息（谁在代理）
     */
    private UserDto impersonator;

    /**
     * 应用配置（权限、currentUser 含 impersonator 字段，供前端切换会话）
     */
    private ApplicationConfigurationDto configuration;
}
