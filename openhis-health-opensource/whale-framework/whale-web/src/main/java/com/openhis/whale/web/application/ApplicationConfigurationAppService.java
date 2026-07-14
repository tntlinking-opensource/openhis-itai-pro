/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.web.application;

import com.openhis.whale.core.application.dto.*;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.core.infrastructure.permission.IPermissionChecker;
import com.openhis.whale.identity.application.session.LoginSessionContext;
import com.openhis.whale.identity.domain.Role;
import com.openhis.whale.identity.domain.User;
import com.openhis.whale.identity.domain.UserRole;
import com.openhis.whale.identity.infrastructure.repository.RoleRepository;
import com.openhis.whale.identity.infrastructure.repository.UserRepository;
import com.openhis.whale.identity.infrastructure.repository.UserRoleRepository;
import com.openhis.whale.identity.domain.UserTenant;
import com.openhis.whale.identity.infrastructure.repository.UserTenantRepository;
import com.openhis.whale.permission.domain.Permission;
import com.openhis.whale.permission.infrastructure.repository.PermissionRepository;
import com.openhis.whale.setting.domain.Setting;
import com.openhis.whale.setting.infrastructure.repository.SettingRepository;
import com.openhis.whale.tenant.domain.Tenant;
import com.openhis.whale.tenant.infrastructure.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用配置服务
 * 参考ABP的AbpApplicationConfigurationAppService
 * 提供前端需要的所有配置信息
 */
@Service
public class ApplicationConfigurationAppService implements com.openhis.whale.core.application.IApplicationConfigurationAppService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfigurationAppService.class);

    @Autowired
    private LoginSessionContext loginSession;

    @Autowired
    private TenantContext tenantContext;

    @Autowired
    private IPermissionChecker permissionChecker;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserTenantRepository userTenantRepository;

    /**
     * 获取应用配置
     * 包含前端需要的所有配置信息：权限、用户信息、租户信息等
     */
    public ApplicationConfigurationDto get() {
        logger.debug("获取应用配置...");

        ApplicationConfigurationDto result = new ApplicationConfigurationDto();

        // 认证配置（权限策略）
        result.setAuth(getAuthConfig());

        // 当前用户信息
        result.setCurrentUser(getCurrentUser());

        // 当前租户信息
        result.setCurrentTenant(getCurrentTenant());

        // 多租户配置
        result.setMultiTenancy(getMultiTenancy());

        // 设置配置
        result.setSetting(getSettingConfig());

        logger.debug("应用配置获取完成");

        return result;
    }

    /**
     * 获取应用配置（指定用户ID和租户ID）
     * 用于登录时获取配置，此时用户还未设置到SecurityContext
     */
    public ApplicationConfigurationDto get(Long userId, Long tenantId) {
        logger.debug("获取应用配置: userId={}, tenantId={}", userId, tenantId);

        ApplicationConfigurationDto result = new ApplicationConfigurationDto();

        // 临时设置租户上下文
        Long originalTenantId = tenantContext.getCurrentTenantId();
        try {
            if (tenantId != null) {
                tenantContext.setCurrentTenantId(tenantId);
            }

            // 认证配置（权限策略）
            result.setAuth(getAuthConfig(userId));

            // 当前用户信息
            result.setCurrentUser(getCurrentUser(userId, tenantId));

            // 当前租户信息
            result.setCurrentTenant(getCurrentTenant(tenantId));

            // 多租户配置
            result.setMultiTenancy(getMultiTenancy());

            // 设置配置
            result.setSetting(getSettingConfig(tenantId));
        } finally {
            // 恢复原始租户上下文
            if (originalTenantId != null) {
                tenantContext.setCurrentTenantId(originalTenantId);
            } else {
                tenantContext.clear();
            }
        }

        logger.debug("应用配置获取完成");

        return result;
    }

    /**
     * 获取认证配置（权限策略）
     */
    private ApplicationAuthConfigurationDto getAuthConfig() {
        if (!loginSession.isAuthenticated()) {
            return new ApplicationAuthConfigurationDto();
        }

        Long userId = loginSession.getCurrentUserId();
        if (userId == null) {
            return new ApplicationAuthConfigurationDto();
        }

        return getAuthConfig(userId);
    }

    /**
     * 获取认证配置（权限策略）- 指定用户ID
     */
    private ApplicationAuthConfigurationDto getAuthConfig(Long userId) {
        ApplicationAuthConfigurationDto authConfig = new ApplicationAuthConfigurationDto();

        Set<String> grantedNames = permissionChecker.getGrantedPermissionNames(userId);
        if (grantedNames.isEmpty()) {
            return authConfig;
        }

        List<Permission> allPermissions = permissionRepository.findAll();
        for (Permission permission : allPermissions) {
            String permissionName = permission.getName();
            if (permissionName != null && grantedNames.contains(permissionName)) {
                authConfig.getGrantedPolicies().put(permissionName, true);
            }
        }

        return authConfig;
    }

    /**
     * 获取当前用户信息
     */
    private CurrentUserDto getCurrentUser() {
        if (!loginSession.isAuthenticated()) {
            CurrentUserDto userDto = new CurrentUserDto();
            userDto.setIsAuthenticated(false);
            return userDto;
        }

        Long userId = loginSession.getCurrentUserId();
        if (userId == null) {
            CurrentUserDto userDto = new CurrentUserDto();
            userDto.setIsAuthenticated(false);
            return userDto;
        }

        return getCurrentUser(userId, null);
    }

    /**
     * 获取当前用户信息 - 指定用户ID和租户ID
     */
    private CurrentUserDto getCurrentUser(Long userId, Long tenantId) {
        CurrentUserDto userDto = new CurrentUserDto();
        // Gate：无用户主键时按未登录返回，避免后续仓储按 null 误查
        if (userId == null) {
            userDto.setIsAuthenticated(false);
            return userDto;
        }
        userDto.setIsAuthenticated(true);
        userDto.setId(userId);

        // 获取用户详细信息
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            userDto.setUserName(user.getUserName());
            Long resolvedTenant = tenantId != null ? tenantId : resolvePrimaryTenantForUser(user.getId());
            userDto.setTenantId(resolvedTenant);
            userDto.setEmail(user.getEmail());
            // 注意：User实体中可能没有name和surName字段，这里需要根据实际情况调整
            // userDto.setName(user.getName());
            // userDto.setSurName(user.getSurName());
            userDto.setPhoneNumber(user.getPhoneNumber());
        }

        // 获取用户角色（仅有效绑定）
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        List<Long> roleIds = userRoles.stream()
                .filter(ur -> !Boolean.TRUE.equals(ur.getIsDeleted()))
                .map(UserRole::getRoleId)
                // Pre：跳过 role_id 为空的脏关联行，避免角色批量加载异常
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!roleIds.isEmpty()) {
            List<Role> roles = roleRepository.findAllById(roleIds);
            List<String> roleNames = roles.stream()
                    .map(Role::getName)
                    .collect(Collectors.toList());
            userDto.setRoles(roleNames);
        }

        // 优先从 LoginSessionContext 获取 practitionerId（已登录时）
        Long practitionerId = loginSession.getCurrentPractitionerId();
        if (practitionerId != null) {
            userDto.setPractitionerId(practitionerId);
        }

        fillImpersonatorFields(userDto);

        return userDto;
    }

    @Override
    public void applyImpersonatorFields(
            CurrentUserDto userDto,
            Long impersonatorUserId,
            String impersonatorUserName,
            Long impersonatorTenantId) {
        if (userDto == null || impersonatorUserId == null) {
            return;
        }
        userDto.setImpersonatorUserId(impersonatorUserId);
        userDto.setImpersonatorUserName(impersonatorUserName);
        userDto.setImpersonatorTenantId(impersonatorTenantId);
        fillImpersonatorTenantName(userDto, impersonatorTenantId);
    }

    private void fillImpersonatorFields(CurrentUserDto userDto) {
        if (!loginSession.isAuthenticated() || !loginSession.isImpersonating()) {
            return;
        }
        applyImpersonatorFields(
                userDto,
                loginSession.getImpersonatorUserId(),
                loginSession.getImpersonatorUserName(),
                loginSession.getImpersonatorTenantId());
    }

    private void fillImpersonatorTenantName(CurrentUserDto userDto, Long impersonatorTenantId) {
        if (impersonatorTenantId == null) {
            return;
        }
        tenantRepository.findById(impersonatorTenantId).ifPresent(tenant -> {
            userDto.setImpersonatorTenantName(tenant.getName());
        });
    }

    /**
     * 获取当前租户信息
     */
    private CurrentTenantDto getCurrentTenant() {
        Long tenantId = tenantContext.getCurrentTenantId();
        return getCurrentTenant(tenantId);
    }

    /**
     * 获取当前租户信息 - 指定租户ID
     */
    private CurrentTenantDto getCurrentTenant(Long tenantId) {
        CurrentTenantDto tenantDto = new CurrentTenantDto();
        tenantDto.setId(tenantId);
        tenantDto.setIsAvailable(tenantId != null);

        // 从数据库查询真实的租户信息
        if (tenantId != null) {
            try {
                Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
                if (tenant != null) {
                    tenantDto.setName(tenant.getName());
                } else {
                    tenantDto.setName("未知租户-" + tenantId);
                }
            } catch (Exception e) {
                logger.warn("获取租户信息失败: tenantId={}, error={}", tenantId, e.getMessage());
                tenantDto.setName("租户-" + tenantId);
            }
        }

        return tenantDto;
    }

    /**
     * 获取多租户配置
     */
    private MultiTenancyInfoDto getMultiTenancy() {
        MultiTenancyInfoDto multiTenancy = new MultiTenancyInfoDto();
        // 默认启用多租户
        multiTenancy.setIsEnabled(true);
        return multiTenancy;
    }

    /**
     * 获取设置配置
     * 获取所有对客户端可见的设置值
     */
    private ApplicationSettingConfigurationDto getSettingConfig() {
        Long tenantId = tenantContext.getCurrentTenantId();
        return getSettingConfig(tenantId);
    }

    /**
     * 获取设置配置 - 指定租户ID
     * 获取所有对客户端可见的设置值
     * 包括：
     * 1. 系统级别的设置（providerName为null，tenantId为null）
     * 2. 当前租户的设置（如果指定了租户ID，providerName为null）
     *
     * 注意：只返回系统级别和租户级别的设置，不返回用户级别的设置（providerName为"U"）
     */
    private ApplicationSettingConfigurationDto getSettingConfig(Long tenantId) {
        ApplicationSettingConfigurationDto settingConfig = new ApplicationSettingConfigurationDto();

        try {
            // 获取系统级别设置 (tenantId is null)
            List<Setting> systemSettings = settingRepository.findByTenantId(null);
            systemSettings.stream()
                    .filter(s -> s.getProviderName() == null
                            && !Boolean.TRUE.equals(s.getIsDeleted())
                            && s.getValue() != null)
                    .forEach(s -> settingConfig.getValues().put(s.getName(), s.getValue()));

            // 如果有租户ID，获取租户级别设置并覆盖系统设置
            if (tenantId != null) {
                List<Setting> tenantSettings = settingRepository.findByTenantId(tenantId);
                tenantSettings.stream()
                        .filter(s -> s.getProviderName() == null
                                && !Boolean.TRUE.equals(s.getIsDeleted())
                                && s.getValue() != null)
                        .forEach(s -> settingConfig.getValues().put(s.getName(), s.getValue()));
            }
        } catch (Exception e) {
            logger.warn("获取设置配置时发生错误: {}", e.getMessage());
            // 即使获取设置失败，也不影响其他配置的返回
        }

        return settingConfig;
    }

    /** 用户主数据不再含 tenant_id，展示用租户取自 {@code whale_user_tenants} */
    private Long resolvePrimaryTenantForUser(Long userId) {
        return userTenantRepository.findByUserId(userId).stream()
            .map(UserTenant::getTenantId)
            .findFirst()
            .orElse(null);
    }
}
