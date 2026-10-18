/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.share;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * campus 模块操作人解析：封装 {@link ICurrentUser}，禁止 AppService 直注登录上下文。
 */
@Component
public class CampusOperatorResolver {

    private final ICurrentUser currentUser;

    public CampusOperatorResolver(@Autowired(required = false) ICurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * 要求当前请求已绑定登录用户，返回用户主键。
     */
    public Long requireOperatorId() {
        if (currentUser == null || !currentUser.isAuthenticated() || currentUser.getCurrentUserId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "请先登录后再操作校区组织");
        }
        return currentUser.getCurrentUserId();
    }

    /**
     * 解析当前操作人（未登录时返回 empty）。
     */
    public Optional<Long> resolveOperatorId() {
        if (currentUser == null || !currentUser.isAuthenticated() || currentUser.getCurrentUserId() == null) {
            return Optional.empty();
        }
        return Optional.of(currentUser.getCurrentUserId());
    }
}
