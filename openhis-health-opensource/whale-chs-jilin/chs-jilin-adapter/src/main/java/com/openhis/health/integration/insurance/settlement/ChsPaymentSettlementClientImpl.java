/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement;

import com.openhis.whale.chs.jilin.contracts.client.ChsPaymentSettlementClient;
import com.openhis.whale.chs.jilin.contracts.dto.PaymentSettlementTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 收费结算 → 医保协同挂接（Wave 1：可观测占位；Wave 2 启动协同单/2207）。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsPaymentSettlementClientImpl implements ChsPaymentSettlementClient {

    /**
     * 门诊/住院收银结算完成后回调；Wave 1 仅记录日志，Wave 2 启动协同单/2207。
     *
     * @param trigger 支付 id、就诊 id、是否住院
     */
    @Override
    public void onPaymentSettled(PaymentSettlementTrigger trigger) {
        // Gate：无 paymentId 则忽略，避免空触发
        if (trigger == null || trigger.getPaymentId() == null) {
            return;
        }
        log.info(
                "Insurance settlement hook received: paymentId={}, encounterId={}, inpatient={}",
                trigger.getPaymentId(),
                trigger.getEncounterId(),
                trigger.isInpatient());
    }
}
