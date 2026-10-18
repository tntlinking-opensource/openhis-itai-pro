/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.permission.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 权限授予信息DTO
 */
@Data
public class PermissionGrantInfoDto {

    /**
     * 权限名称
     */
    private String permissionName;

    /**
     * 提供者名称（R=角色, U=用户, OU=组织单元）
     */
    private String providerName;

    /**
     * 提供者键（角色ID、用户ID等）
     */
    private String providerKey;

    /**
     * 提供者显示名称（角色名称、用户名等）
     */
    private String providerDisplayName;

    /**
     * 租户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;
}
