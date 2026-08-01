/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.PageRequestUtils;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.identity.application.dto.*;
import com.openhis.whale.identity.domain.Role;
import com.openhis.whale.identity.domain.User;
import com.openhis.whale.identity.domain.UserClaim;
import com.openhis.whale.identity.domain.UserRole;
import com.openhis.whale.identity.domain.UserTenant;
import com.openhis.whale.identity.domain.event.UserDeletedEvent;
import com.openhis.whale.identity.infrastructure.repository.RoleRepository;
import com.openhis.whale.identity.infrastructure.repository.UserClaimRepository;
import com.openhis.whale.identity.infrastructure.repository.UserRepository;
import com.openhis.whale.identity.infrastructure.repository.UserRoleRepository;
import com.openhis.whale.identity.infrastructure.repository.UserTenantRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户应用服务
 */
@Service
public class UserAppService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserClaimRepository userClaimRepository;

    @Autowired
    private CurrentOperatorResolver currentOperatorResolver;

    @Autowired
    private UserTenantRepository userTenantRepository;

    @Autowired
    private IDomainEventPublisher domainEventPublisher;

    /**
     * 判断平台用户是否存在（含软删过滤前的存在性由仓储过滤器决定）。
     */
    public boolean existsById(Long id) {
        return id != null && userRepository.existsById(id);
    }

    /**
     * 根据ID获取用户
     */
    public UserDto getById(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("用户ID不能为空");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("用户", request.getId()));
        return toDto(user);
    }

    /**
     * 获取用户列表
     */
    public PagedResult<UserDto> getList(UserQueryRequest request) {
        Specification<User> spec = buildSpecification(request);
        var pageRequest = PageRequestUtils.createDefault(
                request.getPageNo(),
                request.getPageSize()
        );

        Page<User> page = userRepository.findAll(spec, pageRequest);
        return PagedResult.fromPage(page.map(this::toDto));
    }

    /**
     * 创建用户
     */
    public UserDto create(CreateUserRequest request) {
        if (request.getUserName() == null || request.getUserName().trim().isEmpty()) {
            throw new ValidationException("用户名不能为空");
        }
        String userName = request.getUserName().trim();
        assertActiveUserNameAvailable(userName, null);
        assertActiveEmailAvailable(request.getEmail(), null);

        User user = new User();
        user.setUserName(userName);
        user.setName(request.getName());
        user.setSurname(request.getSurname());
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhoneNumber(trimToNull(request.getPhoneNumber()));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        user.setExtraProperties(request.getExtraProperties());

        user = userRepository.save(user);
        bindUserToCurrentTenant(user.getId());
        return toDto(user);
    }

    /**
     * 将用户绑定到当前登录租户（{@code whale_user_tenants}）。
     * <p>无当前租户（宿主上下文）时跳过。</p>
     */
    private void bindUserToCurrentTenant(Long userId) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null || userId == null) {
            return;
        }
        UserTenant existing = userTenantRepository.findByUserIdAndTenantId(userId, tenantId);
        if (existing == null) {
            UserTenant userTenant = new UserTenant();
            userTenant.setUserId(userId);
            userTenant.setTenantId(tenantId);
            userTenantRepository.save(userTenant);
            return;
        }
        if (Boolean.TRUE.equals(existing.getIsDeleted())) {
            existing.setIsDeleted(false);
            existing.setDeletedAt(null);
            existing.setDeletedBy(null);
            userTenantRepository.save(existing);
        }
    }

    /**
     * 更新用户
     */
    public UserDto update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户", id));

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        if (request.getSurname() != null) {
            user.setSurname(request.getSurname());
        }

        if (request.getEmail() != null) {
            assertActiveEmailAvailable(request.getEmail(), id);
            user.setEmail(trimToNull(request.getEmail()));
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(trimToNull(request.getPhoneNumber()));
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        if (request.getExtraProperties() != null) {
            user.setExtraProperties(request.getExtraProperties());
        }

        user = userRepository.save(user);
        return toDto(user);
    }

    /**
     * 删除用户（软删除）；发布 {@link UserDeletedEvent} 供 health 联动注销医务人员。
     */
    @Transactional
    public void delete(DeleteUserRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("用户ID不能为空");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("用户", request.getId()));

        // 保护：admin账户不能被删除
        if ("admin".equalsIgnoreCase(user.getUserName())) {
            throw new BusinessException("系统管理员账户不能被删除");
        }

        // 软删除：设置删除标记，而不是调用repository.delete()
        user.setIsDeleted(true);
        user.setDeletedAt(java.time.LocalDateTime.now());

        // 设置删除人
        Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
        if (currentUserId != null) {
            user.setDeletedBy(currentUserId);
        }

        userRepository.save(user);  // 保存更新（软删除）
        // Cmd：用户软删时同步软删登录角色绑定，避免残留 whale_user_roles
        this.softDeleteUserRoles(user.getId());
        // Event：发布 UserDeletedEvent，health 联动 retire 绑定 adm_practitioner / adm_pr_assign
        domainEventPublisher.publish(new UserDeletedEvent(user.getId(), currentUserId));
    }

    /**
     * 软删指定用户全部有效 {@code whale_user_roles} 行。
     *
     * @param userId 用户主键
     */
    private void softDeleteUserRoles(Long userId) {
        if (userId == null) {
            return;
        }
        List<UserRole> existingRows = userRoleRepository.findByUserId(userId);
        LocalDateTime deletedAt = LocalDateTime.now();
        for (UserRole row : existingRows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            row.setIsDeleted(true);
            row.setDeletedAt(deletedAt);
            userRoleRepository.save(row);
        }
    }

    /**
     * 启用用户
     */
    public void enable(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("用户ID不能为空");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("用户", request.getId()));
        user.setIsActive(true);
        userRepository.save(user);
    }

    /**
     * 禁用用户
     */
    public void disable(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("用户ID不能为空");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("用户", request.getId()));
        user.setIsActive(false);
        userRepository.save(user);
    }

    /**
     * 锁定用户
     */
    public void lock(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("用户ID不能为空");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("用户", request.getId()));
        user.setIsLockedOut(true);
        user.setLockoutEnd(LocalDateTime.now().plusMinutes(30));
        userRepository.save(user);
    }

    /**
     * 解锁用户
     */
    public void unlock(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("用户ID不能为空");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("用户", request.getId()));
        user.setIsLockedOut(false);
        user.setLockoutEnd(null);
        user.setAccessFailedCount(0);
        userRepository.save(user);
    }

    /**
     * 修改密码（用户管理「改密」）。
     * <p>入参与前端 {@code { id, newPassword }} 对齐为单 DTO，避免双简单类型依赖方法参数名绑定；
     * 写哈希后同步解锁，避免「密码已改但仍因锁定无法登录」被误认为改密无效。</p>
     *
     * @param request 用户 ID 与新密码
     */
    public void changePassword(ResetPasswordRequest request) {
        resetPassword(request);
    }

    /**
     * 重置密码：校验入参、更新密码哈希，并清除锁定状态。
     *
     * @param request 用户 ID 与新密码
     */
    public void resetPassword(ResetPasswordRequest request) {
        if (request == null) {
            throw new ValidationException("重置密码请求不能为空");
        }
        if (request.getId() == null) {
            throw new ValidationException("用户ID不能为空");
        }
        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            throw new ValidationException("新密码不能为空");
        }
        String newPassword = request.getNewPassword().trim();
        if (newPassword.length() < 6) {
            throw new ValidationException("密码长度至少6位");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("用户", request.getId()));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // 重置/改密后解锁，避免旧锁定状态挡住新密码登录
        user.setIsLockedOut(false);
        user.setLockoutEnd(null);
        user.setAccessFailedCount(0);
        userRepository.save(user);
    }

    /**
     * 分配角色
     */
    public void assignRoles(Long id, AssignRolesRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("用户", id));

        List<UserRole> allRoles = userRoleRepository.findAllByUserId(id);

        Set<Long> newRoleIds = new HashSet<>(
            request.getRoleIds() != null ? request.getRoleIds() : List.of());
        ensureAllRolesForAdminUser(user, newRoleIds);
        Set<Long> satisfiedRoleIds = new HashSet<>();

        for (UserRole existingRole : allRoles) {
            Long roleId = existingRole.getRoleId();

            if (newRoleIds.contains(roleId)) {
                if (Boolean.TRUE.equals(existingRole.getIsDeleted())) {
                    existingRole.setIsDeleted(false);
                    existingRole.setDeletedAt(null);
                    existingRole.setDeletedBy(null);
                    userRoleRepository.save(existingRole);
                }
                satisfiedRoleIds.add(roleId);
            } else if (!Boolean.TRUE.equals(existingRole.getIsDeleted())) {
                existingRole.setIsDeleted(true);
                existingRole.setDeletedAt(LocalDateTime.now());
                userRoleRepository.save(existingRole);
            }
        }

        for (Long roleId : newRoleIds) {
            if (!satisfiedRoleIds.contains(roleId)) {
                UserRole userRole = new UserRole();
                userRole.setUserId(id);
                userRole.setRoleId(roleId);
                userRoleRepository.save(userRole);
            }
        }
    }

    /**
     * 岗位分配追加登录角色：已存在有效绑定时跳过。
     */
    public void addRoleIfAbsent(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            return;
        }
        userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("用户", userId));
        roleRepository.findById(roleId)
            .orElseThrow(() -> new EntityNotFoundException("角色", roleId));

        Optional<UserRole> existing = userRoleRepository.findByUserIdAndRoleId(userId, roleId);
        if (existing.isPresent()) {
            UserRole row = existing.get();
            if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                return;
            }
            row.setIsDeleted(false);
            row.setDeletedAt(null);
            row.setDeletedBy(null);
            userRoleRepository.save(row);
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRoleRepository.save(userRole);
    }

    /**
     * 岗位删除后移除登录角色：仅删除对应 roleId 绑定，不影响其它角色。
     */
    public void removeRoleIfPresent(Long userId, Long roleId) {
        if (userId == null || roleId == null) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (isAdminAccount(user)) {
            return;
        }
        List<UserRole> existingRows = userRoleRepository.findByUserId(userId);
        for (UserRole row : existingRows) {
            if (!roleId.equals(row.getRoleId())) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            row.setIsDeleted(true);
            row.setDeletedAt(LocalDateTime.now());
            userRoleRepository.save(row);
        }
    }

    /**
     * 判定是否为平台 admin 登录账号（用户名 {@code admin}，大小写不敏感）。
     *
     * <p>与角色全量同步、科室/药房运行时超管范围同源判定。</p>
     *
     * @param userId 用户主键；{@code null} 时返回 {@code false}
     * @return {@code true} 表示平台 admin 账号
     */
    public boolean isAdminAccount(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId).map(UserAppService::isAdminAccount).orElse(false);
    }

    /**
     * 将平台 admin 账号与当前租户下全部有效角色对齐（新建角色、打开用户详情时补偿同步）。
     */
    public void syncAllRolesForAdminUser(Long userId) {
        if (userId == null) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (!isAdminAccount(user)) {
            return;
        }
        AssignRolesRequest request = new AssignRolesRequest();
        request.setRoleIds(listAllActiveRoleIds());
        assignRoles(userId, request);
    }

    /**
     * 新建角色后追加至 admin 账号（admin 须拥有全部角色）。
     */
    public void attachRoleToAdminUser(Long roleId) {
        if (roleId == null) {
            return;
        }
        userRepository.findByUserName("admin")
            .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
            .ifPresent(user -> addRoleIfAbsent(user.getId(), roleId));
    }

    /**
     * 按平台角色主键解析 {@code whale_roles.code}；未命中时返回 {@code null}。
     *
     * @param roleId 平台角色主键
     * @return 角色 code 或 null
     */
    public String findRoleCodeById(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roleRepository.findById(roleId).map(Role::getCode).orElse(null);
    }

    /**
     * 按 {@code whale_roles.code} 解析平台角色主键。
     *
     * @param roleCode 平台角色 code，空白时返回 null
     * @return 角色主键或 null
     */
    public Long findRoleIdByCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return roleRepository.findByCode(roleCode.trim()).map(Role::getId).orElse(null);
    }

    /**
     * 内置静态角色 code（大写）→ id 映射，供岗位查人收窄等高频路径缓存。
     *
     * @return 不可变映射视图
     */
    public Map<String, Long> findStaticRoleCodeToIdMap() {
        Map<String, Long> map = new HashMap<>();
        for (Role role : roleRepository.findByIsStaticTrueAndIsDeletedFalseOrderByIdAsc()) {
            if (role == null || role.getCode() == null || role.getId() == null) {
                continue;
            }
            map.put(role.getCode().trim().toUpperCase(Locale.ROOT), role.getId());
        }
        return Map.copyOf(map);
    }

    /**
     * 批量解析仍绑定且启用的用户 id（未软删、{@code isActive=true}）。
     *
     * @param userIds 候选用户主键
     * @return 仍有效的用户 id 集合
     */
    public Set<Long> findActiveBoundUserIds(Iterable<Long> userIds) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long userId : userIds) {
                if (userId != null) {
                    distinct.add(userId);
                }
            }
        }
        if (distinct.isEmpty()) {
            return Set.of();
        }
        Set<Long> activeUserIds = new HashSet<>();
        for (User user : userRepository.findAllById(distinct)) {
            if (user == null || user.getId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                continue;
            }
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                continue;
            }
            activeUserIds.add(user.getId());
        }
        return activeUserIds;
    }

    /**
     * 持有指定平台角色且账号仍启用的用户主键列表（读 {@code whale_user_roles}）。
     *
     * @param roleCode 平台角色 code，如 {@code ROLE_FINANCE_APPROVER}；空白返回空列表
     * @return 启用用户主键；角色不存在或无绑定时为空列表
     */
    public List<Long> listActiveUserIdsByRoleCode(String roleCode) {
        Long roleId = this.findRoleIdByCode(roleCode);
        if (roleId == null) {
            return List.of();
        }
        List<UserRole> userRoles = this.userRoleRepository.findByRoleId(roleId);
        LinkedHashSet<Long> candidateUserIds = new LinkedHashSet<>();
        for (UserRole userRole : userRoles) {
            if (userRole == null || userRole.getUserId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(userRole.getIsDeleted())) {
                continue;
            }
            candidateUserIds.add(userRole.getUserId());
        }
        if (candidateUserIds.isEmpty()) {
            return List.of();
        }
        Set<Long> activeUserIds = this.findActiveBoundUserIds(candidateUserIds);
        List<Long> result = new ArrayList<>();
        for (Long userId : candidateUserIds) {
            if (activeUserIds.contains(userId)) {
                result.add(userId);
            }
        }
        return result;
    }

    /**
     * 获取用户已分配角色列表；admin 账号会先同步全量角色再回读。
     *
     * @param id 用户主键；为 null 时返回空列表，避免 findAllById(null) 触发全表误查
     * @return 角色 DTO 列表（不含已软删关联）
     */
    public List<RoleDto> getRoles(Long id) {
        // Gate：无用户主键时直接空列表，防止脏调用拖垮角色仓储
        if (id == null) {
            return List.of();
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户", id));
        if (isAdminAccount(user)) {
            syncAllRolesForAdminUser(id);
        }

        List<UserRole> userRoles = userRoleRepository.findByUserId(id);
        List<Long> roleIds = userRoles.stream()
                .filter(ur -> !Boolean.TRUE.equals(ur.getIsDeleted()))
                .map(UserRole::getRoleId)
                // Pre：跳过 role_id 为空的脏关联行，避免 findAllById 误匹配
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Role> roles = roleRepository.findAllById(roleIds);
        return roles.stream()
                .map(role -> {
                    RoleDto dto = new RoleDto();
                    BeanUtils.copyProperties(role, dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取用户声明列表
     */
    public List<UserClaimDto> getClaims(Long id) {
        userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户", id));

        List<UserClaim> claims = userClaimRepository.findByUserId(id);
        return claims.stream()
                .map(claim -> {
                    UserClaimDto dto = new UserClaimDto();
                    BeanUtils.copyProperties(claim, dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 添加用户声明
     */
    public UserClaimDto addClaim(Long id, AddUserClaimRequest request) {
        userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户", id));

        // 检查声明是否已存在
        if (userClaimRepository.findByUserIdAndClaimTypeAndClaimValue(
                id, request.getClaimType(), request.getClaimValue()).isPresent()) {
            throw new BusinessException("用户声明已存在");
        }

        UserClaim claim = new UserClaim();
        claim.setUserId(id);
        claim.setClaimType(request.getClaimType());
        claim.setClaimValue(request.getClaimValue());
        claim.setTenantId(TenantUtils.getCurrentTenantId());

        claim = userClaimRepository.save(claim);

        UserClaimDto dto = new UserClaimDto();
        BeanUtils.copyProperties(claim, dto);
        return dto;
    }

    /**
     * 删除用户声明
     */
    public void removeClaim(Long id, String claimType, String claimValue) {
        userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户", id));

        userClaimRepository.deleteByUserIdAndClaimTypeAndClaimValue(id, claimType, claimValue);
    }

    /**
     * 删除用户的所有指定类型声明
     */
    public void removeClaimsByType(Long id, String claimType) {
        userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户", id));

        userClaimRepository.deleteByUserIdAndClaimType(id, claimType);
    }

    /**
     * 租户可见性：与 {@code whale_user_tenants} 一致（替代已移除的 {@code whale_users.tenant_id}）。
     * <p>有当前租户时：属于该租户的用户，或无任何租户关联的「全局」用户；宿主上下文：仅无任何租户关联的用户。</p>
     */
    private Specification<User> userTenantVisibilitySpec() {
        return (root, query, cb) -> {
            if (query == null) {
                return cb.conjunction();
            }
            Long tid = TenantUtils.getCurrentTenantId();
            if (tid != null) {
                Subquery<Long> inThisTenant = query.subquery(Long.class);
                Root<UserTenant> ut = inThisTenant.from(UserTenant.class);
                inThisTenant.select(ut.get("id"));
                inThisTenant.where(
                    cb.equal(ut.get("userId"), root.get("id")),
                    cb.equal(ut.get("tenantId"), tid),
                    cb.isFalse(ut.get("isDeleted"))
                );
                Subquery<Long> anyMembership = query.subquery(Long.class);
                Root<UserTenant> ut2 = anyMembership.from(UserTenant.class);
                anyMembership.select(ut2.get("id"));
                anyMembership.where(
                    cb.equal(ut2.get("userId"), root.get("id")),
                    cb.isFalse(ut2.get("isDeleted"))
                );
                return cb.or(cb.exists(inThisTenant), cb.not(cb.exists(anyMembership)));
            }
            // 宿主/未选租户：不按租户过滤（用户-租户关系仅在 whale_user_tenants）
            return cb.conjunction();
        };
    }

    /**
     * 构建查询条件
     */
    private Specification<User> buildSpecification(UserQueryRequest request) {
        return userTenantVisibilitySpec()
            .and(SpecificationUtils.<User>notDeleted())
            .and((root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                    String keyword = "%" + request.getKeyword().trim() + "%";
                    Predicate userNameLike = cb.like(root.get("userName"), keyword);
                    Predicate emailLike = cb.like(root.get("email"), keyword);
                    Predicate nameLike = cb.like(root.get("name"), keyword);
                    Predicate surnameLike = cb.like(root.get("surname"), keyword);
                    Predicate phoneLike = cb.like(root.get("phoneNumber"), keyword);
                    predicates.add(cb.or(userNameLike, emailLike, nameLike, surnameLike, phoneLike));
                }

                if (request.getIsActive() != null) {
                    predicates.add(cb.equal(root.get("isActive"), request.getIsActive()));
                }

                if (request.getIsLockedOut() != null) {
                    predicates.add(cb.equal(root.get("isLockedOut"), request.getIsLockedOut()));
                }

                // 角色多选：命中任一所选角色即纳入结果
                List<Long> roleIds = request.getRoleIds();
                if (roleIds != null && !roleIds.isEmpty() && query != null) {
                    Subquery<Long> roleSub = query.subquery(Long.class);
                    Root<UserRole> ur = roleSub.from(UserRole.class);
                    roleSub.select(ur.get("id"));
                    roleSub.where(
                        cb.equal(ur.get("userId"), root.get("id")),
                        ur.get("roleId").in(roleIds),
                        cb.isFalse(ur.get("isDeleted"))
                    );
                    predicates.add(cb.exists(roleSub));
                }

                return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
            });
    }

    /**
     * 转换为DTO
     */
    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        BeanUtils.copyProperties(user, dto);
        List<Long> tenantIds = userTenantRepository.findByUserId(user.getId()).stream()
            .map(UserTenant::getTenantId)
            .toList();
        dto.setTenantId(tenantIds.isEmpty() ? null : tenantIds.get(0));
        return dto;
    }

    /**
     * admin 账号须拥有当前租户下全部有效角色，不可通过界面手动裁剪。
     */
    private void ensureAllRolesForAdminUser(User user, Set<Long> newRoleIds) {
        if (user == null || newRoleIds == null || !isAdminAccount(user)) {
            return;
        }
        newRoleIds.addAll(listAllActiveRoleIds());
    }

    private static boolean isAdminAccount(User user) {
        return user != null
            && user.getUserName() != null
            && "admin".equalsIgnoreCase(user.getUserName().trim());
    }

    private List<Long> listAllActiveRoleIds() {
        Specification<Role> spec = SpecificationUtils.<Role>tenantFilter()
            .and(SpecificationUtils.notDeleted());
        List<Long> roleIds = new ArrayList<>();
        for (Role role : roleRepository.findAll(spec)) {
            if (role.getId() != null) {
                roleIds.add(role.getId());
            }
        }
        return roleIds;
    }

    /**
     * 用户名占用校验：仅未软删用户（{@code is_deleted = false}）参与判重。
     */

    private void assertActiveUserNameAvailable(String userName, Long excludeUserId) {
        if (userName == null || userName.isBlank()) {
            return;
        }
        if (userRepository.exists(activeUserByUserNameSpec(userName.trim(), excludeUserId))) {
            throw new ValidationException("用户名已存在");
        }
    }

    /**
     * 邮箱占用校验：仅未软删用户参与判重。
     */
    private void assertActiveEmailAvailable(String email, Long excludeUserId) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (userRepository.exists(activeUserByEmailSpec(email.trim(), excludeUserId))) {
            throw new ValidationException(
                excludeUserId == null ? "邮箱已存在" : "邮箱已被其他用户使用");
        }
    }

    private Specification<User> activeUserByUserNameSpec(String userName, Long excludeUserId) {
        return SpecificationUtils.<User>notDeleted()
            .and(SpecificationUtils.equal("userName", userName))
            .and(excludeUserIdSpec(excludeUserId));
    }

    private Specification<User> activeUserByEmailSpec(String email, Long excludeUserId) {
        return SpecificationUtils.<User>notDeleted()
            .and(SpecificationUtils.equal("email", email))
            .and(excludeUserIdSpec(excludeUserId));
    }

    private Specification<User> excludeUserIdSpec(Long excludeUserId) {
        return (root, query, cb) -> excludeUserId == null
            ? cb.conjunction()
            : cb.notEqual(root.get("id"), excludeUserId);
    }
}
