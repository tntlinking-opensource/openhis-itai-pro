/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.context;

import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 构造 Mapper 运行时上下文。
 */
@Component
@RequiredArgsConstructor
public class CrbMappingContextFactory {

    private final CrbProperties crbProperties;

    /**
     * 按当前线程租户构造上下文（事件同步路径）。
     *
     * @param operatorId 操作人
     * @return 映射上下文
     */
    public CrbMappingContext create(String operatorId) {
        return create(operatorId, TenantUtils.getCurrentTenantId());
    }

    /**
     * 按显式租户构造上下文（推送 Worker 异步路径须传入 task.tenantId）。
     *
     * @param operatorId 操作人
     * @param tenantId 租户 id
     * @return 映射上下文
     */
    public CrbMappingContext create(String operatorId, Long tenantId) {
        Long resolvedTenantId = tenantId;
        if (resolvedTenantId == null) {
            resolvedTenantId = TenantUtils.getCurrentTenantId();
        }
        if (resolvedTenantId == null) {
            resolvedTenantId = crbProperties.getLab().getTenantId();
        }
        return new CrbMappingContext()
                .setOrgCode(crbProperties.getFrontSoftware().getOrgCode())
                .setOrgName(crbProperties.getFrontSoftware().getOrgName())
                .setTenantId(resolvedTenantId)
                .setOperatorId(operatorId);
    }
}
