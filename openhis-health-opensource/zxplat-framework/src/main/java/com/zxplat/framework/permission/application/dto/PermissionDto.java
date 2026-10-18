/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.permission.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限DTO
 */
@Data
public class PermissionDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String displayName;
    private String parentName;
    private String description;
    private Long tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private List<PermissionDto> children;  // 子权限
    /** 是否可授予角色；菜单目录分组节点为 {@code false}。 */
    private Boolean grantable;
}
