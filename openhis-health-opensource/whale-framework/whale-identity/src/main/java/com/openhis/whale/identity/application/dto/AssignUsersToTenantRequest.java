/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import lombok.Data;
import java.util.List;

/**
 * 分配用户到租户请求
 */
@Data
public class AssignUsersToTenantRequest {

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 用户ID列表
     */
    private List<Long> userIds;
}
