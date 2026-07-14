/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.share.support;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 博思 H5 票面链接 query 规范化工具。
 *
 * <p>博思返回的 {@code pictureUrl} 中 {@code t=} 令牌可能含非 UTF-8 字节；
 * 浏览器直接打开会乱码，需对 query 中非 ASCII 字符做百分号编码。</p>
 */
public final class BossPictureUrlSupport {

    private BossPictureUrlSupport() {
    }

    /**
     * 规范化票面 H5 链接的 query 段（就地解析，不依赖 {@code java.net.URI}）。
     *
     * <p>无 query 或 query 全为 ASCII 可打印字符时原样返回；
     * {@code t=} 参数及含高位字节的值均做 ISO-8859-1 字节级百分号编码。</p>
     *
     * @param url 博思原始 pictureUrl
     * @return 规范化后的 URL；入参空白时原样返回
     */
    public static String normalizePictureUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || url.chars().noneMatch(BossPictureUrlSupport::needsQueryEncoding)) {
            return url;
        }
        String base = url.substring(0, queryStart + 1);
        String query = url.substring(queryStart + 1);
        StringBuilder out = new StringBuilder(base);
        int start = 0;
        while (start < query.length()) {
            int amp = query.indexOf('&', start);
            String pair = amp < 0 ? query.substring(start) : query.substring(start, amp);
            if (start > 0) {
                out.append('&');
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.append(pair);
            } else {
                String key = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                out.append(key).append('=');
                if ("t".equals(key) || value.chars().anyMatch(BossPictureUrlSupport::needsQueryEncoding)) {
                    out.append(percentEncodeQueryValue(value));
                } else {
                    out.append(value);
                }
            }
            if (amp < 0) {
                break;
            }
            start = amp + 1;
        }
        return out.toString();
    }

    /**
     * 判断 query 字符是否需百分号编码（非 ASCII 或控制字符）。
     *
     * @param ch Unicode 码点
     * @return 需要编码返回 true
     */
    private static boolean needsQueryEncoding(int ch) {
        return ch > 127 || ch < 32;
    }

    /**
     * 将 query 值按 ISO-8859-1 字节流做 RFC3986 未保留字符百分号编码。
     *
     * @param value 原始 query 值
     * @return 编码后的值
     */
    private static String percentEncodeQueryValue(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return sb.toString();
    }
}
