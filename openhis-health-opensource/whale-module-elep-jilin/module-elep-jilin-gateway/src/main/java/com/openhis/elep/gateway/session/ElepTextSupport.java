/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.gateway.session;

import org.apache.commons.lang3.StringUtils;

/** ELEP 模块字符串工具（对齐 ybapp StringUtil 子集）。 */
public final class ElepTextSupport {

    private ElepTextSupport() {
    }

    public static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return StringUtils.isBlank(text);
        }
        return false;
    }

    public static boolean isNotEmpty(Object value) {
        return !isEmpty(value);
    }
}
