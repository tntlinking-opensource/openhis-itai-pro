/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.common.validator;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 应用层跨场景登录门控：从 {@link ICurrentUser} 解析当前操作人主键，符合《代码生成通用规范》
 * <strong>B.6.1</strong>（{@code application.common} 下 {@code validator.*}，前置条件校验类组件；禁止在各 AppService 复制私有 {@code requireOperatorId}）。
 *
 * <p>仅依赖基础设施登录上下文，不注入领域服务（对齐 B.6.1 领域调用红线）。错误码与文案由调用方按 AS 契约传入。</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentOperatorResolver {

    /** 当前 HTTP 安全上下文中的登录用户（与用户主键解析同源）。 */
    private final ICurrentUser currentUser;

    /**
     * 要求当前请求已绑定登录用户，返回用户主键。
     *
     * @param errorCode 业务错误码（与 {@link BusinessException#getErrorCode()} 一致）
     * @param message   未登录时透出给调用方的中文提示
     * @return 非 {@code null} 的用户主键
     * @throws BusinessException 上下文无用户 id 时
     */
    public Long requireOperatorId(String errorCode, String message) {
        Long uid = currentUser.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException(errorCode, message);
        }
        return uid;
    }

    /**
     * 返回当前登录用户主键；未登录时为 {@code null}。
     *
     * <p>供读路径可选过滤（如「仅本人」未勾选时不强制登录）；写路径须使用 {@link #requireOperatorId}。</p>
     *
     * @return 用户主键，或 {@code null}
     */
    public Long findOperatorIdOrNull() {
        return currentUser.getCurrentUserId();
    }
}
