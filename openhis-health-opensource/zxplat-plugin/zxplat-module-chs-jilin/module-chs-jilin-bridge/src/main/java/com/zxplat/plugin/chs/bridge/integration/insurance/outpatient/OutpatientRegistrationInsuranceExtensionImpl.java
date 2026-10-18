/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.integration.insurance.outpatient;

import com.zxplat.plugin.chs.bridge.outpatient.workflow.OpPlatformRegistrationOrchestrator;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.outpatient.queries.model.OpPlatformRegistrationQueryModel;
import com.zxplat.plugin.chs.domain.outpatient.loader.OpPreSettleContextLoader;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 吉林医保门诊挂号平台登记 SPI 实现。
 *
 * <p><b>SPI 接口</b>：{@link OutpatientRegistrationInsuranceExtension}</p>
 * <p><b>Health 调用方</b>：{@code OutpatientRegistrationAppService}、
 * {@code RegistrationReceiptPrintAssembler}、{@code ChargeReceiptPrintAssembler}</p>
 * <p><b>Bridge 委托</b>：{@link OpPreSettleContextLoader} 补全上下文 →
 * {@link OpPlatformRegistrationOrchestrator}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(OpPlatformRegistrationOrchestrator.class)
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class OutpatientRegistrationInsuranceExtensionImpl implements OutpatientRegistrationInsuranceExtension {

    private final ChsInsuranceContractReader contractReader;
    private final OpPreSettleContextLoader contextLoader;
    private final OpPlatformRegistrationOrchestrator registrationOrchestrator;

    /** 本地：返回 Extension 已启用。 */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** 本地：判断合同是否为医保第三方合同。 */
    @Override
    public boolean supportsThirdPartyContract(Long contractId) {
        return contractReader.isInsuranceContract(contractId);
    }

    /** 【2201】确保平台挂号登记，写入 mdtrt_id。 */
    @Override
    public OutpatientRegistrationPlatformExtensionOut ensurePlatformRegistration(
        OutpatientRegistrationPlatformExtensionIn command) {
        var enriched = contextLoader.enrichRegistration(
            ChsOutpatientInsuranceIntegrationMapper.toPlatformRegistrationCommand(command));
        return ChsOutpatientInsuranceIntegrationMapper.toExtensionRegistrationOut(
            registrationOrchestrator.ensureRegistration(enriched));
    }

    /** 【2202】撤销平台挂号登记。 */
    @Override
    public OutpatientRegistrationPlatformExtensionOut revokePlatformRegistration(
        OutpatientRegistrationPlatformExtensionIn command) {
        return ChsOutpatientInsuranceIntegrationMapper.toExtensionRevokeRegistrationOut(
            registrationOrchestrator.revokeRegistration(
                ChsOutpatientInsuranceIntegrationMapper.toRevokePlatformRegistrationCommand(command)));
    }

    /** 本地：按就诊 ID 查询平台登记快照，无网关出站。 */
    @Override
    public List<OutpatientRegistrationPlatformQueryExtensionOut> findPlatformSnapshots(
        OutpatientRegistrationPlatformQueryExtensionIn query) {
        if (query == null || query.getEncounterIds() == null || query.getEncounterIds().isEmpty()) {
            return List.of();
        }
        Map<Long, Long> draftMap;
        if (query.getPaymentDraftIdByEncounter() != null) {
            draftMap = query.getPaymentDraftIdByEncounter();
        } else {
            draftMap = Map.of();
        }
        List<OpPlatformRegistrationQueryModel> rows =
            registrationOrchestrator.listSnapshots(query.getEncounterIds(), draftMap);
        List<OutpatientRegistrationPlatformQueryExtensionOut> result = new ArrayList<>(rows.size());
        for (OpPlatformRegistrationQueryModel row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            result.add(new OutpatientRegistrationPlatformQueryExtensionOut()
                .setEncounterId(row.getEncounterId())
                .setContractId(row.getContractId())
                .setMdtrtId(row.getMdtrtId())
                .setPsnNo(row.getPsnNo())
                .setInsuplcAdmdvs(row.getInsuplcAdmdvs())
                .setPlatformRegistered(row.getPlatformRegistered())
                .setInsurancePreSettled(row.getInsurancePreSettled())
                .setYbFundPaySumamt(row.getYbFundPaySumamt())
                .setYbAcctPay(row.getYbAcctPay())
                .setYbPsnCashPay(row.getYbPsnCashPay()));
        }
        return result;
    }
}
