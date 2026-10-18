/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.permission.application.dto;

import lombok.Data;

/**
 * 更新权限请求
 */
@Data
public class UpdatePermissionRequest {

    private String displayName;

    private String parentName;

    private String description;
}
