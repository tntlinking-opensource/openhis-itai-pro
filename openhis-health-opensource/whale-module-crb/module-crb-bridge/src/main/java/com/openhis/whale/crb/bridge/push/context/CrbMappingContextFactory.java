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

    public CrbMappingContext create(String operatorId) {
        return new CrbMappingContext()
                .setOrgCode(crbProperties.getFrontSoftware().getOrgCode())
                .setOrgName(crbProperties.getFrontSoftware().getOrgCode())
                .setTenantId(TenantUtils.getCurrentTenantId())
                .setOperatorId(operatorId);
    }
}
