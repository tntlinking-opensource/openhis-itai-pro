/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.identity;

import com.zxplat.framework.identity.domain.User;
import com.zxplat.framework.identity.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台 admin 账号判定（用户名 {@code admin}）。
 *
 * <p>放在 {@code infrastructure.identity}（非 {@code platform}）：platform 层 ArchUnit 禁止直连
 * zxplat-framework identity Repository；且本组件不经 {@code UserAppService}，避免与 {@code CurrentUser}
 * → {@code IPractitionerManagedScopeResolver} 形成 Spring 依赖环。</p>
 *
 * <p>按 userId 缓存判定结果：登录选科室/管辖解析同请求内会多次判定，避免重复打 {@code zxplat_users}。</p>
 */
@Component
@RequiredArgsConstructor
public class PlatformAdminAccountChecker {

    private final UserRepository userRepository;

    /** userId → 是否 admin；进程内缓存，账号改名场景极少，可接受短暂不一致。 */
    private final ConcurrentHashMap<Long, Boolean> adminFlagByUserId = new ConcurrentHashMap<>();

    /**
     * 判定是否为平台 admin 登录账号（用户名 {@code admin}，大小写不敏感）。
     *
     * @param userId 用户主键；{@code null} 时返回 {@code false}
     * @return {@code true} 表示平台 admin 账号
     */
    public boolean isAdminAccount(Long userId) {
        if (userId == null) {
            return false;
        }
        Boolean cached = this.adminFlagByUserId.get(userId);
        if (cached != null) {
            return cached.booleanValue();
        }
        User user = userRepository.findById(userId).orElse(null);
        boolean admin = user != null
            && user.getUserName() != null
            && "admin".equalsIgnoreCase(user.getUserName().trim());
        this.adminFlagByUserId.put(userId, Boolean.valueOf(admin));
        return admin;
    }
}
