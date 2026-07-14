/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 批量授予权限请求
 */
@Data
public class BatchGrantPermissionRequest {

    /**
     * 权限名称列表
     */
    private List<String> permissionNames;

    @NotBlank(message = "提供者名称不能为空")
    private String providerName;  // R=角色, U=用户, OU=组织单元

    @NotBlank(message = "提供者键不能为空")
    private String providerKey;  // 角色ID、用户ID等
}
