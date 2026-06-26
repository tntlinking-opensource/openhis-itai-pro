/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement;

import com.openhis.health.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientRegistrationPlatformCommand;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientRegistrationPlatformResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientRegistrationPlatformSnapshot;
import com.openhis.whale.chs.jilin.contracts.client.ChsOutpatientSettlementClient;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRegistrationCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeRegistrationCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePlatformRegistrationSnapshot;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRegistrationResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeRegistrationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 门诊挂号 ↔ 吉林医保平台登记的 Extension 桥接层。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(ChsOutpatientSettlementClient.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class OutpatientRegistrationInsuranceExtensionImpl implements OutpatientRegistrationInsuranceExtension {

    private final ChsOutpatientSettlementClient chsOutpatientSettlementClient;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean supportsThirdPartyContract(Long contractId) {
        return chsOutpatientSettlementClient.isInsuranceContract(contractId);
    }

    @Override
    public OutpatientRegistrationPlatformResult ensurePlatformRegistration(
        OutpatientRegistrationPlatformCommand command) {
        OpInsuranceRegistrationResult chsResult =
            chsOutpatientSettlementClient.ensurePlatformRegistration(toChsCommand(command));
        return toNeutralResult(chsResult);
    }

    @Override
    public OutpatientRegistrationPlatformResult revokePlatformRegistration(
        OutpatientRegistrationPlatformCommand command) {
        OpInsuranceRevokeRegistrationResult chsResult =
            chsOutpatientSettlementClient.revokePlatformRegistration(toRevokeCommand(command));
        OutpatientRegistrationPlatformResult result = new OutpatientRegistrationPlatformResult();
        if (chsResult != null) {
            result.setEncounterPlatformId(chsResult.getMdtrtId());
        }
        return result;
    }

    @Override
    public List<OutpatientRegistrationPlatformSnapshot> findPlatformSnapshots(
        List<Long> encounterIds,
        Map<Long, Long> paymentDraftIdByEncounter) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> draftMap = paymentDraftIdByEncounter != null ? paymentDraftIdByEncounter : Map.of();
        List<OpInsurancePlatformRegistrationSnapshot> rows =
            chsOutpatientSettlementClient.listPlatformRegistrationSnapshots(encounterIds, draftMap);
        List<OutpatientRegistrationPlatformSnapshot> result = new ArrayList<>(rows.size());
        for (OpInsurancePlatformRegistrationSnapshot row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            result.add(new OutpatientRegistrationPlatformSnapshot()
                .setEncounterId(row.getEncounterId())
                .setContractId(row.getContractId())
                .setMdtrtId(row.getMdtrtId())
                .setInsuplcAdmdvs(row.getInsuplcAdmdvs())
                .setPlatformRegistered(row.getPlatformRegistered())
                .setInsurancePreSettled(row.getInsurancePreSettled())
                .setYbFundPaySumamt(row.getYbFundPaySumamt())
                .setYbAcctPay(row.getYbAcctPay())
                .setYbPsnCashPay(row.getYbPsnCashPay()));
        }
        return result;
    }

    private static OpInsuranceRevokeRegistrationCommand toRevokeCommand(OutpatientRegistrationPlatformCommand command) {
        OpInsuranceRevokeRegistrationCommand chsCommand = new OpInsuranceRevokeRegistrationCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setContractId(command.getContractId());
        chsCommand.setMedType(command.getMedType());
        chsCommand.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        return chsCommand;
    }

    private static OpInsuranceRegistrationCommand toChsCommand(OutpatientRegistrationPlatformCommand command) {
        OpInsuranceRegistrationCommand chsCommand = new OpInsuranceRegistrationCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setContractId(command.getContractId());
        chsCommand.setMdtrtCertType(command.getPayerCertType());
        chsCommand.setMdtrtCertNo(command.getPayerCertNo());
        chsCommand.setPsnNo(command.getInsuredPersonNo());
        chsCommand.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        chsCommand.setInsutype(command.getInsutype());
        chsCommand.setMedType(command.getMedType());
        return chsCommand;
    }

    private static OutpatientRegistrationPlatformResult toNeutralResult(OpInsuranceRegistrationResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        OutpatientRegistrationPlatformResult result = new OutpatientRegistrationPlatformResult();
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        return result;
    }
}
