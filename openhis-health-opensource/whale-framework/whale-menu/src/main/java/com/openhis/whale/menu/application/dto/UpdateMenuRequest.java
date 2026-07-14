/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.menu.application.dto;

import lombok.Data;

/**
 * 更新菜单请求
 */
@Data
public class UpdateMenuRequest {

    private String name;
    private String displayName;
    private String icon;
    private String path;
    private String component;
    private String permissionName;
    /**
     * 为 true 时表示将菜单挂到根（清空 parent_id）。与「请求体未带 parentId」区分，避免误清除。
     */
    private Boolean clearParent;
    private Long parentId;
    private Integer sortOrder;
    private Boolean isEnabled;
    private Boolean isHidden;
    private Boolean isExternal;
    private String externalUrl;
    private String menuType;
    private String remark;
    private Boolean keepAlive;
}
