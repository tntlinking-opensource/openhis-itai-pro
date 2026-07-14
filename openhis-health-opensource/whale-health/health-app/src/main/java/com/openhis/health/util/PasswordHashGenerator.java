/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码哈希生成工具。
 *
 * <p>独立 main 入口，用于生成 BCrypt 哈希写入数据库种子脚本（如 V2__Seed_data.sql）。</p>
 */
public class PasswordHashGenerator {

    /**
     * 生成默认管理员密码 {@code Admin@123456} 的 BCrypt 哈希并打印验证结果。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String password = "Admin@123456";
        String hash = encoder.encode(password);
        System.out.println("密码: " + password);
        System.out.println("BCrypt哈希: " + hash);

        // Asm：打印 BCrypt 哈希供种子脚本引用，并校验 encode/matches 往返一致
        boolean matches = encoder.matches(password, hash);
        String verifyResult;
        if (matches) {
            verifyResult = "✓ 匹配";
        } else {
            verifyResult = "✗ 不匹配";
        }
        System.out.println("验证结果: " + verifyResult);
    }
}
