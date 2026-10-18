/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.share;

import org.springframework.stereotype.Component;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;

import lombok.RequiredArgsConstructor;

/**
 * PEIS 模块执业者解析：封装 {@link ICurrentUser}，禁止 AppService 直注登录上下文（B.6.5 / D1）。
 */
@Component
@RequiredArgsConstructor
public class PeisCurrentPractitionerResolver {

    /** 当前登录用户与会话上下文（含绑定的 {@code adm_practitioner.id}）。 */
    private final ICurrentUser currentUser;

    /**
     * 要求当前请求已绑定医务人员，返回 {@code adm_practitioner.id}。
     *
     * @param errorCode 业务错误码
     * @param message   未绑定时的中文提示
     * @return 非 {@code null} 的医务人员主键
     * @throws BusinessException 未绑定医务人员时
     */
    public Long requirePractitionerId(String errorCode, String message) {
        Long practitionerId = findPractitionerIdOrNull();
        if (practitionerId == null) {
            throw new BusinessException(errorCode, message);
        }
        return practitionerId;
    }

    /**
     * 返回当前登录用户绑定的医务人员主键；未绑定或未登录时为 {@code null}。
     *
     * @return 医务人员主键，或 {@code null}
     */
    public Long findPractitionerIdOrNull() {
        if (!currentUser.isAuthenticated()) {
            return null;
        }
        return currentUser.getCurrentPractitionerId();
    }

    /**
     * 要求当前请求已绑定医务人员（与 {@link #requirePractitionerId} 同义）。
     *
     * @param errorCode 业务错误码
     * @param message   未绑定时的中文提示
     * @return 非 {@code null} 的医务人员主键
     */
    public Long requireOperatorId(String errorCode, String message) {
        return requirePractitionerId(errorCode, message);
    }

    /**
     * 返回当前登录用户绑定的医务人员主键；未绑定或未登录时为 {@code null}。
     *
     * @return 医务人员主键，或 {@code null}
     */
    public Long findOperatorIdOrNull() {
        return findPractitionerIdOrNull();
    }
}
