/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.config.share;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * imaging 模块操作人解析：封装 {@link ICurrentUser}，禁止 AppService 直注登录上下文（B.6.5）。
 *
 * <p>写路径操作人取 {@code adm_practitioner.id}（{@link ICurrentUser#getCurrentPractitionerId}），
 * 禁止依赖 health-application。</p>
 */
@Component
public class ImagingOperatorResolver {

    private final ICurrentUser currentUser;

    public ImagingOperatorResolver(@Autowired(required = false) ICurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * 要求当前请求已绑定医务人员，返回医务人员主键。
     *
     * @return 非空医务人员主键
     * @throws BusinessException 未登录或未绑定医务人员时
     */
    public Long requireOperatorId() {
        if (currentUser == null || !currentUser.isAuthenticated()
            || currentUser.getCurrentPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, "请先登录后再操作阅片地址配置");
        }
        return currentUser.getCurrentPractitionerId();
    }

    /**
     * 解析当前操作人（未登录或未绑定时返回 empty）。
     */
    public java.util.Optional<Long> resolveOperatorId() {
        if (currentUser == null || !currentUser.isAuthenticated()
            || currentUser.getCurrentPractitionerId() == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(currentUser.getCurrentPractitionerId());
    }
}
