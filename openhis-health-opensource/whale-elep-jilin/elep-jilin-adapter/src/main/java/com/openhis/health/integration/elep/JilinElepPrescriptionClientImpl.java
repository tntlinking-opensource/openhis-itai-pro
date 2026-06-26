/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep;

import com.openhis.elep.domain.prescription.facade.ElepPrescriptionOperations;
import com.openhis.whale.elep.jilin.contracts.client.JilinElepPrescriptionClient;
import com.openhis.whale.elep.jilin.contracts.command.ElepClonePrescriptionCommand;
import com.openhis.whale.elep.jilin.contracts.command.ElepMedResultQuery;
import com.openhis.whale.elep.jilin.contracts.result.ElepMedResultResult;
import com.openhis.whale.elep.jilin.contracts.command.ElepPatientHistoryQuery;
import com.openhis.whale.elep.jilin.contracts.result.ElepPatientHistoryResult;
import com.openhis.whale.elep.jilin.contracts.dto.ElepPrescriptionDraftDto;
import com.openhis.whale.elep.jilin.contracts.command.ElepQueryCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepQueryResult;
import com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult;
import com.openhis.whale.elep.jilin.contracts.snapshot.ElepRxNoSnapshot;
import com.openhis.whale.elep.jilin.contracts.command.ElepUploadCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 电子处方 契约实现（委托 {@link ElepPrescriptionOperations}）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ElepPrescriptionOperations.class)
@ConditionalOnProperty(name = "openhis.modules.elep.enabled", havingValue = "true")
public class JilinElepPrescriptionClientImpl implements JilinElepPrescriptionClient {

    private final ElepPrescriptionOperations elepPrescriptionOperations;

    @Override
    public boolean supportsContract(Long contractId) {
        return elepPrescriptionOperations.supportsContract(contractId);
    }

    @Override
    public ElepUploadResult upload(ElepUploadCommand command) {
        return elepPrescriptionOperations.upload(command);
    }

    @Override
    public ElepRevokeResult revoke(ElepRevokeCommand command) {
        return elepPrescriptionOperations.revoke(command);
    }

    @Override
    public ElepQueryResult query(ElepQueryCommand command) {
        return elepPrescriptionOperations.query(command);
    }

    @Override
    public ElepMedResultResult queryMedResult(ElepMedResultQuery query) {
        return elepPrescriptionOperations.queryMedResult(query);
    }

    @Override
    public Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId) {
        return elepPrescriptionOperations.findRxNoByPrescription(prescriptionGroupId);
    }

    @Override
    public Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo) {
        return elepPrescriptionOperations.findRxNoByPrescriptionNo(prescriptionNo);
    }

    @Override
    public ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query) {
        return elepPrescriptionOperations.listPatientHistory(query);
    }

    @Override
    public ElepPrescriptionDraftDto cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        return elepPrescriptionOperations.cloneToNewEncounter(command);
    }
}
