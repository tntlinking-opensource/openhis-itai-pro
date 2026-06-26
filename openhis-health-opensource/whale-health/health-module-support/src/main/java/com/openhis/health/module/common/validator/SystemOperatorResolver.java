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

package com.openhis.health.module.common.validator;

import org.springframework.stereotype.Component;

/**
 * 后台任务（定时任务、Quartz 线程等）无 HTTP 登录态时的系统业务操作人主键。
 *
 * <p>固定为种子数据 admin 医务人员（{@code adm_practitioner.id}，绑定 {@code whale_users.id=1}）。</p>
 */
@Component
public class SystemOperatorResolver {

    /** 种子数据 admin 用户主键（{@code whale_users.id}），仅用于平台用户域桥接。 */
    public static final long ADMIN_USER_ID = 1L;

    /**
     * 种子数据 admin 医务人员主键（{@code adm_practitioner.id}，见 Flyway V23）。
     */
    public static final long ADMIN_PRACTITIONER_ID = 308791351298756608L;

    /**
     * @return 系统业务操作人医务人员主键（{@code adm_practitioner.id}）
     */
    public Long getOperatorPractitionerId() {
        return ADMIN_PRACTITIONER_ID;
    }

    /**
     * @deprecated 业务审计须用 {@link #getOperatorPractitionerId()}；本方法仅保留平台用户域兼容。
     */
    @Deprecated
    public Long getOperatorUserId() {
        return ADMIN_USER_ID;
    }
}
