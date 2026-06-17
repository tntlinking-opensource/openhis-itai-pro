/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.common.validator;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从 {@link ICurrentUser} 解析当前登录用户绑定的医务人员主键（{@code adm_practitioner.id}）。
 *
 * <p>对齐《代码生成通用规范》<strong>B.6.1</strong>：写路径执行人/制单人须用
 * {@link #requirePractitionerId(String, String)}；读路径可选过滤用 {@link #findPractitionerIdOrNull()}。</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentPractitionerResolver {

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
}
