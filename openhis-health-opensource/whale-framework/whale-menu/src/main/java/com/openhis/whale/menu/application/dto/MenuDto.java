/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.menu.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单DTO
 */
@Data
public class MenuDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String code;
    private String name;
    private String displayName;
    private String icon;
    private String path;
    private String component;
    private String permissionName;
    private Long parentId;
    private String parentDisplayName;
    private Integer sortOrder;
    private Boolean isEnabled;
    private Boolean isHidden;
    private Boolean isExternal;
    private String externalUrl;
    private String menuType;
    private String remark;
    private Long tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private Boolean keepAlive;

    /**
     * 子菜单列表（树形结构）
     */
    private List<MenuDto> children;
}
