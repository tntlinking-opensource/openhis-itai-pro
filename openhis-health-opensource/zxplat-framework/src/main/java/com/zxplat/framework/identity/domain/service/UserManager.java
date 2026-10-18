/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.domain.service;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.identity.domain.User;
import com.zxplat.framework.identity.infrastructure.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户管理服务（Domain Service）
 */
@Service
public class UserManager {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordValidator passwordValidator;

    /**
     * 创建用户
     */
    public User create(String userName, String email, String password) {
        // 检查用户名是否已存在
        if (userRepository.existsByUserName(userName)) {
            throw new ValidationException("用户名已存在");
        }

        // 检查邮箱是否已存在（仅当邮箱不为空时）
        if (email != null && !email.trim().isEmpty()) {
            if (userRepository.existsByEmail(email)) {
                throw new ValidationException("邮箱已存在");
            }
        }

        // 验证密码
        passwordValidator.validate(password);

        // 创建用户
        User user = new User();
        user.setUserName(userName);
        user.setEmail(email);  // 可以为null
        user.setIsActive(true);
        user.setIsLockedOut(false);
        user.setAccessFailedCount(0);

        return user;
    }

    /**
     * 修改密码
     */
    public void changePassword(User user, String newPassword) {
        // 验证新密码
        passwordValidator.validate(newPassword);

        // 可以在这里添加密码历史检查等业务逻辑
        // user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    /**
     * 锁定用户
     */
    public void lockUser(User user, int lockoutMinutes) {
        user.setIsLockedOut(true);
        user.setLockoutEnd(LocalDateTime.now().plusMinutes(lockoutMinutes));
        userRepository.save(user);
    }

    /**
     * 解锁用户
     */
    public void unlockUser(User user) {
        user.setIsLockedOut(false);
        user.setLockoutEnd(null);
        user.setAccessFailedCount(0);
        userRepository.save(user);
    }

    /**
     * 处理登录失败
     */
    public void handleLoginFailure(User user, int maxFailures, int lockoutMinutes) {
        int failures = user.getAccessFailedCount() + 1;
        user.setAccessFailedCount(failures);

        if (failures >= maxFailures) {
            lockUser(user, lockoutMinutes);
        } else {
            userRepository.save(user);
        }
    }
}
