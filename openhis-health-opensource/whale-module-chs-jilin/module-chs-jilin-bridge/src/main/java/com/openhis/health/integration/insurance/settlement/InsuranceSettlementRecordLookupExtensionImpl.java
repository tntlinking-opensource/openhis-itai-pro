/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.settlement;

import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFundLookupExtensionOut;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * 吉林医保结算回执 lookup SPI 实现（日结 insutype、博思基金分项）。
 *
 * <p><b>SPI 接口</b>：{@link InsuranceSettlementRecordLookupExtension}</p>
 * <p><b>Bridge 委托</b>：{@link YbSettlementRecordQueryService}</p>
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

    @Override
    public Map<Long, BigDecimal> mapRetailUploadDiffBySettlementRecordIds(
        Collection<Long> settlementRecordIds) {
        if (settlementRecordIds == null || settlementRecordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        return settlementRecordQueryService.mapRetailUploadDiffByIds(settlementRecordIds, tenantId);
    }

    @Override
    public Map<Long, BigDecimal> mapRetailUploadDiffByPaymentIds(Collection<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        return settlementRecordQueryService.mapRetailUploadDiffByPaymentIds(paymentIds, tenantId);
    }

    @Override
    public Optional<InsuranceSettlementFundLookupExtensionOut> findFundByPaymentId(Long paymentId) {
        if (paymentId == null) {
            return Optional.empty();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Optional.empty();
        }
        return settlementRecordQueryService.findFirstConfirmedByPaymentId(paymentId, tenantId)
            .map(this::toFundOut);
    }

    private InsuranceSettlementFundLookupExtensionOut toFundOut(YbSettlementRecord row) {
        return new InsuranceSettlementFundLookupExtensionOut()
            .setSettlementRecordId(row.getId())
            .setPaymentId(row.getPaymentId())
            .setContractId(row.getContractId())
            .setAccountId(row.getAccountId())
            .setPsnNo(row.getPsnNo())
            .setStatus(row.getStatus())
            .setMedfeeSumamt(row.getMedfeeSumamt())
            .setFulamtOwnpayAmt(row.getFulamtOwnpayAmt())
            .setOverlmtSelfpay(row.getOverlmtSelfpay())
            .setPreselfpayAmt(row.getPreselfpayAmt())
            .setInscpScpAmt(row.getInscpScpAmt())
            .setActPayDedc(row.getActPayDedc())
            .setHifpPay(row.getHifpPay())
            .setPoolPropSelfpay(row.getPoolPropSelfpay())
            .setCvlservPay(row.getCvlservPay())
            .setHifesPay(row.getHifesPay())
            .setHifmiPay(row.getHifmiPay())
            .setHifobPay(row.getHifobPay())
            .setMafPay(row.getMafPay())
            .setHifdmPay(row.getHifdmPay())
            .setOthPay(row.getOthPay())
            .setFundPaySumamt(row.getFundPaySumamt())
            .setPsnPartAmt(row.getPsnPartAmt())
            .setAcctPay(row.getAcctPay())
            .setPsnCashPay(row.getPsnCashPay())
            .setHospPartAmt(row.getHospPartAmt());
    }
}
