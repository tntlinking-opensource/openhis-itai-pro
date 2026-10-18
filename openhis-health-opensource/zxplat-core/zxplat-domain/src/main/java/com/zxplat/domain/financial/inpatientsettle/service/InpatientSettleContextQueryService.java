/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.inpatientsettle.service;

import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterContextQueryService;
import com.zxplat.domain.financial.inpatientsettle.queries.model.InpatientSettleContextQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.zxplat.domain.share.constants.InpatientSettleConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;

/** 住院结算 L1 batch hydrate：就诊上下文 + 登记账户 + charge/payment 汇总。 */
@Service
@RequiredArgsConstructor
public class InpatientSettleContextQueryService {

    private final EncounterContextQueryService encounterContextQueryService;
    private final AccountQueryService accountQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /**
     * 加载住院结算摘要所需的 L1 上下文（不含患者一致性 Gate 与就诊状态 Gate）。
     *
     * @param encounterId 住院就诊主键
     * @param patientId   患者主键（登记账户查询口径）
     * @return L1 上下文
     * @throws BusinessException 登记账户缺失时 {@link InpatientSettleConstants#EX_IP_STL_103}
     */
    public InpatientSettleContextQueryModel mustLoadSettleContext(Long encounterId, Long patientId) {
        InpatientEncounterContextQueryModel inpatientContext =
            encounterContextQueryService.mustGetInpatientContextByEncounterId(encounterId);
        Account account = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        BigDecimal pendingTotal =
            chargeItemQueryService.sumPendingSettlementTotalPriceByEncounterId(encounterId);
        BigDecimal settledTotal =
            chargeItemQueryService.sumSettledTotalPriceByEncounterId(encounterId);
        Set<Long> reversedIds = paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(
            Collections.singleton(encounterId));
        InpatientSettleContextQueryModel model = new InpatientSettleContextQueryModel();
        model.setInpatientContext(inpatientContext);
        model.setRegistrationAccount(account);
        model.setPendingSettlementTotal(pendingTotal);
        model.setSettledTotal(settledTotal);
        model.setReversedPendingResettle(reversedIds.contains(encounterId));
        return model;
    }
}
