/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.support;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * MRMS 操作人解析，禁止 AppService 直注 {@link ICurrentUser}。
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
