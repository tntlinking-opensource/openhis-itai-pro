/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.application;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.core.infrastructure.permission.IPermissionGrantService;
import com.openhis.whale.core.infrastructure.permission.RequirePermission;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.identity.application.dto.IdRequest;
import com.openhis.whale.identity.infrastructure.repository.RoleRepository;
import com.openhis.whale.identity.infrastructure.repository.UserRepository;
import com.openhis.whale.permission.application.dto.BatchGrantPermissionRequest;
import com.openhis.whale.permission.application.dto.CreatePermissionRequest;
import com.openhis.whale.permission.application.dto.GetGrantsRequest;
import com.openhis.whale.permission.application.dto.GrantPermissionRequest;
import com.openhis.whale.permission.application.dto.PermissionDto;
import com.openhis.whale.permission.application.dto.PermissionGrantInfoDto;
import com.openhis.whale.permission.application.dto.PermissionQueryRequest;
import com.openhis.whale.permission.application.dto.UpdatePermissionRequest;
import com.openhis.whale.permission.application.support.NavigationPermissionNodeNames;
import com.openhis.whale.permission.application.support.NavigationPermissionTreeProvider;
import com.openhis.whale.permission.domain.Permission;
import com.openhis.whale.permission.domain.PermissionGrant;
import com.openhis.whale.permission.infrastructure.repository.PermissionGrantRepository;
import com.openhis.whale.permission.infrastructure.repository.PermissionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限应用服务
 */
@Service
public class PermissionAppService implements IPermissionGrantService {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CurrentOperatorResolver currentOperatorResolver;

    @Autowired(required = false)
    private NavigationPermissionTreeProvider navigationPermissionTreeProvider;

    /**
     * 获取所有权限
     */
    @RequirePermission("Navigation.PermissionManagement")
    public List<PermissionDto> getAll() {
        Specification<Permission> spec = SpecificationUtils.<Permission>tenantFilter()
                .and(SpecificationUtils.<Permission>notDeleted());
        List<Permission> permissions = permissionRepository.findAll(spec);
        return permissions.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取权限树
     */
    @RequirePermission("Navigation.PermissionManagement")
    public List<PermissionDto> getTree() {
        if (navigationPermissionTreeProvider != null) {
            return navigationPermissionTreeProvider.buildMenuAlignedTree();
        }
        return buildTreeFromParentNames();
    }

    private List<PermissionDto> buildTreeFromParentNames() {
        Specification<Permission> spec = SpecificationUtils.<Permission>tenantFilter()
                .and(SpecificationUtils.<Permission>notDeleted());
        List<Permission> allPermissions = permissionRepository.findAll(spec);

        // 构建权限树
        Map<String, PermissionDto> permissionMap = allPermissions.stream()
                .map(this::toDto)
                .collect(Collectors.toMap(PermissionDto::getName, p -> p));

        List<PermissionDto> rootPermissions = new ArrayList<>();
        for (PermissionDto dto : permissionMap.values()) {
            if (dto.getParentName() == null || dto.getParentName().isEmpty()) {
                rootPermissions.add(dto);
            } else {
                PermissionDto parent = permissionMap.get(dto.getParentName());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(dto);
                }
            }
        }

        return rootPermissions;
    }

    /**
     * 过滤不可授权节点（目录分组占位名）。
     */
    public static List<String> filterGrantablePermissionNames(List<String> permissionNames) {
        if (permissionNames == null || permissionNames.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>();
        for (String name : permissionNames) {
            if (NavigationPermissionNodeNames.isGrantablePermissionName(name)) {
                filtered.add(name.trim());
            }
        }
        return filtered;
    }

    /**
     * 根据ID获取权限
     */
    @RequirePermission("Navigation.PermissionManagement")
    public PermissionDto getById(IdRequest request) {
        Permission permission = permissionRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("权限", request.getId()));
        return toDto(permission);
    }

    /**
     * 查询权限列表
     */
    @RequirePermission("Navigation.PermissionManagement")
    public List<PermissionDto> getList(PermissionQueryRequest request) {
        Specification<Permission> spec = buildSpecification(request);
        List<Permission> permissions = permissionRepository.findAll(spec);
        return permissions.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 创建权限
     */
    @RequirePermission("Navigation.PermissionManagement")
    public PermissionDto create(CreatePermissionRequest request) {
        // 检查权限名称是否已存在
        if (permissionRepository.existsByName(request.getName())) {
            throw new ValidationException("权限名称已存在: " + request.getName());
        }

        // 如果指定了父权限，检查父权限是否存在
        if (request.getParentName() != null && !request.getParentName().isEmpty()) {
            permissionRepository.findByName(request.getParentName())
                    .orElseThrow(() -> new EntityNotFoundException("父权限", 0L));
        }

        Permission permission = new Permission();
        permission.setName(request.getName());
        permission.setDisplayName(request.getDisplayName());
        permission.setParentName(request.getParentName());
        permission.setDescription(request.getDescription());
        permission.setTenantId(TenantUtils.getCurrentTenantId());

        permission = permissionRepository.save(permission);
        return toDto(permission);
    }

    /**
     * 更新权限
     */
    @RequirePermission("Navigation.PermissionManagement")
    public PermissionDto update(Long id, UpdatePermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("权限", id));

        // 如果指定了父权限，检查父权限是否存在
        if (request.getParentName() != null && !request.getParentName().isEmpty()) {
            if (!request.getParentName().equals(permission.getParentName())) {
                permissionRepository.findByName(request.getParentName())
                        .orElseThrow(() -> new EntityNotFoundException("父权限", 0L));
            }
        }

        permission.setDisplayName(request.getDisplayName());
        permission.setParentName(request.getParentName());
        permission.setDescription(request.getDescription());

        permission = permissionRepository.save(permission);
        return toDto(permission);
    }

    /**
     * 删除权限（软删除）
     */
    @RequirePermission("Navigation.PermissionManagement")
    public void delete(IdRequest request) {
        Permission permission = permissionRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("权限", request.getId()));

        // 检查是否有子权限
        List<Permission> children = permissionRepository.findByParentName(permission.getName());
        if (!children.isEmpty()) {
            throw new ValidationException("无法删除权限，存在子权限");
        }

        // 检查是否有权限授予记录
        List<PermissionGrant> grants = permissionGrantRepository.findByPermissionName(permission.getName());
        if (!grants.isEmpty()) {
            throw new ValidationException("无法删除权限，存在权限授予记录");
        }

        // 软删除
        permission.setIsDeleted(true);
        permission.setDeletedAt(LocalDateTime.now());
        Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
        if (currentUserId != null) {
            permission.setDeletedBy(currentUserId);
        }
        permissionRepository.save(permission);
    }

    /**
     * 获取权限的授予信息
     */
    @RequirePermission("Navigation.PermissionManagement")
    public List<PermissionGrantInfoDto> getGrants(GetGrantsRequest request) {
        String permissionName = request.getPermissionName();
        List<PermissionGrant> grants = permissionGrantRepository.findByPermissionName(permissionName)
                .stream()
                .filter(g -> !Boolean.TRUE.equals(g.getIsDeleted()))
                .collect(Collectors.toList());

        List<PermissionGrantInfoDto> result = new ArrayList<>();
        for (PermissionGrant grant : grants) {
            PermissionGrantInfoDto dto = new PermissionGrantInfoDto();
            dto.setPermissionName(grant.getPermissionName());
            dto.setProviderName(grant.getProviderName());
            dto.setProviderKey(grant.getProviderKey());
            dto.setTenantId(grant.getTenantId());

            // 获取提供者显示名称
            if ("R".equals(grant.getProviderName())) {
                // 角色
                try {
                    Long roleId = Long.parseLong(grant.getProviderKey());
                    roleRepository.findById(roleId).ifPresent(role -> {
                        dto.setProviderDisplayName(role.getDisplayName() != null ? role.getDisplayName() : role.getName());
                    });
                } catch (NumberFormatException e) {
                    dto.setProviderDisplayName(grant.getProviderKey());
                }
            } else if ("U".equals(grant.getProviderName())) {
                // 用户
                try {
                    Long userId = Long.parseLong(grant.getProviderKey());
                    userRepository.findById(userId).ifPresent(user -> {
                        dto.setProviderDisplayName(user.getUserName());
                    });
                } catch (NumberFormatException e) {
                    dto.setProviderDisplayName(grant.getProviderKey());
                }
            } else {
                dto.setProviderDisplayName(grant.getProviderKey());
            }

            result.add(dto);
        }

        return result;
    }

    /**
     * 批量授予权限
     */
    @RequirePermission("Navigation.PermissionManagement")
    public void batchGrant(BatchGrantPermissionRequest request) {
        if (request.getPermissionNames() == null || request.getPermissionNames().isEmpty()) {
            throw new ValidationException("权限名称列表不能为空");
        }

        for (String permissionName : request.getPermissionNames()) {
            try {
                grant(permissionName, request.getProviderName(), request.getProviderKey());
            } catch (BusinessException e) {
                // 如果权限已授予，忽略错误
                if (e.getMessage() == null || !e.getMessage().contains("已授予")) {
                    throw e;
                }
            }
        }
    }

    /**
     * 批量撤销权限
     */
    @RequirePermission("Navigation.PermissionManagement")
    public void batchRevoke(BatchGrantPermissionRequest request) {
        if (request.getPermissionNames() == null || request.getPermissionNames().isEmpty()) {
            throw new ValidationException("权限名称列表不能为空");
        }

        for (String permissionName : request.getPermissionNames()) {
            try {
                revoke(permissionName, request.getProviderName(), request.getProviderKey());
            } catch (BusinessException e) {
                // 如果权限授予不存在，忽略错误
                if (e.getMessage() == null || !e.getMessage().contains("不存在")) {
                    throw e;
                }
            }
        }
    }

    /**
     * 构建查询条件
     */
    private Specification<Permission> buildSpecification(PermissionQueryRequest request) {
        return SpecificationUtils.<Permission>tenantFilter()
                .and(SpecificationUtils.<Permission>notDeleted())
                .and((root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();

                    if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                        String keyword = "%" + request.getKeyword().trim() + "%";
                        Predicate nameLike = cb.like(root.get("name"), keyword);
                        Predicate displayNameLike = cb.like(root.get("displayName"), keyword);
                        predicates.add(cb.or(nameLike, displayNameLike));
                    }

                    if (request.getParentName() != null && !request.getParentName().isEmpty()) {
                        predicates.add(cb.equal(root.get("parentName"), request.getParentName()));
                    }

                    return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
                });
    }

    /**
     * 根据用户ID获取权限
     */
    public List<String> getByUser(Long userId) {
        return getGrantedPermissionNames("U", String.valueOf(userId));
    }

    /**
     * 根据角色ID获取权限
     */
    public List<String> getByRole(Long roleId) {
        return getGrantedPermissionNames("R", String.valueOf(roleId));
    }

    /**
     * 授予权限（接口实现）
     */
    @Override
    public void grant(String permissionName, String providerName, String providerKey) {
        // 检查权限是否存在
        permissionRepository.findByName(permissionName)
                .orElseThrow(() -> new ValidationException("权限定义不存在: " + permissionName));

        // 含已软删记录查询，避免 UNIQUE 约束下重复 insert
        List<PermissionGrant> existingGrants = permissionGrantRepository
            .findAllByPermissionNameAndProviderNameAndProviderKey(permissionName, providerName, providerKey);
        for (PermissionGrant existing : existingGrants) {
            if (!Boolean.TRUE.equals(existing.getIsDeleted())) {
                return;
            }
        }
        for (PermissionGrant existing : existingGrants) {
            if (Boolean.TRUE.equals(existing.getIsDeleted())) {
                existing.setIsDeleted(false);
                existing.setDeletedAt(null);
                existing.setDeletedBy(null);
                permissionGrantRepository.save(existing);
                return;
            }
        }

        // 创建权限授予
        PermissionGrant grant = new PermissionGrant();
        grant.setPermissionName(permissionName);
        grant.setProviderName(providerName);
        grant.setProviderKey(providerKey);
        grant.setTenantId(TenantUtils.getCurrentTenantId());

        permissionGrantRepository.save(grant);
    }

    /**
     * 撤销权限（接口实现）
     */
    @Override
    public void revoke(String permissionName, String providerName, String providerKey) {
        // 查找权限授予（包括已删除的）
        PermissionGrant grant = permissionGrantRepository.findByPermissionNameAndProviderNameAndProviderKey(
                permissionName, providerName, providerKey)
                .stream()
                .findFirst()
                .orElse(null);

        if (grant == null || Boolean.TRUE.equals(grant.getIsDeleted())) {
            throw new BusinessException("权限授予不存在");
        }

        // 软删除权限授予
        grant.setIsDeleted(true);
        grant.setDeletedAt(LocalDateTime.now());
        Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
        if (currentUserId != null) {
            grant.setDeletedBy(currentUserId);
        }
        permissionGrantRepository.save(grant);
    }

    /**
     * 授予权限（使用DTO，保持向后兼容）
     */
    public void grant(GrantPermissionRequest request) {
        grant(request.getPermissionName(), request.getProviderName(), request.getProviderKey());
    }

    /**
     * 撤销权限（使用DTO，保持向后兼容）
     */
    public void revoke(GrantPermissionRequest request) {
        revoke(request.getPermissionName(), request.getProviderName(), request.getProviderKey());
    }

    /**
     * 判断指定提供者是否存在任意有效授予（接口实现）
     */
    @Override
    public boolean hasGrants(String providerName, String providerKey) {
        return permissionGrantRepository.findByProviderNameAndProviderKey(providerName, providerKey)
                .stream()
                .anyMatch(pg -> !Boolean.TRUE.equals(pg.getIsDeleted()));
    }

    /**
     * 获取指定提供者当前授予的权限名称列表（接口实现）
     */
    @Override
    public List<String> getGrantedPermissionNames(String providerName, String providerKey) {
        return permissionGrantRepository.findByProviderNameAndProviderKey(providerName, providerKey)
                .stream()
                .filter(pg -> !Boolean.TRUE.equals(pg.getIsDeleted()))
                .map(PermissionGrant::getPermissionName)
                .distinct()
                .toList();
    }

    /**
     * 仅保留权限定义表中存在的权限名（接口实现）。
     */
    @Override
    public List<String> retainExistingPermissionDefinitions(List<String> permissionNames) {
        if (permissionNames == null || permissionNames.isEmpty()) {
            return List.of();
        }
        List<String> retained = new ArrayList<>();
        for (String permissionName : permissionNames) {
            if (permissionName == null || permissionName.isBlank()) {
                continue;
            }
            String normalized = permissionName.trim();
            if (permissionRepository.findByName(normalized)
                .filter(permission -> !Boolean.TRUE.equals(permission.getIsDeleted()))
                .isPresent()) {
                retained.add(normalized);
            }
        }
        return retained;
    }

    /**
     * 撤销某权限名下的全部有效授予（菜单隐藏/停用/删除时级联清理）。
     */
    @Override
    public void revokeAllGrantsForPermission(String permissionName) {
        if (permissionName == null || permissionName.isBlank()) {
            return;
        }
        String normalized = permissionName.trim();
        List<PermissionGrant> grants = permissionGrantRepository.findByPermissionName(normalized);
        if (grants.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
        List<PermissionGrant> toSave = new ArrayList<>();
        for (PermissionGrant grant : grants) {
            if (Boolean.TRUE.equals(grant.getIsDeleted())) {
                continue;
            }
            grant.setIsDeleted(true);
            grant.setDeletedAt(now);
            if (currentUserId != null) {
                grant.setDeletedBy(currentUserId);
            }
            toSave.add(grant);
        }
        if (!toSave.isEmpty()) {
            permissionGrantRepository.saveAll(toSave);
        }
    }

    /**
     * 转换为DTO
     */
    private PermissionDto toDto(Permission permission) {
        PermissionDto dto = new PermissionDto();
        BeanUtils.copyProperties(permission, dto);
        return dto;
    }
}
