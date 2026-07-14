/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.application.client;

import com.openhis.elep.domain.prescription.access.ElepPrescriptionOperations;
import com.openhis.elep.domain.client.JilinElepPrescriptionClient;
import com.openhis.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.openhis.elep.domain.prescription.command.ElepMedResultQuery;
import com.openhis.elep.domain.prescription.command.ElepPatientHistoryQuery;
import com.openhis.elep.domain.prescription.command.ElepQueryCommand;
import com.openhis.elep.domain.prescription.command.ElepRevokeCommand;
import com.openhis.elep.domain.prescription.command.ElepUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepPrescriptionDraft;
import com.openhis.elep.domain.prescription.result.ElepMedResultResult;
import com.openhis.elep.domain.prescription.result.ElepPatientHistoryResult;
import com.openhis.elep.domain.prescription.result.ElepQueryResult;
import com.openhis.elep.domain.prescription.result.ElepRevokeResult;
import com.openhis.elep.domain.prescription.result.ElepUploadResult;
import com.openhis.elep.domain.snapshot.ElepRxNoSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@link JilinElepPrescriptionClient} 实现（委托 {@link ElepPrescriptionOperations}）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(name = {"elepPrescriptionOrchestrator", "elepPrescriptionOperationsRemoteAdapter"})
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
    public ElepPrescriptionDraft cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        return elepPrescriptionOperations.cloneToNewEncounter(command);
    }
}
