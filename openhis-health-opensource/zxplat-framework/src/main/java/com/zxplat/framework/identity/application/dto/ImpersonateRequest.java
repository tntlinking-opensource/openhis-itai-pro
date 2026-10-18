/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import lombok.Data;

/**
 * 代理登录请求
 */
@Data
public class ImpersonateRequest {

    /**
     * 被代理的用户ID
     */
    private Long userId;

    /**
     * 被代理的租户ID（可选）
     */
    private Long tenantId;

    /**
     * 代理原因（可选，写入安全审计）
     */
    private String reason;
}
