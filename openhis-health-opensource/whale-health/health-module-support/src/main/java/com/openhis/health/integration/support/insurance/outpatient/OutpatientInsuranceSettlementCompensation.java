/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.insurance.outpatient;

import com.openhis.health.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeCorrectSettleCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeRevokeSettleCommand;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeSettlementRecordResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 门诊混合结算「部分 2207 成功」后的自动补偿（2208 → 失败则 2601）。
 *
 * <p>触发点：{@code OutpatientChargeAppService#collectInsuranceConfirmResults} 任一组 confirm 抛异常时，
 * 对已成功的组调用本类，避免平台残留 ACTIVE 正式结算。</p>
 *
 * <p>调用链：本类 → {@link OutpatientChargeInsuranceExtension} → chs-jilin Port → 2208/2601。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutpatientInsuranceSettlementCompensation {

    private final OutpatientChargeInsuranceExtension chargeInsuranceExtension;

    /**
     * 对指定支付单下已落库的医保正式结算记录逐条执行 2208 冲正。
     *
     * @param paymentId 门诊支付单主键；为 null 或扩展未启用时静默返回
     * @throws BusinessException 任一条 2208/2601 失败时抛出 INS-013
     */
    public void compensateSettlementsByPayment(Long paymentId) {
        // Gate：未装配医保或 paymentId 缺失则无需补偿
        if (!chargeInsuranceExtension.isEnabled() || paymentId == null) {
            return;
        }
        // Pre：查 payment 下全部未撤销回执（混合结算可能多条）
        List<OutpatientChargeSettlementRecordResult> records =
            chargeInsuranceExtension.listSettlementsByPayment(paymentId);
        if (records.isEmpty()) {
            return;
        }
        // Cmd：逐条冲正已成功落库的正式结算，避免平台残留 ACTIVE 记录
        for (OutpatientChargeSettlementRecordResult record : records) {
            if (record == null || record.getSettlementRecordId() == null) {
                continue;
            }
            OutpatientChargeRevokeSettleCommand command = new OutpatientChargeRevokeSettleCommand();
            command.setSettlementRecordId(record.getSettlementRecordId());
            command.setPaymentId(paymentId);
            command.setPreSettleId(record.getPreSettleId());
            command.setSettlementId(record.getSettlementId());
            try {
                // Cmd：优先 2208 撤销正式结算
                chargeInsuranceExtension.revokeSettle(command);
            } catch (RuntimeException ex) {
                log.warn(
                    "2208 compensation failed paymentId={} settlementRecordId={}, trying 2601",
                    paymentId,
                    record.getSettlementRecordId(),
                    ex);
                // Gate：无 2207 msgid 则无法 2601 冲正，直接失败阻断重复收费
                if (StringUtils.isBlank(record.getMsgid2207())) {
                    throw compensationFailed(paymentId, record.getSettlementRecordId(), ex);
                }
                OutpatientChargeCorrectSettleCommand correctCommand = new OutpatientChargeCorrectSettleCommand();
                correctCommand.setSettlementRecordId(record.getSettlementRecordId());
                correctCommand.setOriginalInfno("2207");
                try {
                    // Cmd：2208 失败降级 2601 冲正原 2207 msgid
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

    private static BusinessException compensationFailed(Long paymentId, Long settlementRecordId, RuntimeException cause) {
        return new BusinessException(
            "INS-013",
            "部分医保结算失败后自动冲正未完全成功，请勿重复收费，联系医保办处理");
    }
}
