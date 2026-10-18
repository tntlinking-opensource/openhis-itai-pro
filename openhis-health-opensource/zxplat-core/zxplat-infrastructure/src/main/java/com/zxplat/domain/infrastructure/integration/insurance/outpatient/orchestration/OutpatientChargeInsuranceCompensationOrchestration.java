/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.orchestration;

import com.zxplat.domain.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeCorrectSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 医保正式结算成功后的自动补偿（2208 → 失败则 2601）。
 *
 * <p>触发点：
 * <ul>
 *   <li>{@code OutpatientChargeAppService#collectInsuranceConfirmResults} 多批 2207 中途失败；</li>
 *   <li>{@code OutpatientChargeAppService#confirmOutpatientChargeSettlement} 全部 2207 成功后本地落账失败。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutpatientChargeInsuranceCompensationOrchestration {

    private final OutpatientChargeInsuranceExtension chargeInsuranceExtension;

    /**
     * 对指定支付单下已落库的医保正式结算记录逐条执行 2208 冲正。
     *
     * @param paymentId 门诊支付单主键；为 null 或扩展未启用时静默返回
     * @throws BusinessException 任一条 2208/2601 失败时抛出 INS-013
     */
    public void compensateSettlementsByPayment(Long paymentId) {
        if (!chargeInsuranceExtension.isEnabled() || paymentId == null) {
            return;
        }
        List<OutpatientChargeSettlementRecordExtensionOut> records =
            chargeInsuranceExtension.listSettlementsByPayment(paymentId);
        if (records.isEmpty()) {
            return;
        }
        for (OutpatientChargeSettlementRecordExtensionOut record : records) {
            if (record == null || record.getSettlementRecordId() == null) {
                continue;
            }
            OutpatientChargeRevokeSettleExtensionIn command = new OutpatientChargeRevokeSettleExtensionIn();
            command.setSettlementRecordId(record.getSettlementRecordId());
            command.setPaymentId(paymentId);
            command.setPreSettleId(record.getPreSettleId());
            command.setSettlementId(record.getSettlementId());
            try {
                chargeInsuranceExtension.revokeSettle(command);
            } catch (RuntimeException ex) {
                log.warn(
                    "2208 compensation failed paymentId={} settlementRecordId={}, trying 2601",
                    paymentId,
                    record.getSettlementRecordId(),
                    ex);
                if (StringUtils.isBlank(record.getMsgid2207())) {
                    throw compensationFailed(paymentId, record.getSettlementRecordId(), ex);
                }
                OutpatientChargeCorrectSettleExtensionIn correctCommand = new OutpatientChargeCorrectSettleExtensionIn();
                correctCommand.setSettlementRecordId(record.getSettlementRecordId());
                correctCommand.setOriginalInfno("2207");
                try {
                    chargeInsuranceExtension.correctSettleTransaction(correctCommand);
                } catch (RuntimeException ex2) {
                    log.error(
                        "2601 compensation failed paymentId={} settlementRecordId={}",
                        paymentId,
                        record.getSettlementRecordId(),
                        ex2);
                    throw compensationFailed(paymentId, record.getSettlementRecordId(), ex2);
                }
            }
        }
    }

    private static BusinessException compensationFailed(
            Long paymentId, Long settlementRecordId, RuntimeException cause) {
        return new BusinessException(
            "INS-013",
            "部分医保结算失败后自动冲正未完全成功，请勿重复收费，联系医保办处理");
    }
}
