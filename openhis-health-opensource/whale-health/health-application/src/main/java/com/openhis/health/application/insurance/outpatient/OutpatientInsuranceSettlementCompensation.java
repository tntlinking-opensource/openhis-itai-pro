/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.outpatient;

import com.openhis.whale.chs.jilin.port.OutpatientInsuranceSettlementPort;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceSettleRecordResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 门诊医保平台结算部分成功后的 2208 自动冲正。
 */
@Slf4j
@Component
@ConditionalOnBean(OutpatientInsuranceSettlementPort.class)
@RequiredArgsConstructor
public class OutpatientInsuranceSettlementCompensation {

    @Autowired(required = false)
    private OutpatientInsuranceSettlementPort outpatientInsuranceSettlementPort;

    /**
     * 对指定支付单下已落库的医保正式结算记录逐条执行 2208 冲正。
     *
     * @param paymentId 门诊支付单主键；为 null 或 Port 未启用时静默返回
     * @throws BusinessException 任一条 2208 冲正失败时抛出 INS-013
     */
    public void compensateSettlementsByPayment(Long paymentId) {
        if (outpatientInsuranceSettlementPort == null || paymentId == null) {
            return;
        }
        // Pre：加载该支付单下全部医保正式结算记录。
        List<OpInsuranceSettleRecordResult> records =
            outpatientInsuranceSettlementPort.listSettlementsByPayment(paymentId);
        if (records.isEmpty()) {
            return;
        }
        // Cmd：逐条调用 2208；任一条失败则中止并提示人工介入。
        for (OpInsuranceSettleRecordResult record : records) {
            if (record == null || record.getSettlementRecordId() == null) {
                continue;
            }
            OpInsuranceRevokeSettleCommand command = new OpInsuranceRevokeSettleCommand();
            command.setSettlementRecordId(record.getSettlementRecordId());
            command.setPaymentId(paymentId);
            command.setPreSettleId(record.getPreSettleId());
            command.setSetlId(record.getSetlId());
            try {
                outpatientInsuranceSettlementPort.revokeSettle(command);
            } catch (RuntimeException ex) {
                log.error(
                    "2208 compensation failed paymentId={} settlementRecordId={}",
                    paymentId,
                    record.getSettlementRecordId(),
                    ex);
                throw new BusinessException(
                    "INS-013",
                    "部分医保结算失败后自动冲正未完全成功，请勿重复收费，联系医保办处理");
            }
        }
    }
}
