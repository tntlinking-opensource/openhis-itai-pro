/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import lombok.Data;

/**
 * 选择租户请求（写入服务端会话，业务 API 不再依赖客户端请求头）。
 */
@Data
public class SelectTenantRequest {

    /**
     * 租户主键
     */
    private Long tenantId;
}
