/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.settlement;

import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 吉林医保结算回执 lookup SPI 实现（日结 insutype）。
 *
 * <p><b>SPI 接口</b>：{@link InsuranceSettlementRecordLookupExtension}</p>
 * <p><b>Bridge 委托</b>：{@link YbSettlementRecordQueryService#mapInsutypeByIds}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(YbSettlementRecordQueryService.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceSettlementRecordLookupExtensionImpl implements InsuranceSettlementRecordLookupExtension {

    private final YbSettlementRecordQueryService settlementRecordQueryService;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Map<Long, String> mapInsutypeBySettlementRecordIds(Collection<Long> settlementRecordIds) {
        if (settlementRecordIds == null || settlementRecordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        return settlementRecordQueryService.mapInsutypeByIds(settlementRecordIds, tenantId);
    }

    @Override
    public Map<Long, String> mapInsutypeByPaymentIds(Collection<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        return settlementRecordQueryService.mapInsutypeByPaymentIds(paymentIds, tenantId);
    }
}
