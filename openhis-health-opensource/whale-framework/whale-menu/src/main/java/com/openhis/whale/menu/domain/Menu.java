/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.menu.domain;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单实体
 */
@Entity
@Table(name = "whale_menus", indexes = {
    @Index(name = "idx_menus_code", columnList = "code"),
    @Index(name = "idx_menus_parent_id", columnList = "parent_id"),
    @Index(name = "idx_menus_tenant_id", columnList = "tenant_id")
})
@Getter
@Setter
public class Menu extends BaseAuditedEntity implements IMultiTenant {

    /**
     * 菜单代码（唯一）
     */
    @Column(name = "code", nullable = false, length = 128, unique = true)
    private String code;

    /**
     * 菜单名称
     */
    @Column(name = "name", nullable = false, length = 256)
    private String name;

    /**
     * 菜单显示名称
     */
    @Column(name = "display_name", length = 256)
    private String displayName;

    /**
     * 菜单图标
     */
    @Column(name = "icon", length = 128)
    private String icon;

    /**
     * 菜单路径（路由路径）
     */
    @Column(name = "path", length = 512)
    private String path;

    /**
     * 组件路径（前端组件路径）
     */
    @Column(name = "component", length = 512)
    private String component;

    /**
     * 权限名称（关联权限系统）
     */
    @Column(name = "permission_name", length = 256)
    private String permissionName;

    /**
     * 父菜单ID（树形结构）
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 排序号
     */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /**
     * 是否启用
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    /**
     * 是否隐藏
     */
    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden = false;

    /**
     * 是否外链
     */
    @Column(name = "is_external", nullable = false)
    private Boolean isExternal = false;

    /**
     * 外链地址
     */
    @Column(name = "external_url", length = 512)
    private String externalUrl;

    /**
     * 菜单类型（目录、菜单、按钮）
     */
    @Column(name = "menu_type", length = 32, nullable = false)
    private String menuType = "menu"; // directory, menu, button

    /**
     * 备注
     */
    @Column(name = "remark", length = 512)
    private String remark;

    @Column(name = "keep_alive")
    private Boolean keepAlive;



    /**
     * 父菜单（关联）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Menu parent;
}
