/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.identity.domain.Role;
import com.openhis.whale.identity.infrastructure.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 角色管理服务（Domain Service）
 */
@Service
public class RoleManager {

    @Autowired
    private RoleRepository roleRepository;

    /**
     * 创建角色
     */
    public Role create(String name, String displayName, String code) {
        // 检查角色名称是否已存在
        if (roleRepository.existsByName(name)) {
            throw new ValidationException("角色名称已存在");
        }

        // 如果提供了代码，检查代码是否已存在
        if (code != null && !code.trim().isEmpty()) {
            roleRepository.findByCode(code).ifPresent(r -> {
                throw new ValidationException("角色代码已存在");
            });
        }

        // 创建角色
        Role role = new Role();
        role.setName(name);
        role.setDisplayName(displayName);
        role.setCode(code);
        role.setIsDefault(false);
        role.setIsStatic(false);

        return role;
    }

    /**
     * 删除角色
     */
    public void delete(Role role) {
        // 静态角色不允许删除
        if (role.getIsStatic()) {
            throw new BusinessException("静态角色不允许删除");
        }

        // 可以在这里添加其他业务逻辑，比如检查是否有用户使用该角色
        roleRepository.delete(role);
    }

    /**
     * 检查角色是否可以修改
     */
    public boolean canModify(Role role) {
        return !role.getIsStatic();
    }
}
