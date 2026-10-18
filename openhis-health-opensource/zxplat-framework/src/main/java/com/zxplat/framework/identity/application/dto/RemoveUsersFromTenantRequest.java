/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import lombok.Data;
import java.util.List;

/**
 * 从租户移除用户请求
 */
@Data
public class RemoveUsersFromTenantRequest {

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 用户ID列表
     */
    private List<Long> userIds;
}
