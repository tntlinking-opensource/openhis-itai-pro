/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.menu.application.support;

import com.zxplat.framework.core.application.util.SpecificationUtils;
import com.zxplat.framework.menu.domain.Menu;
import com.zxplat.framework.menu.infrastructure.repository.MenuRepository;
import com.zxplat.framework.permission.application.dto.PermissionDto;
import com.zxplat.framework.permission.application.support.NavigationPermissionNodeNames;
import com.zxplat.framework.permission.application.support.NavigationPermissionTreeProvider;
import com.zxplat.framework.permission.domain.Permission;
import com.zxplat.framework.permission.infrastructure.repository.PermissionRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按启用且未隐藏菜单树构建权限选择树：目录为不可授权分组，叶子菜单对应 {@code Navigation.{Code}} 权限。
 */
@Component
public class MenuAlignedNavigationPermissionTreeProvider implements NavigationPermissionTreeProvider {

    private static final String DIRECTORY_MENU_TYPE = "directory";

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Override
    public List<PermissionDto> buildMenuAlignedTree() {
        List<Menu> menus = loadMenusForTree();
        Map<String, Permission> permissionByName = loadNavigationPermissions();
        Set<String> attachedPermissionNames = new HashSet<>();
        Map<Long, List<Menu>> childrenByParentId = groupChildrenByParent(menus);

        List<PermissionDto> roots = new ArrayList<>();
        for (Menu root : childrenByParentId.getOrDefault(0L, List.of())) {
            PermissionDto node = toTreeNode(root, childrenByParentId, permissionByName, attachedPermissionNames);
            if (node != null) {
                roots.add(node);
            }
        }

        return roots;
    }

    private List<Menu> loadMenusForTree() {
        Specification<Menu> spec = SpecificationUtils.<Menu>tenantFilter()
            .and(SpecificationUtils.<Menu>notDeleted())
            .and((root, query, cb) -> cb.equal(root.get("isEnabled"), true))
            .and((root, query, cb) -> cb.equal(root.get("isHidden"), false));
        List<Menu> menus = menuRepository.findAll(spec);
        menus.sort(menuSortOrder());
        return menus;
    }

    private Map<String, Permission> loadNavigationPermissions() {
        Specification<Permission> spec = SpecificationUtils.<Permission>tenantFilter()
            .and(SpecificationUtils.<Permission>notDeleted());
        List<Permission> permissions = permissionRepository.findAll(spec);
        Map<String, Permission> map = new HashMap<>();
        for (Permission permission : permissions) {
            if (permission == null || permission.getName() == null) {
                continue;
            }
            if (!permission.getName().startsWith(NavigationPermissionNodeNames.ROOT)) {
                continue;
            }
            if (NavigationPermissionNodeNames.isFolderNodeName(permission.getName())) {
                continue;
            }
            map.putIfAbsent(permission.getName(), permission);
        }
        return map;
    }

    private Map<Long, List<Menu>> groupChildrenByParent(List<Menu> menus) {
        Map<Long, List<Menu>> childrenByParentId = new HashMap<>();
        for (Menu menu : menus) {
            if (menu == null || menu.getId() == null) {
                continue;
            }
            Long parentKey = menu.getParentId() != null ? menu.getParentId() : 0L;
            childrenByParentId.computeIfAbsent(parentKey, key -> new ArrayList<>()).add(menu);
        }
        for (List<Menu> siblings : childrenByParentId.values()) {
            siblings.sort(menuSortOrder());
        }
        return childrenByParentId;
    }

    private PermissionDto toTreeNode(
        Menu menu,
        Map<Long, List<Menu>> childrenByParentId,
        Map<String, Permission> permissionByName,
        Set<String> attachedPermissionNames) {
        if (menu == null || menu.getId() == null) {
            return null;
        }
        List<Menu> childMenus = childrenByParentId.getOrDefault(menu.getId(), List.of());
        if (isDirectory(menu)) {
            return buildFolderNode(menu, childMenus, childrenByParentId, permissionByName, attachedPermissionNames);
        }
        return buildLeafOrPromotedNode(menu, childMenus, childrenByParentId, permissionByName, attachedPermissionNames);
    }

    private PermissionDto buildFolderNode(
        Menu menu,
        List<Menu> childMenus,
        Map<Long, List<Menu>> childrenByParentId,
        Map<String, Permission> permissionByName,
        Set<String> attachedPermissionNames) {
        List<PermissionDto> children = new ArrayList<>();
        for (Menu child : childMenus) {
            PermissionDto childNode = toTreeNode(child, childrenByParentId, permissionByName, attachedPermissionNames);
            if (childNode != null) {
                children.add(childNode);
            }
        }
        if (children.isEmpty()) {
            return null;
        }
        PermissionDto folder = new PermissionDto();
        folder.setName(NavigationPermissionNodeNames.folderNodeName(menu.getId()));
        folder.setDisplayName(resolveMenuDisplayName(menu));
        folder.setParentName(resolveParentPermissionName(menu));
        folder.setGrantable(Boolean.FALSE);
        folder.setChildren(children);
        return folder;
    }

    private PermissionDto buildLeafOrPromotedNode(
        Menu menu,
        List<Menu> childMenus,
        Map<Long, List<Menu>> childrenByParentId,
        Map<String, Permission> permissionByName,
        Set<String> attachedPermissionNames) {
        List<PermissionDto> childNodes = new ArrayList<>();
        for (Menu child : childMenus) {
            PermissionDto childNode = toTreeNode(child, childrenByParentId, permissionByName, attachedPermissionNames);
            if (childNode != null) {
                childNodes.add(childNode);
            }
        }

        String permissionName = resolveMenuPermissionName(menu);
        PermissionDto selfNode = null;
        if (permissionName != null && MenuGrantability.isGrantableMenuEntry(menu)) {
            Permission permission = permissionByName.get(permissionName);
            if (permission != null) {
                selfNode = toDto(permission);
                selfNode.setGrantable(Boolean.TRUE);
                selfNode.setDisplayName(resolveMenuDisplayName(menu));
                attachedPermissionNames.add(permissionName);
            }
        }

        if (selfNode != null && !childNodes.isEmpty()) {
            selfNode.setChildren(childNodes);
            return selfNode;
        }
        if (selfNode != null) {
            return selfNode;
        }
        if (childNodes.isEmpty()) {
            return null;
        }
        if (childNodes.size() == 1) {
            return childNodes.get(0);
        }
        PermissionDto wrapper = new PermissionDto();
        wrapper.setName(NavigationPermissionNodeNames.folderNodeName(menu.getId()));
        wrapper.setDisplayName(resolveMenuDisplayName(menu));
        wrapper.setGrantable(Boolean.FALSE);
        wrapper.setChildren(childNodes);
        return wrapper;
    }

    private String resolveMenuPermissionName(Menu menu) {
        if (menu.getPermissionName() != null && !menu.getPermissionName().isBlank()) {
            return menu.getPermissionName().trim();
        }
        if (menu.getCode() == null || menu.getCode().isBlank()) {
            return null;
        }
        return MenuNavigationPermissionNames.menuPermissionName(menu.getCode());
    }

    private String resolveParentPermissionName(Menu menu) {
        if (menu.getParentId() == null) {
            return null;
        }
        return NavigationPermissionNodeNames.folderNodeName(menu.getParentId());
    }

    private boolean isDirectory(Menu menu) {
        String menuType = menu.getMenuType();
        return menuType != null && DIRECTORY_MENU_TYPE.equalsIgnoreCase(menuType.trim());
    }

    private String resolveMenuDisplayName(Menu menu) {
        if (menu.getDisplayName() != null && !menu.getDisplayName().isBlank()) {
            return menu.getDisplayName().trim();
        }
        if (menu.getName() != null && !menu.getName().isBlank()) {
            return menu.getName().trim();
        }
        return menu.getCode();
    }

    private static Comparator<Menu> menuSortOrder() {
        return Comparator
            .comparing(Menu::getSortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(Menu::getId, Comparator.nullsLast(Long::compareTo));
    }

    private static PermissionDto toDto(Permission permission) {
        PermissionDto dto = new PermissionDto();
        BeanUtils.copyProperties(permission, dto);
        return dto;
    }
}
