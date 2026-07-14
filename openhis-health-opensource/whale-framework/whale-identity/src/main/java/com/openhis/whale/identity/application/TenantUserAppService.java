/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application;

import com.openhis.whale.core.infrastructure.multitenancy.PlatformTenantAccessPolicy;
import com.openhis.whale.core.infrastructure.permission.RequirePermission;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.identity.application.dto.*;
import com.openhis.whale.identity.domain.User;
import com.openhis.whale.identity.domain.UserTenant;
import com.openhis.whale.identity.infrastructure.repository.UserRepository;
import com.openhis.whale.identity.infrastructure.repository.UserTenantRepository;
import com.openhis.whale.tenant.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户用户管理服务
 * 管理租户与用户的关联关系
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserAppService {

    private final UserTenantRepository userTenantRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final PlatformTenantAccessPolicy platformTenantAccessPolicy;

    /**
     * 获取租户的用户列表
     */
    @RequirePermission("Navigation.TenantManagement")
    public List<UserDto> getUsers(IdRequest request) {
        if (request.getId() == null) {
            throw new com.openhis.whale.core.application.exception.ValidationException("租户ID不能为空");
        }

        platformTenantAccessPolicy.assertCanManageTenant(currentOperatorResolver.findOperatorIdOrNull(), request.getId());

        tenantRepository.findById(request.getId())
                .orElseThrow(() -> new com.openhis.whale.core.application.exception.EntityNotFoundException("租户", request.getId()));

        // 获取该租户下的所有用户关联（过滤已删除的）
        List<UserTenant> userTenants = userTenantRepository.findByTenantId(request.getId()).stream()
                .filter(ut -> !Boolean.TRUE.equals(ut.getIsDeleted()))
                .collect(Collectors.toList());

        // 获取用户ID列表
        List<Long> userIds = userTenants.stream()
                .map(UserTenant::getUserId)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询用户详情（避免懒加载问题）
        List<User> users = userRepository.findAllById(userIds);
        return users.stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .map(user -> {
                    UserDto dto = new UserDto();
                    BeanUtils.copyProperties(user, dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 分配用户到租户
     */
    @RequirePermission("Navigation.TenantManagement")
    public void assignUsers(AssignUsersToTenantRequest request) {
        if (request.getTenantId() == null) {
            throw new com.openhis.whale.core.application.exception.ValidationException("租户ID不能为空");
        }
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new com.openhis.whale.core.application.exception.ValidationException("用户ID列表不能为空");
        }

        platformTenantAccessPolicy.assertCanManageTenant(currentOperatorResolver.findOperatorIdOrNull(), request.getTenantId());

        tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new com.openhis.whale.core.application.exception.EntityNotFoundException("租户", request.getTenantId()));

        for (Long userId : request.getUserIds()) {
            // 检查关联是否已存在（包括已删除的）
            UserTenant existing = userTenantRepository.findByUserIdAndTenantId(userId, request.getTenantId());

            if (existing == null) {
                // 创建新关联
                UserTenant userTenant = new UserTenant();
                userTenant.setUserId(userId);
                userTenant.setTenantId(request.getTenantId());
                userTenantRepository.save(userTenant);
            } else if (Boolean.TRUE.equals(existing.getIsDeleted())) {
                // 恢复已删除的关联
                existing.setIsDeleted(false);
                existing.setDeletedAt(null);
                existing.setDeletedBy(null);
                userTenantRepository.save(existing);
            }
            // 如果关联已存在且未删除，则跳过
        }
    }

    /**
     * 从租户移除用户
     */
    @RequirePermission("Navigation.TenantManagement")
    public void removeUsers(RemoveUsersFromTenantRequest request) {
        if (request.getTenantId() == null) {
            throw new com.openhis.whale.core.application.exception.ValidationException("租户ID不能为空");
        }
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new com.openhis.whale.core.application.exception.ValidationException("用户ID列表不能为空");
        }

        platformTenantAccessPolicy.assertCanManageTenant(currentOperatorResolver.findOperatorIdOrNull(), request.getTenantId());

        tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new com.openhis.whale.core.application.exception.EntityNotFoundException("租户", request.getTenantId()));

        // 软删除用户-租户关联
        for (Long userId : request.getUserIds()) {
            UserTenant userTenant = userTenantRepository.findByUserIdAndTenantId(userId, request.getTenantId());
            if (userTenant != null && !Boolean.TRUE.equals(userTenant.getIsDeleted())) {
                userTenant.setIsDeleted(true);
                userTenant.setDeletedAt(LocalDateTime.now());
                Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
                if (currentUserId != null) {
                    userTenant.setDeletedBy(currentUserId);
                }
                userTenantRepository.save(userTenant);
            }
        }
    }
}
