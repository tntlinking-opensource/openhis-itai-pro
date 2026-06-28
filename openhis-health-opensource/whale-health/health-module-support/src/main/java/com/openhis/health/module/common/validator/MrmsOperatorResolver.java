/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.common.validator;

import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * MRMS 会话门控：解析租户与操作人，禁止 AppService 直注 {@link ICurrentUser}。
 *
 * <p>病案域审计字段当前存 {@code whale_users.id}；与 {@link CurrentPractitionerResolver} 医务人员主键口径不同，
 * 本类仅服务 MRMS 既有契约。</p>
 */
@Component
public class MrmsOperatorResolver {

    private final ICurrentUser currentUser;

    /**
     * @param currentUser 当前登录用户（测试环境可为空）
     */
    public MrmsOperatorResolver(@Autowired(required = false) ICurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * 要求已登录并返回用户主键。
     *
     * @return 操作人主键
     * @throws BusinessException 未登录时
     */
    public Long requireOperatorId() {
        if (currentUser == null || !currentUser.isAuthenticated() || currentUser.getCurrentUserId() == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "请先登录后再操作病案");
        }
        return currentUser.getCurrentUserId();
    }

    /**
     * 解析当前租户主键。
     *
     * @return 租户主键
     * @throws BusinessException 租户上下文缺失时
     */
    public Long requireTenantId() {
        if (currentUser == null || currentUser.getSessionTenantId() == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "租户上下文缺失");
        }
        return currentUser.getSessionTenantId();
    }

    /**
     * 系统内部调用时使用（离院监听等），无登录则返回 null。
     *
     * @return 操作人主键或 null
     */
    public Long resolveOperatorIdOrNull() {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            return null;
        }
        return currentUser.getCurrentUserId();
    }
}
