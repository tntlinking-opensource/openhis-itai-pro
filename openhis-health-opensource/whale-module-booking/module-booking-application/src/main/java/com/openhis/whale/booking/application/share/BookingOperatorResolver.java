/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.share;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * booking 模块操作人解析：封装 {@link ICurrentUser}，禁止 AppService 直注登录上下文（B.6.5）。
 */
@Component
public class BookingOperatorResolver {

    private final ICurrentUser currentUser;

    public BookingOperatorResolver(@Autowired(required = false) ICurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * 要求当前请求已绑定登录用户，返回用户主键。
     *
     * @return 非空用户主键
     * @throws BusinessException 未登录时
     */
    public Long requireOperatorId() {
        if (currentUser == null || !currentUser.isAuthenticated() || currentUser.getCurrentUserId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "请先登录后再操作排班");
        }
        return currentUser.getCurrentUserId();
    }

    /**
     * 解析当前操作人（未登录时返回 empty）。
     */
    public java.util.Optional<Long> resolveOperatorId() {
        if (currentUser == null || !currentUser.isAuthenticated() || currentUser.getCurrentUserId() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(currentUser.getCurrentUserId());
    }
}
