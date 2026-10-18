/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.permission.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 授予权限请求
 */
@Data
public class GrantPermissionRequest {

    @NotBlank(message = "权限名称不能为空")
    private String permissionName;

    @NotBlank(message = "提供者名称不能为空")
    private String providerName;  // R=角色, U=用户, OU=组织单元

    @NotBlank(message = "提供者键不能为空")
    private String providerKey;  // 角色ID、用户ID等
}
