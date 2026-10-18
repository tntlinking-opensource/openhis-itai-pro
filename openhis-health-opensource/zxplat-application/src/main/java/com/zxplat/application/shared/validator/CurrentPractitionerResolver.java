/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.validator;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从 {@link ICurrentUser} 解析当前登录用户绑定的医务人员主键（{@code adm_practitioner.id}）。
 *
 * <p>对齐《代码生成通用规范》<strong>B.6.1 / B.6.5</strong>：HIS 业务写路径的操作人、执行人、收费人、
 * 发药人等须存 {@code adm_practitioner.id}，<strong>禁止</strong>使用 {@code zxplat_users.id}。
 * 写路径用 {@link #requirePractitionerId(String, String)} 或 {@link #requireOperatorId(String, String)}；
 * 读路径可选过滤用 {@link #findPractitionerIdOrNull()} 或 {@link #findOperatorIdOrNull()}。</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentPractitionerResolver {

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
     * 要求当前请求已绑定医务人员，返回 {@code adm_practitioner.id}（与 {@link #requirePractitionerId} 同义）。
     *
     * <p>供从框架 {@code CurrentOperatorResolver}（用户主键）迁移的业务写路径使用。</p>
     *
     * @param errorCode 业务错误码
     * @param message   未绑定时的中文提示
     * @return 非 {@code null} 的医务人员主键
     * @throws BusinessException 未绑定医务人员时
     */
    public Long requireOperatorId(String errorCode, String message) {
        return requirePractitionerId(errorCode, message);
    }

    /**
     * 返回当前登录用户绑定的医务人员主键；未绑定或未登录时为 {@code null}（与 {@link #findPractitionerIdOrNull} 同义）。
     *
     * @return 医务人员主键，或 {@code null}
     */
    public Long findOperatorIdOrNull() {
        return findPractitionerIdOrNull();
    }
}
