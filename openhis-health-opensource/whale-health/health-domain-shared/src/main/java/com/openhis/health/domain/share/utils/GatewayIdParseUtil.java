/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.whale.core.application.exception.ValidationException;

import java.util.List;

/**
 * 网关/API 层字符串主键解析与非空校验。
 *
 * <p>前端常以字符串传 ID，批量操作又以字符串列表提交；在转入领域 {@code Long} 之前统一 trim、格式校验，
 * 避免非法格式进入领域或仓储。</p>
 *
 */
public final class GatewayIdParseUtil {

    private GatewayIdParseUtil() {
    }

    /**
     * trim 后解析为 long；空串或非法数字抛出 {@link ValidationException}。
     *
     * @param raw     原始字符串
     * @param message 失败提示（完整句式）
     * @return 主键
     */
    public static long parseRequiredLong(String raw, String message) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ValidationException(message);
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(message);
        }
    }

    /**
     * 批量字符串 ID：集合非空且每项均可解析为 long。
     *
     * @param ids     主键列表
     * @param message 集合为空或某项非法时的提示（前后端合一文案时使用）
     */
    public static void requireNonEmptyParsableLongStrings(List<String> ids, String message) {
        requireNonEmptyParsableLongStrings(ids, message, message);
    }

    /**
     * 批量字符串 ID：集合非空与单项可解析分为两类提示，便于区分「未传」与「格式非法」。
     *
     * @param ids               主键列表
     * @param emptyMessage      集合为 null 或空时的提示
     * @param invalidItemMessage 某项为空串或无法解析为 long 时的提示
     */
    public static void requireNonEmptyParsableLongStrings(
        List<String> ids,
        String emptyMessage,
        String invalidItemMessage) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(emptyMessage);
        }
        for (String id : ids) {
            parseRequiredLong(id, invalidItemMessage);
        }
    }

    /**
     * 批量 {@link Long} 主键非空断言。
     *
     * @param ids     主键列表
     * @param message 为空时的提示
     */
    public static void requireNonEmptyLongIds(List<Long> ids, String message) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(message);
        }
    }
}
