/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.session;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 框架应用层操作人解析（B.6.5）：{@code *AppService} 禁止直注 {@link ICurrentUser}，
 * 写路径须经本组件 {@link #requireOperatorId} 取得 {@code zxplat_users.id}。
 */
@Component
@RequiredArgsConstructor
public class CurrentOperatorResolver {

    private final ICurrentUser currentUser;

    /**
     * 要求当前请求已绑定登录用户，返回用户主键。
     *
     * @param errorCode 业务错误码
     * @param message   未登录时透出给调用方的中文提示
     * @return 非 {@code null} 的用户主键
     * @throws BusinessException 上下文无用户 id 时
     */
    public Long requireOperatorId(String errorCode, String message) {
        Long uid = currentUser.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException(errorCode, message);
        }
        return uid;
    }

    /**
     * 返回当前登录用户主键；未登录时为 {@code null}。
     *
     * <p>供读路径可选过滤或软删审计；写路径须使用 {@link #requireOperatorId}。</p>
     *
     * @return 用户主键，或 {@code null}
     */
    public Long findOperatorIdOrNull() {
        return currentUser.getCurrentUserId();
    }

    /**
     * 返回当前登录用户显示名；未登录时为 {@code null}。
     *
     * @return 用户名，或 {@code null}
     */
    public String findOperatorUserNameOrNull() {
        if (!currentUser.isAuthenticated()) {
            return null;
        }
        return currentUser.getCurrentUserName();
    }

    /**
     * 当前请求是否已认证。
     *
     * @return {@code true} 表示已登录
     */
    public boolean isAuthenticated() {
        return currentUser.isAuthenticated();
    }
}
