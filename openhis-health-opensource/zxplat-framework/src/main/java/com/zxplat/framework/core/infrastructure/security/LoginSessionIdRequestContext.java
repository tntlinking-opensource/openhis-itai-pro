/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.security;

import com.zxplat.framework.core.infrastructure.web.RequestScopeCleaner;
import org.springframework.stereotype.Component;

/**
 * 当前请求的登录会话实例标识（JWT {@code loginSessionId}）。
 *
 * <p>同一用户多浏览器/多 Token 各自一份租户·医院·科室选择，禁止按 userId 互相覆盖。
 * 由 JWT 过滤器在拦截器之前写入；仅允许在请求结束清理，禁止在 {@code preHandle} 清空。</p>
 */
@Component
public class LoginSessionIdRequestContext implements RequestScopeCleaner {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    /**
     * 写入本请求登录会话实例 id。
     *
     * @param loginSessionId JWT 中的会话实例；空则视为未绑定
     */
    public static void set(String loginSessionId) {
        if (loginSessionId == null) {
            HOLDER.remove();
            return;
        }
        String trimmed = loginSessionId.trim();
        if (trimmed.isEmpty()) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(trimmed);
    }

    /**
     * 读取本请求登录会话实例 id。
     *
     * @return 非空会话 id；未绑定则 {@code null}
     */
    public static String get() {
        return HOLDER.get();
    }

    /**
     * 清除本请求登录会话实例 id。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 请求结束释放 ThreadLocal，防止 Tomcat 线程复用串到下一请求。
     */
    @Override
    public void clearRequestScope() {
        clear();
    }
}
