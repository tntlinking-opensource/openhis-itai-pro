/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.menu.application;

import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.PageRequestUtils;
import com.zxplat.framework.core.application.util.SpecificationUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.infrastructure.permission.RequirePermission;
import com.zxplat.framework.core.infrastructure.permission.IPermissionChecker;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import com.zxplat.framework.identity.domain.Role;
import com.zxplat.framework.identity.domain.UserRole;
import com.zxplat.framework.identity.infrastructure.repository.RoleRepository;
import com.zxplat.framework.identity.infrastructure.repository.UserRoleRepository;
import com.zxplat.framework.identity.application.RolePlatformConstants;
import com.zxplat.framework.identity.application.dto.IdRequest;
import com.zxplat.framework.menu.application.dto.*;
import com.zxplat.framework.menu.application.support.MenuViewPermissionProvisioner;
import com.zxplat.framework.menu.domain.Menu;
import com.zxplat.framework.menu.infrastructure.repository.MenuRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单应用服务
 */
@Service
public class MenuAppService {

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CurrentOperatorResolver currentOperatorResolver;

    @Autowired
    private IPermissionChecker permissionChecker;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MenuViewPermissionProvisioner menuViewPermissionProvisioner;

    /**
     * 根据ID获取菜单
     */
    @RequirePermission("Navigation.MenuManagement")
    public MenuDto getById(IdRequest request) {
        Menu menu = menuRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("菜单", request.getId()));
        return toDto(menu);
    }

    /**
     * 获取菜单列表（树形结构，菜单管理用，含隐藏/停用项）
     */
    @RequirePermission("Navigation.MenuManagement")
    public List<MenuDto> getTree() {
        // 应用租户过滤和软删除过滤
        Specification<Menu> spec = SpecificationUtils.<Menu>tenantFilter()
                .and(SpecificationUtils.<Menu>notDeleted())
                .and((root, query, cb) -> cb.isNull(root.get("parentId")));

        List<Menu> rootMenus = menuRepository.findAll(spec);
        // 手动排序
        rootMenus.sort((a, b) -> {
            int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
            int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
            return Integer.compare(orderA, orderB);
        });

        return rootMenus.stream()
                .map(this::toDtoWithChildren)
                .collect(Collectors.toList());
    }

    /**
     * 角色授权用菜单树：仅启用且未隐藏的页面（权限与可见页面一一对应）。
     */
    @RequirePermission("Navigation.RoleManagement")
    public List<MenuDto> getGrantTree() {
        List<Menu> menus = loadEnabledMenusForCurrentTenant();
        Set<Long> menuIds = new HashSet<>();
        for (Menu menu : menus) {
            if (menu.getId() != null) {
                menuIds.add(menu.getId());
            }
        }
        return buildMenuTree(menus, menuIds::contains);
    }

    /**
     * 当前登录用户可见菜单树：运行时以 {@code PermissionGrant} 为唯一事实源，
     * 通过 {@link Menu#getPermissionName()} 与有效权限匹配，并补全祖先目录节点。
     */
    public List<MenuDto> getTreeByLoginUser() {
        Long userId = currentOperatorResolver.findOperatorIdOrNull();
        if (userId == null) {
            return List.of();
        }
        List<Menu> enabledMenus = loadEnabledMenusForCurrentTenant();
        if (isAdminUser(userId)) {
            return buildMenuTree(enabledMenus, menuId -> true);
        }
        Set<String> granted = permissionChecker.getGrantedPermissionNames(userId);
        Set<Long> visibleMenuIds = resolveVisibleMenuIds(enabledMenus, granted);
        return buildMenuTree(enabledMenus, visibleMenuIds::contains);
    }

    /**
     * 获取菜单列表（分页）。
     * 无关键词时分页根菜单并带出子树；有关键词时按名称、显示名称、路由路径过滤，并保留命中节点的祖先与子孙。
     */
    public PagedResult<MenuDto> getList(MenuQueryRequest request) {
        String keyword = request.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            return pageMenusByKeyword(request, keyword.trim());
        }

        Specification<Menu> spec = SpecificationUtils.<Menu>tenantFilter()
            .and(SpecificationUtils.<Menu>notDeleted())
            .and((root, query, cb) -> cb.isNull(root.get("parentId")));

        var pageRequest = PageRequestUtils.createDefault(
            request.getPageNo(),
            request.getPageSize()
        );

        Page<Menu> page = menuRepository.findAll(spec, pageRequest);
        return PagedResult.fromPage(page.map(this::toDtoWithChildren));
    }

    /**
     * 按关键词过滤菜单树后，对根节点分页。
     */
    private PagedResult<MenuDto> pageMenusByKeyword(MenuQueryRequest request, String keyword) {
        List<Menu> menus = loadMenusForCurrentTenant();
        Set<Long> includedIds = resolveKeywordMenuIds(menus, keyword);
        List<MenuDto> roots = buildMenuTree(menus, includedIds::contains);
        return sliceMenuPage(roots, request);
    }

    private List<Menu> loadMenusForCurrentTenant() {
        Specification<Menu> spec = SpecificationUtils.<Menu>tenantFilter()
            .and(SpecificationUtils.<Menu>notDeleted());
        List<Menu> menus = menuRepository.findAll(spec);
        menus.sort(menuSortOrder());
        return menus;
    }

    /**
     * 命中名称、显示名称或路由路径的节点，连同祖先和子孙一并保留。
     */
    private Set<Long> resolveKeywordMenuIds(List<Menu> menus, String keyword) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        Map<Long, Menu> menuById = new HashMap<>();
        Map<Long, List<Menu>> childrenByParentId = new HashMap<>();
        for (Menu menu : menus) {
            if (menu.getId() == null) {
                continue;
            }
            menuById.put(menu.getId(), menu);
            Long parentKey = menu.getParentId() != null ? menu.getParentId() : 0L;
            childrenByParentId.computeIfAbsent(parentKey, key -> new ArrayList<>()).add(menu);
        }

        Set<Long> included = new HashSet<>();
        Set<Long> expanded = new HashSet<>();
        for (Menu menu : menus) {
            if (menu.getId() == null || !matchesMenuKeyword(menu, normalizedKeyword)) {
                continue;
            }
            included.add(menu.getId());
            addAncestorMenuIds(menu, menuById, included);
            addDescendantMenuIds(menu.getId(), childrenByParentId, included, expanded);
        }
        return included;
    }

    private boolean matchesMenuKeyword(Menu menu, String normalizedKeyword) {
        if (containsKeyword(menu.getName(), normalizedKeyword)) {
            return true;
        }
        if (containsKeyword(menu.getDisplayName(), normalizedKeyword)) {
            return true;
        }
        return containsKeyword(menu.getPath(), normalizedKeyword);
    }

    private boolean containsKeyword(String value, String normalizedKeyword) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private void addDescendantMenuIds(
        Long menuId,
        Map<Long, List<Menu>> childrenByParentId,
        Set<Long> included,
        Set<Long> expanded
    ) {
        if (!expanded.add(menuId)) {
            return;
        }
        List<Menu> children = childrenByParentId.get(menuId);
        if (children == null) {
            return;
        }
        for (Menu child : children) {
            if (child.getId() == null) {
                continue;
            }
            included.add(child.getId());
            addDescendantMenuIds(child.getId(), childrenByParentId, included, expanded);
        }
    }

    private PagedResult<MenuDto> sliceMenuPage(List<MenuDto> roots, MenuQueryRequest request) {
        int pageSize = request.getPageSize();
        int pageIndex = request.getPageNo();
        int fromIndex = pageIndex * pageSize;
        List<MenuDto> pageContent = new ArrayList<>();
        if (fromIndex < roots.size()) {
            int toIndex = Math.min(fromIndex + pageSize, roots.size());
            pageContent.addAll(roots.subList(fromIndex, toIndex));
        }
        return PagedResult.of(pageContent, roots.size(), pageIndex + 1, pageSize);
    }

    /**
     * 获取当前登录人的菜单列表（树形结构）
     * */


    /**
     * 创建菜单
     */
    @RequirePermission("Navigation.MenuManagement")
    public MenuDto create(CreateMenuRequest request) {
        // 检查菜单代码是否已存在
        if (menuRepository.existsByCode(request.getCode())) {
            throw new ValidationException("菜单代码已存在");
        }

        // 检查父菜单
        if (request.getParentId() != null) {
            menuRepository.findById(request.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("父菜单", request.getParentId()));
        }

        Menu menu = new Menu();
        BeanUtils.copyProperties(request, menu);
        menu.setTenantId(TenantUtils.getCurrentTenantId());
        menuViewPermissionProvisioner.apply(menu);

        menu = menuRepository.save(menu);
        return toDto(menu);
    }

    /**
     * 更新菜单
     */
    @RequirePermission("Navigation.MenuManagement")
    public MenuDto update(Long id, UpdateMenuRequest request) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("菜单", id));

        if (Boolean.TRUE.equals(request.getClearParent())) {
            menu.setParentId(null);
        } else if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new BusinessException("不能将自己设为父菜单");
            }
            checkCircularReference(id, request.getParentId());
            menuRepository.findById(request.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("父菜单", request.getParentId()));
            menu.setParentId(request.getParentId());
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            menu.setName(request.getName().trim());
        }
        if (request.getDisplayName() != null) {
            menu.setDisplayName(request.getDisplayName());
        }
        if (request.getIcon() != null) {
            menu.setIcon(request.getIcon());
        }
        if (request.getPath() != null && !request.getPath().isBlank()) {
            menu.setPath(request.getPath().trim());
        }
        if (request.getComponent() != null) {
            menu.setComponent(request.getComponent());
        }
        if (request.getSortOrder() != null) {
            menu.setSortOrder(request.getSortOrder());
        }
        if (request.getIsEnabled() != null) {
            menu.setIsEnabled(request.getIsEnabled());
        }
        if (request.getIsHidden() != null) {
            menu.setIsHidden(request.getIsHidden());
        }
        if (request.getIsExternal() != null) {
            menu.setIsExternal(request.getIsExternal());
        }
        if (request.getExternalUrl() != null) {
            menu.setExternalUrl(request.getExternalUrl());
        }
        if (request.getMenuType() != null && !request.getMenuType().isBlank()) {
            menu.setMenuType(request.getMenuType().trim());
        }
        if (request.getRemark() != null) {
            menu.setRemark(request.getRemark());
        }
        if (request.getKeepAlive() != null) {
            menu.setKeepAlive(request.getKeepAlive());
        }

        menuViewPermissionProvisioner.apply(menu);
        menu = menuRepository.save(menu);
        return toDto(menu);
    }

    /**
     * 删除菜单（软删除）
     */
    @RequirePermission("Navigation.MenuManagement")
    public void delete(IdRequest request) {
        Menu menu = menuRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("菜单", request.getId()));

        // 检查是否有子菜单（未删除的）
        Specification<Menu> childSpec = SpecificationUtils.<Menu>tenantFilter()
                .and(SpecificationUtils.<Menu>notDeleted())
                .and((root, query, cb) -> cb.equal(root.get("parentId"), request.getId()));
        List<Menu> children = menuRepository.findAll(childSpec);

        if (!children.isEmpty()) {
            throw new BusinessException("存在子菜单，无法删除");
        }

        // 软删除
        menu.setIsDeleted(true);
        menu.setDeletedAt(LocalDateTime.now());
        Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
        if (currentUserId != null) {
            menu.setDeletedBy(currentUserId);
        }
        menuViewPermissionProvisioner.apply(menu);
        menuRepository.save(menu);
    }

    /**
     * 启用菜单
     */
    @RequirePermission("Navigation.MenuManagement")
    public void enable(IdRequest request) {
        Menu menu = menuRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("菜单", request.getId()));
        menu.setIsEnabled(true);
        menuViewPermissionProvisioner.apply(menu);
        menuRepository.save(menu);
    }

    /**
     * 禁用菜单
     */
    @RequirePermission("Navigation.MenuManagement")
    public void disable(IdRequest request) {
        Menu menu = menuRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("菜单", request.getId()));
        menu.setIsEnabled(false);
        menuViewPermissionProvisioner.apply(menu);
        menuRepository.save(menu);
    }

    /**
     * 检查循环引用
     */
    private void checkCircularReference(Long menuId, Long parentId) {
        // 检查父菜单是否是当前菜单的子菜单（递归检查）
        Menu parent = menuRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("父菜单", parentId));

        Long currentParentId = parent.getParentId();
        while (currentParentId != null) {
            if (currentParentId.equals(menuId)) {
                throw new BusinessException("不能将子菜单设为父菜单，会造成循环引用");
            }
            Menu currentParent = menuRepository.findById(currentParentId)
                    .orElse(null);
            if (currentParent == null) {
                break;
            }
            currentParentId = currentParent.getParentId();
        }
    }

    /**
     * 构建查询条件
     */
    private Specification<Menu> buildSpecification(MenuQueryRequest request) {
        return SpecificationUtils.<Menu>tenantFilter()
                .and(SpecificationUtils.<Menu>notDeleted())
                .and((root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();

                    // 关键字搜索
                    if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                        String pattern = "%" + request.getKeyword().trim() + "%";
                        predicates.add(cb.or(
                                cb.like(root.get("code"), pattern),
                                cb.like(root.get("name"), pattern),
                                cb.like(root.get("displayName"), pattern)
                        ));
                    }

                    // 父菜单ID过滤
                    if (request.getParentId() != null) {
                        predicates.add(cb.equal(root.get("parentId"), request.getParentId()));
                    }

                    // 菜单类型过滤
                    if (request.getMenuType() != null && !request.getMenuType().trim().isEmpty()) {
                        predicates.add(cb.equal(root.get("menuType"), request.getMenuType()));
                    }

                    // 是否启用过滤
                    if (request.getIsEnabled() != null) {
                        predicates.add(cb.equal(root.get("isEnabled"), request.getIsEnabled()));
                    }

                    // 是否隐藏过滤
                    if (request.getIsHidden() != null) {
                        predicates.add(cb.equal(root.get("isHidden"), request.getIsHidden()));
                    }

                    return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
                });
    }

    /**
     * 转换为DTO
     */
    private MenuDto toDto(Menu menu) {
        MenuDto dto = new MenuDto();
        BeanUtils.copyProperties(menu, dto);
        if (menu.getParent() != null) {
            dto.setParentDisplayName(menu.getParent().getDisplayName());
        }
        return dto;
    }

    /**
     * 转换为DTO（包含子菜单）
     */
    private MenuDto toDtoWithChildren(Menu menu) {
        MenuDto dto = toDto(menu);

        // 查询子菜单（应用租户过滤和软删除过滤）
        Specification<Menu> childSpec = SpecificationUtils.<Menu>tenantFilter()
                .and(SpecificationUtils.<Menu>notDeleted())
                .and((root, query, cb) -> cb.equal(root.get("parentId"), menu.getId()));

        List<Menu> children = menuRepository.findAll(childSpec);
        // 手动排序
        children.sort((a, b) -> {
            int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
            int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
            return Integer.compare(orderA, orderB);
        });

        dto.setChildren(children.stream()
                .map(this::toDtoWithChildren)
                .collect(Collectors.toList()));
        return dto;
    }

    private List<Menu> loadEnabledMenusForCurrentTenant() {
        Specification<Menu> spec = SpecificationUtils.<Menu>tenantFilter()
            .and(SpecificationUtils.<Menu>notDeleted())
            .and((root, query, cb) -> cb.equal(root.get("isEnabled"), true))
            .and((root, query, cb) -> cb.equal(root.get("isHidden"), false));
        List<Menu> menus = menuRepository.findAll(spec);
        menus.sort(menuSortOrder());
        return menus;
    }

    private static Comparator<Menu> menuSortOrder() {
        return (a, b) -> {
            int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
            int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
            return Integer.compare(orderA, orderB);
        };
    }

    private boolean isAdminUser(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        List<Long> roleIds = userRoles.stream()
            .filter(ur -> !Boolean.TRUE.equals(ur.getIsDeleted()))
            .map(UserRole::getRoleId)
            // 跳过 role_id 为空的脏关联行，避免 admin 判定误命中
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return false;
        }
        List<Role> roles = roleRepository.findAllById(roleIds);
        return roles.stream()
            .anyMatch(role -> "admin".equalsIgnoreCase(role.getName())
                || RolePlatformConstants.CODE_ADMIN.equalsIgnoreCase(role.getCode()));
    }

    /**
     * 解析当前用户可见菜单主键：叶子按 permission_name 匹配授权，目录节点补全祖先链。
     */
    private Set<Long> resolveVisibleMenuIds(List<Menu> menus, Set<String> grantedPermissions) {
        Map<Long, Menu> menuById = new HashMap<>();
        for (Menu menu : menus) {
            if (menu.getId() != null) {
                menuById.put(menu.getId(), menu);
            }
        }
        Set<Long> visible = new HashSet<>();
        for (Menu menu : menus) {
            if (isMenuEntryGranted(menu, grantedPermissions)) {
                visible.add(menu.getId());
                addAncestorMenuIds(menu, menuById, visible);
            }
        }
        return visible;
    }

    private boolean isMenuEntryGranted(Menu menu, Set<String> grantedPermissions) {
        String permissionName = menu.getPermissionName();
        if (permissionName == null || permissionName.isBlank()) {
            return false;
        }
        return grantedPermissions.contains(permissionName.trim());
    }

    private void addAncestorMenuIds(Menu menu, Map<Long, Menu> menuById, Set<Long> visible) {
        Long parentId = menu.getParentId();
        while (parentId != null) {
            if (!visible.add(parentId)) {
                break;
            }
            Menu parent = menuById.get(parentId);
            if (parent == null) {
                break;
            }
            parentId = parent.getParentId();
        }
    }

    private List<MenuDto> buildMenuTree(List<Menu> allMenus, java.util.function.Predicate<Long> includeMenuId) {
        Map<Long, List<Menu>> childrenByParentId = new HashMap<>();
        for (Menu menu : allMenus) {
            if (menu.getId() == null || !includeMenuId.test(menu.getId())) {
                continue;
            }
            Long parentKey = menu.getParentId() != null ? menu.getParentId() : 0L;
            childrenByParentId.computeIfAbsent(parentKey, key -> new ArrayList<>()).add(menu);
        }
        for (List<Menu> siblings : childrenByParentId.values()) {
            siblings.sort(menuSortOrder());
        }
        List<Menu> roots = childrenByParentId.getOrDefault(0L, List.of());
        return roots.stream()
            .map(root -> toDtoWithFilteredChildren(root, childrenByParentId))
            .collect(Collectors.toList());
    }

    private MenuDto toDtoWithFilteredChildren(Menu menu, Map<Long, List<Menu>> childrenByParentId) {
        MenuDto dto = toDto(menu);
        List<Menu> children = childrenByParentId.getOrDefault(menu.getId(), List.of());
        dto.setChildren(children.stream()
            .map(child -> toDtoWithFilteredChildren(child, childrenByParentId))
            .collect(Collectors.toList()));
        return dto;
    }
}
