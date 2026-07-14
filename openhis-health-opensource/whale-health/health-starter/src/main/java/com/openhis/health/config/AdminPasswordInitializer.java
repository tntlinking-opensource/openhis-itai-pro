/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.config;

import com.openhis.whale.identity.domain.User;
import com.openhis.whale.identity.infrastructure.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 管理员密码初始化器。
 *
 * <p>应用就绪时检查 id=1 或 userName=admin 的管理员账户：恢复软删、重置 BCrypt 哈希为默认密码，
 * 并确保账户处于激活且未锁定状态。{@code @Order(1)} 优先于其他启动日志输出。</p>
 */
@Component
public class AdminPasswordInitializer {

    private static final Logger logger = LoggerFactory.getLogger(AdminPasswordInitializer.class);

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 检查并强制更新管理员密码，确保开发/演示环境可凭默认账户登录。
     *
     * @param event 应用就绪事件，数据库迁移完成后触发
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // 在其他初始化之前执行
    public void initializeAdminPassword(ApplicationReadyEvent event) {
        try {
            logger.info(">>> 检查管理员账户...");

            // Pre：优先按种子 id=1 定位管理员，兼容历史库仅 userName 一致的场景
            Optional<User> adminUserOpt = userRepository.findById(1L);

            if (!adminUserOpt.isPresent()) {
                // id=1 不存在时回退按 userName 查找
                adminUserOpt = userRepository.findByUserName(ADMIN_USERNAME);
            }

            if (adminUserOpt.isPresent()) {
                User adminUser = adminUserOpt.get();

                // 软删管理员无法登录，启动时自动恢复以便开发环境可用
                if (Boolean.TRUE.equals(adminUser.getIsDeleted())) {
                    logger.warn("⚠️  管理员用户被软删除，正在恢复...");
                    adminUser.setIsDeleted(false);
                    adminUser.setDeletedAt(null);
                    adminUser.setDeletedBy(null);
                }

                // 强制同步默认密码，确保开发/演示环境可登录
                String newHash = passwordEncoder.encode(ADMIN_PASSWORD);
                adminUser.setPasswordHash(newHash);
                adminUser.setIsActive(true); // 确保用户是激活状态
                adminUser.setIsLockedOut(false); // 确保用户未被锁定
                adminUser.setAccessFailedCount(0); // 重置失败次数
                adminUser.setIsDeleted(false); // 确保未被软删除
                userRepository.save(adminUser);

                // 验证新哈希
                boolean newHashValid = passwordEncoder.matches(ADMIN_PASSWORD, newHash);
                if (newHashValid) {
                    logger.info(">>> 管理员账户就绪: user={}", ADMIN_USERNAME);
                } else {
                    logger.error("❌ 密码更新后验证失败！这不应该发生！");
                }
            } else {
                logger.error("❌ 未找到管理员用户，请检查数据库种子数据是否已执行");
                logger.error("💡 提示: 请确保 V2__Seed_data.sql 迁移脚本已成功执行");
                logger.error("💡 或者手动执行以下SQL创建管理员用户:");
                logger.error("   INSERT INTO whale_users (id, user_name, email, password_hash, is_active, tenant_id, created_at, is_deleted)");
                logger.error("   VALUES (1, 'admin', 'admin@example.com', '[BCrypt哈希]', true, NULL, NOW(), false);");
            }
        } catch (Exception e) {
            logger.error("❌ 初始化管理员密码时发生错误: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }
}
