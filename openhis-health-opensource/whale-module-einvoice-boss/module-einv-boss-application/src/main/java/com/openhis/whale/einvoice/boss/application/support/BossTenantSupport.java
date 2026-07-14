/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.support;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;

/**
 * 博思模块租户上下文校验。
 */
public final class BossTenantSupport {

    private BossTenantSupport() {
    }

    /**
     * 要求当前请求已绑定租户，禁止静默回退默认院点。
     *
     * @return 当前租户 ID
     */
    public static Long requireCurrentTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException(
                    EinvBossErrorCodes.EINV_TENANT_REQUIRED,
                    "当前请求未携带租户上下文，请重新登录或选择院点");
        }
        return tenantId;
    }
}
