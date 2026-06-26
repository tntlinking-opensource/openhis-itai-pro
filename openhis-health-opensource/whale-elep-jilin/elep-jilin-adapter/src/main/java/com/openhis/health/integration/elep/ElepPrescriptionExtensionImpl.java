/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep;

import com.openhis.health.integration.elep.command.CloneElepPrescriptionCommand;
import com.openhis.health.integration.elep.command.ElepMedResultQueryCommand;
import com.openhis.health.integration.elep.command.ElepQueryCommand;
import com.openhis.health.integration.elep.command.ElepRevokeCommand;
import com.openhis.health.integration.elep.command.ElepUploadCommand;
import com.openhis.health.integration.elep.command.PatientElepHistoryQuery;
import com.openhis.health.integration.elep.mapper.ElepIntegrationMapper;
import com.openhis.health.integration.elep.result.ElepMedResult;
import com.openhis.health.integration.elep.result.ElepPageResult;
import com.openhis.health.integration.elep.result.ElepPrescriptionDraftDto;
import com.openhis.health.integration.elep.result.ElepQueryResult;
import com.openhis.health.integration.elep.result.ElepRevokeResult;
import com.openhis.health.integration.elep.result.ElepRxNoSnapshot;
import com.openhis.health.integration.elep.result.ElepUploadResult;
import com.openhis.health.integration.elep.result.PatientElepPrescriptionSummaryDto;
import com.openhis.whale.elep.jilin.contracts.client.JilinElepPrescriptionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@link ElepPrescriptionExtension} 实现（委托 {@link JilinElepPrescriptionClient}）。省份差异由 Maven 模块隔离。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(JilinElepPrescriptionClient.class)
@ConditionalOnProperty(name = "openhis.modules.elep.enabled", havingValue = "true")
public class ElepPrescriptionExtensionImpl implements ElepPrescriptionExtension {

    private final JilinElepPrescriptionClient jilinElepPrescriptionClient;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean supportsPrescription(Long prescriptionGroupId) {
        return prescriptionGroupId != null;
    }

    @Override
    public ElepUploadResult upload(ElepUploadCommand command) {
        return ElepIntegrationMapper.toExtensionUploadResult(
                jilinElepPrescriptionClient.upload(ElepIntegrationMapper.toContractsUploadCommand(command)));
    }

    @Override
    public ElepRevokeResult revoke(ElepRevokeCommand command) {
        return ElepIntegrationMapper.toExtensionRevokeResult(
                jilinElepPrescriptionClient.revoke(ElepIntegrationMapper.toContractsRevokeCommand(command)));
    }

    @Override
    public ElepQueryResult query(ElepQueryCommand command) {
        return ElepIntegrationMapper.toExtensionQueryResult(
                jilinElepPrescriptionClient.query(ElepIntegrationMapper.toContractsQueryCommand(command)));
    }

    @Override
    public ElepMedResult queryMedResult(ElepMedResultQueryCommand command) {
        return ElepIntegrationMapper.toExtensionMedResult(
                jilinElepPrescriptionClient.queryMedResult(
                        ElepIntegrationMapper.toContractsMedResultQuery(command)));
    }

    @Override
    public Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId) {
        return jilinElepPrescriptionClient.findRxNoByPrescription(prescriptionGroupId)
                .map(ElepIntegrationMapper::toExtensionRxNoSnapshot);
    }

    @Override
    public Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo) {
        return jilinElepPrescriptionClient.findRxNoByPrescriptionNo(prescriptionNo)
                .map(ElepIntegrationMapper::toExtensionRxNoSnapshot);
    }

    @Override
    public ElepPageResult<PatientElepPrescriptionSummaryDto> listPatientHistory(PatientElepHistoryQuery query) {
        return ElepIntegrationMapper.toExtensionPatientHistoryPage(
                jilinElepPrescriptionClient.listPatientHistory(
                        ElepIntegrationMapper.toContractsPatientHistoryQuery(query)));
    }

    @Override
    public ElepPrescriptionDraftDto cloneToNewEncounter(CloneElepPrescriptionCommand command) {
        return ElepIntegrationMapper.toExtensionPrescriptionDraftDto(
                jilinElepPrescriptionClient.cloneToNewEncounter(
                        ElepIntegrationMapper.toContractsCloneCommand(command)));
    }
}
