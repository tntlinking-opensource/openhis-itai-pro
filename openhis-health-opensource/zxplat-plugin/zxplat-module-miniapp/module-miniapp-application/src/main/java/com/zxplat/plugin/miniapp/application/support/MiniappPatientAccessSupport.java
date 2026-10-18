/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.support;

import com.zxplat.plugin.miniapp.application.config.MiniappProperties;
import com.zxplat.plugin.miniapp.domain.context.MiniappContext;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 解析小程序写路径操作人与就诊人。
 */
@Component
@RequiredArgsConstructor
public class MiniappPatientAccessSupport {

    private final MiniappProperties miniappProperties;
    private final ObjectProvider<ICurrentUser> currentUserProvider;

    /**
     * 解析就诊人：优先 {@link MiniappContext}，否则用请求体 patientId（仅未注入会话时回落）。
     *
     * <p>C1：预约/缴费/团检选定须有 patientId。</p>
     */
    public Long requirePatientId(Long requestPatientId) {
        MiniappContext ctx = MiniappContext.get();
        if (ctx != null && ctx.getPatientId() != null) {
            if (requestPatientId != null && !requestPatientId.equals(ctx.getPatientId())) {
                throw new ValidationException("就诊人与当前会话不一致");
            }
            return ctx.getPatientId();
        }
        if (ctx != null && ctx.getAccountId() != null && requestPatientId == null) {
            throw new BusinessException("BIND_REQUIRED", "请先绑定就诊人");
        }
        if (requestPatientId == null) {
            throw new ValidationException("请先绑定就诊人");
        }
        return requestPatientId;
    }

    /**
     * 解析操作人：会话 practitioner → 配置的系统操作人。
     */
    public Long requireOperatorId() {
        MiniappContext ctx = MiniappContext.get();
        if (ctx != null && ctx.getPractitionerId() != null) {
            return ctx.getPractitionerId();
        }
        ICurrentUser currentUser = currentUserProvider.getIfAvailable();
        if (currentUser != null && currentUser.isAuthenticated()
            && currentUser.getCurrentPractitionerId() != null) {
            return currentUser.getCurrentPractitionerId();
        }
        if (miniappProperties.getSystemOperatorPractitionerId() != null) {
            return miniappProperties.getSystemOperatorPractitionerId();
        }
        throw new ValidationException(
            "小程序写路径失败：请配置 zxplat.miniapp.system-operator-practitioner-id");
    }
}
