/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.validation;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 验证工具类
 */
public class ValidationUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^1[3-9]\\d{9}$"
    );

    /**
     * 验证邮箱格式
     * @param email 邮箱地址
     * @return true表示格式正确
     */
    public static boolean isValidEmail(String email) {
        if (StringUtils.isEmpty(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 验证手机号格式（中国大陆）
     * @param phone 手机号
     * @return true表示格式正确
     */
    public static boolean isValidPhone(String phone) {
        if (StringUtils.isEmpty(phone)) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 验证邮箱格式，如果无效则抛出异常
     * @param email 邮箱地址
     * @param fieldName 字段名
     * @throws ValidationException 验证失败异常
     */
    public static void validateEmail(String email, String fieldName) {
        if (!isValidEmail(email)) {
            Map<String, String> errors = new HashMap<>();
            errors.put(fieldName, "邮箱格式不正确");
            throw new ValidationException("验证失败", errors);
        }
    }

    /**
     * 验证手机号格式，如果无效则抛出异常
     * @param phone 手机号
     * @param fieldName 字段名
     * @throws ValidationException 验证失败异常
     */
    public static void validatePhone(String phone, String fieldName) {
        if (!isValidPhone(phone)) {
            Map<String, String> errors = new HashMap<>();
            errors.put(fieldName, "手机号格式不正确");
            throw new ValidationException("验证失败", errors);
        }
    }

    /**
     * 验证字符串长度
     * @param str 字符串
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @param fieldName 字段名
     * @throws ValidationException 验证失败异常
     */
    public static void validateLength(String str, int minLength, int maxLength, String fieldName) {
        if (str == null) {
            Map<String, String> errors = new HashMap<>();
            errors.put(fieldName, "不能为空");
            throw new ValidationException("验证失败", errors);
        }

        int length = str.length();
        if (length < minLength || length > maxLength) {
            Map<String, String> errors = new HashMap<>();
            errors.put(fieldName, String.format("长度必须在%d-%d之间", minLength, maxLength));
            throw new ValidationException("验证失败", errors);
        }
    }
}
