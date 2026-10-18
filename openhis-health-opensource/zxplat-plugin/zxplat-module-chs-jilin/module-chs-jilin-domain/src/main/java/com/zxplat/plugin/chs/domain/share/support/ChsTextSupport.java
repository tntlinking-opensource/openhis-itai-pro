/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.support;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

/** CHS 平台应答辅助（domain/share，gateway 可依赖） */
public final class ChsTextSupport {

    /** 工具类禁止实例化 */
    private ChsTextSupport() {
    }

    /**
     * 平台 err_msg 提取（接受任意带 {@code getErrMsg()} 的对象）。
     *
     * @param body           平台应答体
     * @param emptyFallback  无 err_msg 时的兜底文案
     * @return 提取到的错误信息
     */
    public static String platformErrMsg(Object body, String emptyFallback) {
        if (body == null) {
            return emptyFallback;
        }
        try {
            var method = body.getClass().getMethod("getErrMsg");
            Object msg = method.invoke(body);
            if (msg != null && isNotEmpty(String.valueOf(msg))) {
                return String.valueOf(msg).trim();
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return emptyFallback;
    }
}
