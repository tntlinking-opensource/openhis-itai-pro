/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.menu.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建菜单请求
 */
@Data
public class CreateMenuRequest {

    @NotBlank(message = "菜单代码不能为空")
    @Size(min = 2, max = 128, message = "菜单代码长度必须在2-128之间")
    private String code;

    @NotBlank(message = "菜单名称不能为空")
    @Size(min = 1, max = 256, message = "菜单名称长度必须在1-256之间")
    private String name;

    private String displayName;
    private String icon;
    private String path;
    private String component;
    private String permissionName;
    private Long parentId;
    private Integer sortOrder = 0;
    private Boolean isEnabled = true;
    private Boolean isHidden = false;
    private Boolean isExternal = false;
    private String externalUrl;
    private String menuType = "menu"; // directory, menu, button
    private String remark;
    private Boolean keepAlive;
}
