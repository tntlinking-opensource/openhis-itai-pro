/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.surplusretention.support;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.framework.core.application.util.StringUtils;

/**
 * 参保地展示格式化：对齐门诊挂号历史列 {@code formatInsuplcAdmdvsDisplay}
 *（1901 字典名优先，否则吉林常用前缀回落）。
 */
final class InsuplcAdmdvsDisplaySupport {

    /** 吉林常用区划前缀 → 展示地名（长前缀优先）。 */
    private static final String[][] LEGACY_PREFIX_LABELS = {
        {"229900", "吉林省省本级"},
        {"220199", "长春市市本级"},
        {"220100", "长春市"},
        {"2299", "吉林省"},
        {"2201", "长春市"},
        {"22", "吉林省"},
    };

    private InsuplcAdmdvsDisplaySupport() {
    }

    /**
     * 组装参保地展示文案。
     *
     * @param admdvsCode 区划码；可空
     * @param dictLabel  1901 字典 label；可空
     * @return 展示文案；码与名皆空时为空串
     */
    static String formatDisplay(String admdvsCode, String dictLabel) {
        String label = StringUtils.trimToNull(dictLabel);
        String digits = normalizeDigits(admdvsCode);
        // 字典命中：名与码不同时拼「名（码）」，与挂号历史一致
        if (label != null) {
            if (digits != null && !digits.equals(label)) {
                return label + "（" + digits + "）";
            }
            return label;
        }
        return formatLegacy(admdvsCode, digits);
    }

    /**
     * 字典未命中时的吉林院点前缀回落。
     *
     * @param raw    原始区划串
     * @param digits 归一化数字码
     * @return 展示文案或空串
     */
    private static String formatLegacy(String raw, String digits) {
        String trimmed = StringUtils.trimToNull(raw);
        if (trimmed == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        if (digits == null) {
            return trimmed;
        }
        for (String[] item : LEGACY_PREFIX_LABELS) {
            String prefix = item[0];
            String name = item[1];
            if (!digits.startsWith(prefix)) {
                continue;
            }
            // 精确命中前缀：仅地名；更长码：地名（码）
            if (digits.equals(prefix)) {
                return name;
            }
            return name + "（" + digits + "）";
        }
        return digits;
    }

    /**
     * 区划串去非数字。
     *
     * @param admdvs 原始值
     * @return 数字码；无数字则 null
     */
    private static String normalizeDigits(String admdvs) {
        String trimmed = StringUtils.trimToNull(admdvs);
        if (trimmed == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }
}
