/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.starter.config;

import com.zxplat.framework.identity.domain.User;
import com.zxplat.framework.identity.infrastructure.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 开发/演示环境管理员账户修复器。
 *
 * <p>仅在账户不可用或默认密码不匹配时写库，避免每次启动强制 rehash 触发审计噪声。
 * 正式环境通过 {@code zxplat.bootstrap.reset-admin-password.enabled=false} 关闭
 *（见各 project / health-app 的 {@code prod} profile）。</p>
 */
@Component
@ConditionalOnProperty(
        name = "zxplat.bootstrap.reset-admin-password.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AdminPasswordInitializer {

    private static final Logger logger = LoggerFactory.getLogger(AdminPasswordInitializer.class);

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 检查管理员账户；仅在需要修复时保存。
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void initializeAdminPassword(ApplicationReadyEvent event) {
        try {
            Optional<User> adminUserOpt = userRepository.findById(1L);
            if (adminUserOpt.isEmpty()) {
                adminUserOpt = userRepository.findByUserName(ADMIN_USERNAME);
            }

            if (adminUserOpt.isEmpty()) {
                logger.error("未找到管理员用户（id=1 / userName={}），请检查 Flyway platform seed", ADMIN_USERNAME);
                return;
            }

            User adminUser = adminUserOpt.get();
            List<String> fixes = new ArrayList<>();

            if (Boolean.TRUE.equals(adminUser.getIsDeleted())) {
                adminUser.setIsDeleted(false);
                adminUser.setDeletedAt(null);
                adminUser.setDeletedBy(null);
                fixes.add("恢复软删");
            }
            if (!Boolean.TRUE.equals(adminUser.getIsActive())) {
                adminUser.setIsActive(true);
                fixes.add("激活");
            }
            if (Boolean.TRUE.equals(adminUser.getIsLockedOut())) {
                adminUser.setIsLockedOut(false);
                adminUser.setLockoutEnd(null);
                fixes.add("解锁");
            }
            if (adminUser.getAccessFailedCount() != null && adminUser.getAccessFailedCount() != 0) {
                adminUser.setAccessFailedCount(0);
                fixes.add("清零失败次数");
            }

            String hash = adminUser.getPasswordHash();
            boolean passwordOk = hash != null && !hash.isBlank()
                    && passwordEncoder.matches(ADMIN_PASSWORD, hash);
            if (!passwordOk) {
                adminUser.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
                fixes.add("重置默认密码");
            }

            if (fixes.isEmpty()) {
                logger.info("管理员账户已就绪（无需写库）: user={}", ADMIN_USERNAME);
                return;
            }

            userRepository.save(adminUser);
            logger.info("管理员账户已修复: user={}, changes={}", ADMIN_USERNAME, fixes);
        } catch (Exception e) {
            logger.error("检查/修复管理员账户失败: {}", e.getMessage(), e);
        }
    }
}
