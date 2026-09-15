/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.support;

import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.mrms.caserecord.resolver.HomepageSelfpayAmtResolver;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFundLookupExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * domain {@link HomepageSelfpayAmtResolver} 适配：住院出院结算 + 医保回执读侧。
 */
@Component
@RequiredArgsConstructor
public class MrmsHomepageSelfpayAmtResolverAdapter implements HomepageSelfpayAmtResolver {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    private final InsuranceSettlementRecordLookupExtension insuranceSettlementRecordLookupExtension;

    /**
     * 解析病案首页自付：优先 Σ 已确认医保 {@code psn_part_amt}，否则纯自费取医疗费总额。
     *
     * @param encounterId  就诊主键
     * @param medfeeSumamt 费用占用合计（charge item 汇总）
     * @return 4401 selfpay_amt；无结算且无总额时 null
     */
    @Override
    public BigDecimal resolveByEncounterId(Long encounterId, BigDecimal medfeeSumamt) {
        if (encounterId == null) {
            return null;
        }
        List<PaymentReconciliationQueryModel> payments =
                paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        List<Long> ybPaymentIds = new ArrayList<>();
        boolean hasInpatientClinicSuccess = false;
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            if (!PaymentKind.INPATIENT_CLINIC.equals(payment.getKindEnum())) {
                continue;
            }
            if (!PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
                continue;
            }
            // 逆向反结单不参与自付汇总
            if (payment.getRelationId() != null) {
                continue;
            }
            hasInpatientClinicSuccess = true;
            if (PaymentYbSettlementSupport.hasConfirmed(payment.getYbSettlements())) {
                ybPaymentIds.add(payment.getId());
            }
        }
        if (!ybPaymentIds.isEmpty() && insuranceSettlementRecordLookupExtension.isEnabled()) {
            BigDecimal fromYb = this.sumPsnPartAmtByPayments(ybPaymentIds);
            if (fromYb != null) {
                return MonetaryAmountSupport.scaleCalc(fromYb);
            }
            // 已确认医保但回执分项暂不可读：不回落 medfee，避免把统筹段算进自付
            return null;
        }
        // 有医保桥但 SPI 未启用：同样不回落 medfee
        if (!ybPaymentIds.isEmpty()) {
            return null;
        }
        // 纯自费出院结算：个人负担=医疗费总额
        if (hasInpatientClinicSuccess) {
            if (medfeeSumamt != null && medfeeSumamt.compareTo(BigDecimal.ZERO) > 0) {
                return MonetaryAmountSupport.scaleCalc(medfeeSumamt);
            }
            Map<Long, BigDecimal> netByEncounter =
                    paymentReconciliationQueryService.mapInpatientClinicSettledNetAmountByEncounterIds(
                            List.of(encounterId));
            BigDecimal net = netByEncounter.get(encounterId);
            if (net != null) {
                return net;
            }
        }
        return null;
    }

    /**
     * 按支付单累加已确认医保回执个人负担；无 psn_part 时回落 medfee − fund。
     *
     * @param paymentIds 含已确认 YB 桥表的出院结算 paymentId
     * @return 累加额；全无有效分项时 null
     */
    private BigDecimal sumPsnPartAmtByPayments(List<Long> paymentIds) {
        BigDecimal total = BigDecimal.ZERO;
        boolean any = false;
        for (Long paymentId : paymentIds) {
            if (paymentId == null) {
                continue;
            }
            Optional<InsuranceSettlementFundLookupExtensionOut> fundOpt =
                    insuranceSettlementRecordLookupExtension.findFundByPaymentId(paymentId);
            if (fundOpt.isEmpty()) {
                continue;
            }
            InsuranceSettlementFundLookupExtensionOut fund = fundOpt.get();
            BigDecimal part = fund.getPsnPartAmt();
            if (part == null && fund.getMedfeeSumamt() != null && fund.getFundPaySumamt() != null) {
                part = fund.getMedfeeSumamt().subtract(fund.getFundPaySumamt());
            }
            if (part == null) {
                continue;
            }
            any = true;
            total = total.add(part);
        }
        if (!any) {
            return null;
        }
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return total;
    }
}
