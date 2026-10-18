/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application;

import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.PageRequestUtils;
import com.zxplat.framework.core.application.util.SpecificationUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.infrastructure.permission.IPermissionGrantService;
import com.zxplat.framework.core.infrastructure.permission.RequirePermission;
import com.zxplat.framework.identity.application.dto.AddRoleClaimRequest;
import com.zxplat.framework.identity.application.dto.AssignPermissionsRequest;
import com.zxplat.framework.identity.application.dto.CreateRoleRequest;
import com.zxplat.framework.identity.application.dto.IdRequest;
import com.zxplat.framework.identity.application.dto.RoleClaimDto;
import com.zxplat.framework.identity.application.dto.RoleDto;
import com.zxplat.framework.identity.application.dto.RoleQueryRequest;
import com.zxplat.framework.identity.application.dto.UpdateRoleRequest;
import com.zxplat.framework.identity.domain.Role;
import com.zxplat.framework.identity.domain.RoleClaim;
import com.zxplat.framework.identity.infrastructure.repository.RoleClaimRepository;
import com.zxplat.framework.identity.infrastructure.repository.RoleRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 角色应用服务：内置角色（种子）与院方自定义角色共用 {@code zxplat_roles}；code 仅服务端使用。
 */
@Service
public class RoleAppService {

    private static final Set<String> ALLOWED_ASSIGNMENT_SCOPES = Set.of(
            RoleAssignmentScopeConstants.ORG,
            RoleAssignmentScopeConstants.WARD,
            RoleAssignmentScopeConstants.PHARMACY,
            RoleAssignmentScopeConstants.WAREHOUSE,
            RoleAssignmentScopeConstants.GLOBAL
    );

    private static final Set<String> ALLOWED_MANAGEMENT_CAPABILITIES = Set.of(
            RoleStaffScopeConstants.DOCTOR_VISIT_ORG,
            RoleStaffScopeConstants.WAREHOUSE_LOCATION,
            RoleStaffScopeConstants.PHARMACY_LOCATION,
            RoleStaffScopeConstants.WORK_ORG,
            RoleStaffScopeConstants.NURSE_WARD_LOCATION
    );

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private IPermissionGrantService permissionGrantService;

    @Autowired
    private RoleClaimRepository roleClaimRepository;

    @Autowired
    private UserAppService userAppService;

    @Autowired
    private BuiltInBusinessRoleOptionLoader builtInBusinessRoleOptionLoader;

    /**
     * 根据ID获取角色。
     *
     * @param request 含角色主键
     * @return 角色 DTO
     */
    @RequirePermission("Navigation.RoleManagement")
    public RoleDto getById(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("角色ID不能为空");
        }
        Role role = roleRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("角色", request.getId()));
        return toDto(role);
    }

    /**
     * 分页查询角色列表。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @RequirePermission("Navigation.RoleManagement")
    public PagedResult<RoleDto> getList(RoleQueryRequest request) {
        Specification<Role> spec = buildSpecification(request);
        var pageRequest = PageRequestUtils.createDefault(
                request.getPageNo(),
                request.getPageSize()
        );

        Page<Role> page = roleRepository.findAll(spec, pageRequest);
        return PagedResult.fromPage(page.map(this::toDto));
    }

    /**
     * 查询全部内置角色（含系统管理员）。
     *
     * @return 内置角色 DTO 列表
     */
    @RequirePermission("Navigation.RoleManagement")
    public List<RoleDto> listBuiltIn() {
        List<RoleDto> result = new ArrayList<>();
        for (Role role : roleRepository.findByIsStaticTrueAndIsDeletedFalseOrderByIdAsc()) {
            result.add(toDto(role));
        }
        return result;
    }

    /**
     * 创建院方自定义角色（仅菜单/岗位范围/管理能力可配；code 由服务端生成）。
     *
     * @param request 创建参数
     * @return 新角色
     */
    @RequirePermission("Navigation.RoleManagement")
    public RoleDto create(CreateRoleRequest request) {
        String displayName = request.getDisplayName() != null ? request.getDisplayName().trim() : "";
        if (displayName.isEmpty()) {
            throw new ValidationException("角色名称不能为空");
        }

        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String slug = toInternalNameSlug(displayName);
        String internalName = RolePlatformConstants.CUSTOM_NAME_PREFIX + slug + "_" + uniqueSuffix;
        int attempt = 0;
        while (roleRepository.existsByName(internalName) && attempt < 5) {
            attempt++;
            uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            internalName = RolePlatformConstants.CUSTOM_NAME_PREFIX + slug + "_" + uniqueSuffix;
        }
        if (roleRepository.existsByName(internalName)) {
            throw new BusinessException("生成角色内部标识失败，请重试");
        }

        Role role = new Role();
        role.setName(internalName);
        role.setDisplayName(displayName);
        role.setCode(RolePlatformConstants.CUSTOM_CODE_PREFIX + uniqueSuffix.toUpperCase(Locale.ROOT));
        role.setDescription(request.getDescription());
        role.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        role.setIsStatic(false);

        role = roleRepository.save(role);
        userAppService.attachRoleToAdminUser(role.getId());
        return toDto(role);
    }

    /**
     * 更新角色展示信息与默认标记（内置/自定义均可，不可改 code/name/isStatic）。
     *
     * @param request 更新参数
     * @return 更新后角色
     */
    @RequirePermission("Navigation.RoleManagement")
    public RoleDto update(UpdateRoleRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("角色ID不能为空");
        }

        Role role = roleRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("角色", request.getId()));

        if (request.getDisplayName() != null) {
            String displayName = request.getDisplayName().trim();
            if (displayName.isEmpty()) {
                throw new ValidationException("角色名称不能为空");
            }
            role.setDisplayName(displayName);
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getIsDefault() != null) {
            role.setIsDefault(request.getIsDefault());
        }

        role = roleRepository.save(role);
        return toDto(role);
    }

    /**
     * 删除自定义角色（软删除）；内置角色不可删。
     *
     * @param request 含角色主键
     */
    @RequirePermission("Navigation.RoleManagement")
    public void delete(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("角色ID不能为空");
        }

        Role role = roleRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("角色", request.getId()));

        if (Boolean.TRUE.equals(role.getIsStatic())) {
            throw new BusinessException("内置角色不允许删除");
        }

        if ("admin".equalsIgnoreCase(role.getName())) {
            throw new BusinessException("系统管理员角色不能被删除");
        }

        if (permissionGrantService.hasGrants("R", String.valueOf(role.getId()))) {
            throw new BusinessException("该角色已分配权限，请先取消权限授予后再删除角色");
        }

        role.setIsDeleted(true);
        role.setDeletedAt(java.time.LocalDateTime.now());
        roleRepository.save(role);
    }

    /**
     * 分配 Navigation 权限。
     *
     * @param id 角色主键
     * @param request 权限名列表
     */
    @RequirePermission("Navigation.RoleManagement")
    public void assignPermissions(Long id, AssignPermissionsRequest request) {
        roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("角色", id));

        List<String> desired = filterGrantablePermissionNames(
            request.getPermissionNames() != null ? request.getPermissionNames() : List.of());
        desired = permissionGrantService.retainExistingPermissionDefinitions(desired);
        List<String> existing = permissionGrantService.getGrantedPermissionNames("R", String.valueOf(id));

        Set<String> desiredSet = new HashSet<>(desired);
        Set<String> existingSet = new HashSet<>(existing);

        for (String permissionName : existingSet) {
            if (!desiredSet.contains(permissionName)) {
                permissionGrantService.revoke(permissionName, "R", String.valueOf(id));
            }
        }

        for (String permissionName : desiredSet) {
            if (!existingSet.contains(permissionName)) {
                permissionGrantService.grant(permissionName, "R", String.valueOf(id));
            }
        }
    }

    /**
     * 获取角色声明列表。
     *
     * @param id 角色主键
     * @return 声明列表
     */
    @RequirePermission("Navigation.RoleManagement")
    public List<RoleClaimDto> getClaims(Long id) {
        roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("角色", id));

        List<RoleClaim> claims = roleClaimRepository.findByRoleId(id);
        return claims.stream()
                .map(claim -> {
                    RoleClaimDto dto = new RoleClaimDto();
                    BeanUtils.copyProperties(claim, dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 添加角色声明。
     *
     * @param id 角色主键
     * @param request 声明内容
     * @return 新声明
     */
    @RequirePermission("Navigation.RoleManagement")
    public RoleClaimDto addClaim(Long id, AddRoleClaimRequest request) {
        roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("角色", id));

        Optional<RoleClaim> existingOpt = roleClaimRepository.findFirstByRoleIdAndClaimTypeAndClaimValue(
                id, request.getClaimType(), request.getClaimValue());
        if (existingOpt.isPresent()) {
            RoleClaim existing = existingOpt.get();
            // 有效声明已存在则拒绝；软删行则恢复，避免唯一约束冲突后再插
            if (!Boolean.TRUE.equals(existing.getIsDeleted())) {
                throw new BusinessException("角色声明已存在");
            }
            existing.setIsDeleted(false);
            existing.setDeletedAt(null);
            existing.setDeletedBy(null);
            existing = roleClaimRepository.save(existing);
            RoleClaimDto restored = new RoleClaimDto();
            BeanUtils.copyProperties(existing, restored);
            return restored;
        }

        RoleClaim claim = new RoleClaim();
        claim.setRoleId(id);
        claim.setClaimType(request.getClaimType());
        claim.setClaimValue(request.getClaimValue());
        claim.setTenantId(TenantUtils.getCurrentTenantId());

        claim = roleClaimRepository.save(claim);

        RoleClaimDto dto = new RoleClaimDto();
        BeanUtils.copyProperties(claim, dto);
        return dto;
    }

    /**
     * 删除角色声明。
     *
     * @param id 角色主键
     * @param claimType 声明类型
     * @param claimValue 声明值
     */
    @RequirePermission("Navigation.RoleManagement")
    public void removeClaim(Long id, String claimType, String claimValue) {
        roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("角色", id));

        roleClaimRepository.deleteByRoleIdAndClaimTypeAndClaimValue(id, claimType, claimValue);
    }

    /**
     * 删除角色的所有指定类型声明。
     *
     * @param id 角色主键
     * @param claimType 声明类型
     */
    @RequirePermission("Navigation.RoleManagement")
    public void removeClaimsByType(Long id, String claimType) {
        roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("角色", id));

        roleClaimRepository.deleteByRoleIdAndClaimType(id, claimType);
    }

    /**
     * 获取角色的岗位范围声明（AssignmentScope）。
     *
     * @param id 角色主键
     * @return 岗位范围 code 列表
     */
    @RequirePermission("Navigation.RoleManagement")
    public List<String> getAssignmentScopes(Long id) {
        roleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("角色", id));
        return readClaimValues(id, RoleAssignmentScopeConstants.CLAIM_TYPE);
    }

    /**
     * 全量替换角色的岗位范围声明。
     *
     * @param id 角色主键
     * @param scopeCodes 岗位范围 code 列表
     */
    @RequirePermission("Navigation.RoleManagement")
    public void assignAssignmentScopes(Long id, List<String> scopeCodes) {
        roleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("角色", id));
        replaceClaimValues(id, RoleAssignmentScopeConstants.CLAIM_TYPE, scopeCodes, ALLOWED_ASSIGNMENT_SCOPES);
    }

    /**
     * 获取角色的管理能力声明（原 StaffScope，控制人员维护页可编辑块）。
     *
     * @param id 角色主键
     * @return 管理能力 code 列表
     */
    @RequirePermission("Navigation.RoleManagement")
    public List<String> getStaffScopes(Long id) {
        roleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("角色", id));
        // 内置角色走 code 模板，与人员维护联动同口径（护士/技师=工作科室，非出诊科室）
        return builtInBusinessRoleOptionLoader.resolveStaffScopesForRoleId(id);
    }

    /**
     * 全量替换角色的管理能力声明。
     *
     * @param id 角色主键
     * @param scopeCodes 管理能力 code 列表
     */
    @RequirePermission("Navigation.RoleManagement")
    public void assignStaffScopes(Long id, List<String> scopeCodes) {
        roleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("角色", id));
        replaceClaimValues(id, RoleStaffScopeConstants.CLAIM_TYPE, scopeCodes, ALLOWED_MANAGEMENT_CAPABILITIES);
    }

    /**
     * 判断角色主键是否为未删除的内置角色。
     *
     * @param roleId 角色主键
     * @return 是否内置
     */
    public boolean isBuiltInRole(Long roleId) {
        if (roleId == null) {
            return false;
        }
        return roleRepository.existsByIdAndIsStaticTrueAndIsDeletedFalse(roleId);
    }

    private Specification<Role> buildSpecification(RoleQueryRequest request) {
        return SpecificationUtils.<Role>tenantFilter()
            .and(SpecificationUtils.<Role>notDeleted())
            .and((root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                    String keyword = "%" + request.getKeyword().trim() + "%";
                    Predicate nameLike = cb.like(root.get("name"), keyword);
                    Predicate displayNameLike = cb.like(root.get("displayName"), keyword);
                    predicates.add(cb.or(nameLike, displayNameLike));
                }

                if (request.getIsDefault() != null) {
                    predicates.add(cb.equal(root.get("isDefault"), request.getIsDefault()));
                }

                if (request.getIsStatic() != null) {
                    predicates.add(cb.equal(root.get("isStatic"), request.getIsStatic()));
                }

                return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
            });
    }

    private static List<String> filterGrantablePermissionNames(List<String> permissionNames) {
        if (permissionNames == null || permissionNames.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>();
        for (String name : permissionNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (name.startsWith("Navigation.__folder__.")) {
                continue;
            }
            filtered.add(name.trim());
        }
        return filtered;
    }

    private List<String> readClaimValues(Long roleId, String claimType) {
        List<RoleClaim> claims = roleClaimRepository.findByRoleId(roleId);
        List<String> values = new ArrayList<>();
        for (RoleClaim claim : claims) {
            if (claimType.equals(claim.getClaimType())
                && claim.getClaimValue() != null
                && !claim.getClaimValue().isBlank()) {
                values.add(claim.getClaimValue().trim());
            }
        }
        return values;
    }

    /**
     * 全量对齐某类型声明：恢复软删、软删多余、仅插入全新值。
     * <p>不可先 delete 再 insert：{@code uk_zxplat_rc_role_type_value} 含软删行，会触发重复键。</p>
     */
    private void replaceClaimValues(Long roleId, String claimType, List<String> scopeCodes, Set<String> allowed) {
        Set<String> desired = new HashSet<>();
        if (scopeCodes != null) {
            for (String scope : scopeCodes) {
                if (scope == null || scope.isBlank()) {
                    continue;
                }
                String trimmed = scope.trim();
                if (!allowed.contains(trimmed)) {
                    throw new ValidationException("不支持的声明值：" + trimmed);
                }
                desired.add(trimmed);
            }
        }

        List<RoleClaim> existingRows = roleClaimRepository.findAllByRoleIdAndClaimType(roleId, claimType);
        Set<String> satisfied = new HashSet<>();
        LocalDateTime deletedAt = LocalDateTime.now();

        for (RoleClaim existing : existingRows) {
            if (existing == null || existing.getClaimValue() == null || existing.getClaimValue().isBlank()) {
                continue;
            }
            String value = existing.getClaimValue().trim();
            if (desired.contains(value)) {
                if (Boolean.TRUE.equals(existing.getIsDeleted())) {
                    existing.setIsDeleted(false);
                    existing.setDeletedAt(null);
                    existing.setDeletedBy(null);
                    roleClaimRepository.save(existing);
                }
                satisfied.add(value);
            } else if (!Boolean.TRUE.equals(existing.getIsDeleted())) {
                existing.setIsDeleted(true);
                existing.setDeletedAt(deletedAt);
                roleClaimRepository.save(existing);
            }
        }

        for (String value : desired) {
            if (satisfied.contains(value)) {
                continue;
            }
            RoleClaim claim = new RoleClaim();
            claim.setRoleId(roleId);
            claim.setClaimType(claimType);
            claim.setClaimValue(value);
            claim.setTenantId(TenantUtils.getCurrentTenantId());
            roleClaimRepository.save(claim);
        }
    }

    private static String toInternalNameSlug(String displayName) {
        String lowered = displayName.toLowerCase(Locale.ROOT).trim();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lowered.length(); index++) {
            char ch = lowered.charAt(index);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                builder.append(ch);
            } else if (ch == ' ' || ch == '-' || ch == '_') {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
            }
        }
        String slug = builder.toString();
        if (slug.isEmpty()) {
            slug = "role";
        }
        if (slug.length() > 48) {
            slug = slug.substring(0, 48);
        }
        return slug;
    }

    private RoleDto toDto(Role role) {
        RoleDto dto = new RoleDto();
        BeanUtils.copyProperties(role, dto);
        return dto;
    }
}
