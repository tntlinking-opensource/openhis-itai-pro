/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import lombok.Data;

/**
 * 更新角色请求
 */
@Data
public class UpdateRoleRequest {

    private Long id;  // 角色ID
    private String displayName;
    private String description;
    private Boolean isDefault;
}
