/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.domain.service;

import com.zxplat.framework.core.application.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 密码验证器
 */
@Component
public class PasswordValidator {

    // 密码最小长度
    private static final int MIN_LENGTH = 8;

    // 密码复杂度正则：必须包含大小写字母、数字、特殊字符
    private static final Pattern COMPLEXITY_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );

    /**
     * 验证密码
     * @param password 密码
     * @throws ValidationException 如果密码不符合要求
     */
    public void validate(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("密码不能为空");
        }

        if (password.length() < MIN_LENGTH) {
            throw new ValidationException(String.format("密码长度至少%d位", MIN_LENGTH));
        }

        if (!COMPLEXITY_PATTERN.matcher(password).matches()) {
            throw new ValidationException("密码必须包含大小写字母、数字和特殊字符");
        }
    }

    /**
     * 检查密码是否符合要求（不抛异常）
     * @param password 密码
     * @return true表示符合要求
     */
    public boolean isValid(String password) {
        try {
            validate(password);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }
}
