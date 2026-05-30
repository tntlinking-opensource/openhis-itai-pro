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

package com.openhis.health.application.common.converter;

import com.openhis.whale.core.application.exception.ValidationException;

/**
 * 文本值转换器：统一处理应用层常见的空白归一与安全数值解析（《代码生成通用规范》<strong>B.6.1</strong>
 * {@code common.converter.*}；跨 ≥2 个 AppService/MapperImpl 的 Long 解析不得再私有复制）。
 */
public final class TextValueConverter {

    private TextValueConverter() {
    }

    /**
     * 空白串归一为 {@code null}，便于区分「未填」与「空串」。
     *
     * @param raw 原始文本
     * @return trim 后非空文本；否则 {@code null}
     */
    public static String nullIfBlank(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * 安全解析字符串为 {@link Long}；非法格式返回 {@code null}。
     *
     * @param raw 原始文本
     * @return 解析成功返回 Long，否则 {@code null}
     */
    public static Long toNullableLong(String raw) {
        String text = nullIfBlank(raw);
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 安全解析字符串为 {@link Integer}；非法格式返回 {@code null}。
     *
     * @param raw 原始文本
     * @return 解析成功返回 Integer，否则 {@code null}
     */
    public static Integer toNullableInteger(String raw) {
        String text = nullIfBlank(raw);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将十进制整数字符串解析为 {@link Long}；空白或格式非法时抛出同一提示的 {@link ValidationException}。
     *
     * @param raw           原始文本，可为 {@code null} 或仅空白
     * @param errorMessage  空白或解析失败时共用提示
     * @return 解析成功的主键
     * @throws ValidationException 不可解析时
     */
    public static Long requireLong(String raw, String errorMessage) {
        String text = nullIfBlank(raw);
        if (text == null) {
            throw new ValidationException(errorMessage);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new ValidationException(errorMessage);
        }
    }

    /**
     * 将十进制整数字符串解析为 {@link Long}；空白与格式非法可使用不同提示（例如网关 id 与 Mapper 目标 id）。
     *
     * @param raw                   原始文本
     * @param blankMessage          空白或未填时的提示
     * @param invalidFormatMessage  非空白但非合法 Long 时的提示
     * @return 解析成功的主键
     * @throws ValidationException 不可解析时
     */
    public static Long requireLong(String raw, String blankMessage, String invalidFormatMessage) {
        String text = nullIfBlank(raw);
        if (text == null) {
            throw new ValidationException(blankMessage);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new ValidationException(invalidFormatMessage);
        }
    }
}
