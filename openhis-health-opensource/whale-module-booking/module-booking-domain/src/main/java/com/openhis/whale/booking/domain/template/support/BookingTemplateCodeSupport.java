/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.support;

import com.openhis.whale.booking.domain.template.constants.BookingTemplateCodeConstants;

import java.util.UUID;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 排班模板与放号策略编码生成与回落（科室内唯一，运维无需手填编码）。
 */
public final class BookingTemplateCodeSupport {

    /** 工具类禁止实例化。 */
    private BookingTemplateCodeSupport() {
    }

    /**
     * 解析待持久化的模板/策略编码。
     *
     * <p>显式传入则 trim 后使用；更新且未传则保留库中编码；新建且未传则按前缀自动生成。</p>
     *
     * @param incomingCode 请求或实体上的编码，可为空白
     * @param existingCode 更新场景下库中已有编码，可为 {@code null}
     * @param autoPrefix   自动生成时前缀，见 {@link BookingTemplateCodeConstants}
     * @return 非空白编码
     */
    public static String resolvePersistedCode(String incomingCode, String existingCode, String autoPrefix) {
        String trimmed = trimToNull(incomingCode);
        if (trimmed != null) {
            // 显式传入编码：以运维填写为准
            return trimmed;
        }
        trimmed = trimToNull(existingCode);
        if (trimmed != null) {
            // 更新未改编码：沿用原值，避免覆盖为自动码
            return trimmed;
        }
        // 新建且未传编码：生成科室内唯一内部码
        return generate(autoPrefix);
    }

    /**
     * 生成带前缀的唯一编码。
     *
     * @param prefix 如 {@link BookingTemplateCodeConstants#PREFIX_DAY_SCHEDULE}
     * @return 前缀 + 12 位十六进制
     */
    public static String generate(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return prefix + suffix;
    }
}
