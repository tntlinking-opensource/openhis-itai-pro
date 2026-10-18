/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.integration.insurance.settlement;

import com.zxplat.plugin.chs.domain.settlement.command.OnPaymentSettledCommand;
import com.zxplat.plugin.chs.domain.settlement.command.OnPaymentSettledYbSettlementLine;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentSettledExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionIn.InsurancePaymentYbSettlementLineExtensionIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Health 收银落账 ExtensionIn ↔ chs domain Command 映射。
 *
 * <p>映射方向：{@link InsurancePaymentSettledExtensionIn} → {@link OnPaymentSettledCommand}（仅入向，无 ExtensionOut）。</p>
 */
public final class InsurancePaymentSettlementIntegrationMapper {

    private InsurancePaymentSettlementIntegrationMapper() {
    }

    /**
     * ExtensionIn → domain Command（无 IO）。
     *
     * @param extensionIn SPI 入参；{@code null} 时返回 {@code null}
     * @return 领域命令 */
    public static OnPaymentSettledCommand toCommand(InsurancePaymentSettledExtensionIn extensionIn) {
        if (extensionIn == null) {
            return null;
        }
        OnPaymentSettledCommand command = new OnPaymentSettledCommand();
        command.setPaymentId(extensionIn.getPaymentId());
        command.setEncounterId(extensionIn.getEncounterId());
        command.setChargeItemIds(extensionIn.getChargeItemIds());
        command.setDisplayAmount(extensionIn.getDisplayAmount());
        command.setBillDate(extensionIn.getBillDate());
        command.setEntererId(extensionIn.getEntererId());
        command.setInpatient(extensionIn.isInpatient());
        command.setYbSettlements(toLines(extensionIn.getYbSettlements()));
        return command;
    }

    /**
     * 将医保结算行 ExtensionIn 列表映射为落账命令行。
     *
     * @param items 医保结算行扩展入参列表
     * @return 落账命令医保结算行列表
     */
    private static List<OnPaymentSettledYbSettlementLine> toLines(
            List<InsurancePaymentYbSettlementLineExtensionIn> items) {
        List<OnPaymentSettledYbSettlementLine> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (InsurancePaymentYbSettlementLineExtensionIn item : items) {
            if (item == null) {
                continue;
            }
            OnPaymentSettledYbSettlementLine line = new OnPaymentSettledYbSettlementLine();
            line.setSetlId(item.getSettlementId());
            line.setChrgBchno(item.getChargeBatchNo());
            line.setMdtrtId(item.getEncounterPlatformId());
            line.setSettlementRecordId(item.getSettlementRecordId());
            line.setAccountId(item.getAccountId());
            line.setStatus(item.getStatus());
            result.add(line);
        }
        return result;
    }
}
