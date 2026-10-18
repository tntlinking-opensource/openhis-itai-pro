/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 病案首页省市区 codes 列（{@code birplc_codes} / {@code napl_codes} /
 * {@code curr_addr_codes} / {@code resd_addr_codes}）List↔斜杠拼接 TEXT。
 *
 * <p>落库唯一格式：{@code 220000/220100/220102}。</p>
 */
public final class HomepageRegionCodesTextUtil {

    /** codes 落库分隔符（与卫统/HQMS 出站拼码一致，避免 ASCII 逗号）。 */
    public static final String CODES_SEP = "/";

    private HomepageRegionCodesTextUtil() {
    }

    /**
     * 省市区 codes 列表序列化为 TEXT 列。
     *
     * @param codes 代码列表；null 时返回 null
     * @return 如 {@code 220000/220100}；空列表返回空串
     */
    public static String stringListToText(List<String> codes) {
        if (codes == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String raw : codes) {
            if (raw == null) {
                continue;
            }
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(CODES_SEP);
            }
            out.append(token);
        }
        return out.toString();
    }

    /**
     * TEXT 列（斜杠拼接）解析为 codes 列表。
     *
     * @param text 列存文本
     * @return 列表；空白时返回空列表
     */
    public static List<String> textToStringList(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        String[] parts = text.trim().split(CODES_SEP);
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String token = part.trim();
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return out;
    }
}
