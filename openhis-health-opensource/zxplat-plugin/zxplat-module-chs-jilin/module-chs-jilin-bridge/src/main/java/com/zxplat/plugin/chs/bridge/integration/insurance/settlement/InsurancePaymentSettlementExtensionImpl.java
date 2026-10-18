/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.integration.insurance.settlement;

import com.zxplat.plugin.chs.bridge.settlement.invoker.Op2601Invoker;
import com.zxplat.plugin.chs.domain.settlement.command.OnPaymentSettledCommand;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentSettledExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.InsurancePaymentSettlementExtension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林医保收银落账事后协同 SPI 实现。
 *
 * <p><b>SPI 接口</b>：{@link InsurancePaymentSettlementExtension}</p>
 * <p><b>Health 调用方</b>：{@code PaymentReconciliationSettleOrchestration} →
 * {@code InsurancePaymentSettlementAfterCommitOrchestration}（由 {@code OutpatientChargeAppService}、
 * {@code InpatientSettleAppService} 等收银 AppService 触发）</p>
 * <p><b>Bridge 委托</b>：{@link Op2601Invoker#correctUncertainSettlements}</p>
 *
 * <h3>Gateway 出站</h3>
 * <pre>
 * zxplat.chs.deployment.mode=embedded → Op2601Invoker → InsuranceGatewayClient
 * zxplat.chs.deployment.mode=remote → 同上（remote 传输，需 -Pwith-chs-remote）
 * </pre>
 *
 * <h3>触发链</h3>
 * <pre>
 * PaymentReconciliationSettleOrchestration.afterPaymentSettled (afterCommit)
 *   → InsurancePaymentSettlementExtension.onPaymentSettled
 *   → 本类
 * </pre>
 *
 * <p>2207 正式结算不在此重复，由 {@code OutpatientChargeInsuranceExtensionImpl} 预结算/确认链完成。
 * 本钩子在落账审计后，由 {@link Op2601Invoker} 扫描
 * 「本地 SUCCESS、平台状态不明」的 2207/2304 调用并发起 2601 冲正，
 * 写入 {@code yb_settlement_correction} 并标记回执 REVOKED。</p>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(Op2601Invoker.class)
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InsurancePaymentSettlementExtensionImpl implements InsurancePaymentSettlementExtension {

    private final Op2601Invoker op2601Invoker;

    /** 本地：返回 Extension 已启用。 */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** 【2601】收银落账后扫描不确定结算并发起冲正，写入 yb_settlement_correction。 */
    @Override
    public void onPaymentSettled(InsurancePaymentSettledExtensionIn extensionIn) {
        OnPaymentSettledCommand command = InsurancePaymentSettlementIntegrationMapper.toCommand(extensionIn);
        if (command == null || command.getPaymentId() == null) {
            return;
        }
        // 落账钩子审计；2601 冲正经 Op2601Invoker 出站，不经 Facade
        log.info(
            "Insurance settlement hook received: paymentId={}, encounterId={}, inpatient={}",
            command.getPaymentId(),
            command.getEncounterId(),
            command.isInpatient());
        op2601Invoker.correctUncertainSettlements(command);
    }
}
