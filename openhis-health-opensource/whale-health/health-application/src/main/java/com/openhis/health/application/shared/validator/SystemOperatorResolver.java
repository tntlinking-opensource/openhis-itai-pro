/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.validator;

import org.springframework.stereotype.Component;

/**
 * 后台任务（定时任务、Quartz 线程等）无 HTTP 登录态时的系统业务操作人主键。
 *
 * <p>固定为种子数据 admin 医务人员（{@code adm_practitioner.id}，绑定 {@code whale_users.id=1}）。</p>
 */
@Component
public class SystemOperatorResolver {

    /** 种子数据 admin 医务人员主键（{@code adm_practitioner.id}，见 Flyway V23）。 */
    public static final long ADMIN_PRACTITIONER_ID = 308791351298756608L;

    /**
     * @return 系统业务操作人医务人员主键（{@code adm_practitioner.id}）
     */
    public Long getOperatorPractitionerId() {
        return ADMIN_PRACTITIONER_ID;
    }
}
