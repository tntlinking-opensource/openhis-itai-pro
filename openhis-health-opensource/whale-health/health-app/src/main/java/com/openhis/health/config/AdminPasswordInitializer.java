/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // 在其他初始化之前执行
    public void initializeAdminPassword() {
        try {
            logger.info("🔍 开始检查管理员密码...");
            
            // 查找用户，包括可能被软删除的用户
            Optional<User> adminUserOpt = userRepository.findById(1L);
            
            if (!adminUserOpt.isPresent()) {
                // 如果按ID找不到，尝试按用户名查找
                adminUserOpt = userRepository.findByUserName(ADMIN_USERNAME);
            }
            
            if (adminUserOpt.isPresent()) {
                User adminUser = adminUserOpt.get();
                
                // 检查是否被软删除
                if (Boolean.TRUE.equals(adminUser.getIsDeleted())) {
                    logger.warn("⚠️  管理员用户被软删除，正在恢复...");
                    adminUser.setIsDeleted(false);
                    adminUser.setDeletedAt(null);
                    adminUser.setDeletedBy(null);
                }
                
                String currentHash = adminUser.getPasswordHash();
                
                logger.info("📋 找到管理员用户: id={}, userName={}, isActive={}, isLockedOut={}, isDeleted={}", 
                    adminUser.getId(), adminUser.getUserName(), adminUser.getIsActive(), 
                    adminUser.getIsLockedOut(), adminUser.getIsDeleted());
                logger.info("🔑 当前密码哈希: {}", currentHash);
                
                // 验证当前密码哈希是否正确
                boolean isValid = false;
                if (currentHash != null && !currentHash.isEmpty()) {
                    try {
                        isValid = passwordEncoder.matches(ADMIN_PASSWORD, currentHash);
                    } catch (Exception e) {
                        logger.warn("⚠️  密码验证时发生异常: {}", e.getMessage());
                        isValid = false;
                    }
                }
                logger.info("🔐 密码验证结果: {}", isValid ? "✓ 正确" : "✗ 不正确");
                
                // 无论当前哈希是否正确，都强制更新一次，确保密码正确
                logger.info("🔄 强制更新管理员密码以确保正确性...");
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
                    logger.info("✅ 管理员密码已更新，用户名: {}, 密码: {}", ADMIN_USERNAME, ADMIN_PASSWORD);
                    logger.info("🔑 新密码哈希: {}", newHash);
                    logger.info("📝 请使用以下凭据登录:");
                    logger.info("   用户名: {}", ADMIN_USERNAME);
                    logger.info("   密码: {}", ADMIN_PASSWORD);
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

