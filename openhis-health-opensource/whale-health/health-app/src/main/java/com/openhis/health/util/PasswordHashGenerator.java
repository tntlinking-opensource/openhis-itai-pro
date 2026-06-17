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
        
        // 验证
        boolean matches = encoder.matches(password, hash);
        System.out.println("验证结果: " + (matches ? "✓ 匹配" : "✗ 不匹配"));
    }
}

