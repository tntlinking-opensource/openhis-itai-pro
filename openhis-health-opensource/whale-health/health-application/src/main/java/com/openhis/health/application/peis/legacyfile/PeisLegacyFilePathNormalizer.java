/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.legacyfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 老 PEIS 文件路径归一：绝对/相对路径 → 相对 storage key（正斜杠、无盘符、无前导 /）。
 */
public final class PeisLegacyFilePathNormalizer {

    private static final String[] DEFAULT_PREFIXES = {
            "d:/0.product/ftpuser/images/",
            "d:/home/ftpuser/images/",
            "/home/ftpuser/images/",
            "/d:/home/ftpuser/images/",
            "/d:/0.product/ftpuser/images/",
    };

    private PeisLegacyFilePathNormalizer() {
    }

    /**
     * 归一为相对 storage key；空入参返回空串。
     *
     * @param raw 库内原路径
     * @param extraPrefixes 额外可剥离前缀（如配置的 root / alias-roots），可空
     */
    public static String normalize(String raw, List<String> extraPrefixes) {
        if (raw == null) {
            return "";
        }
        String p = raw.trim().replace('\\', '/');
        if (p.isEmpty()) {
            return "";
        }
        // 去掉开头多余的 "/D:" 形态
        if (p.startsWith("/") && p.length() > 2 && Character.isLetter(p.charAt(1)) && p.charAt(2) == ':') {
            p = p.substring(1);
        }
        String lower = p.toLowerCase(Locale.ROOT);
        for (String prefix : buildPrefixes(extraPrefixes)) {
            String pref = prefix.toLowerCase(Locale.ROOT).replace('\\', '/');
            if (!pref.endsWith("/")) {
                pref = pref + "/";
            }
            if (lower.startsWith(pref)) {
                p = p.substring(pref.length());
                lower = p.toLowerCase(Locale.ROOT);
                break;
            }
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    public static String normalize(String raw) {
        return normalize(raw, null);
    }

    private static List<String> buildPrefixes(List<String> extra) {
        List<String> list = new ArrayList<>();
        if (extra != null) {
            for (String e : extra) {
                if (e != null && !e.isBlank()) {
                    list.add(e.trim());
                }
            }
        }
        for (String d : DEFAULT_PREFIXES) {
            list.add(d);
        }
        return list;
    }
}
