/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.elep.remote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openhis.elep.domain.prescription.command.ElepCloneCommand;
import com.openhis.elep.domain.prescription.command.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterPrescriptionItem;
import com.openhis.elep.domain.prescription.command.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.result.ElepHistoryDetail;
import com.openhis.elep.domain.prescription.command.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.result.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.command.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.command.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformQueryResult;
import com.openhis.elep.domain.prescription.command.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.result.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.command.ElepHisproxyRevokeCommand;
import com.openhis.elep.domain.prescription.result.ElepHisproxyRevokeResult;
import com.openhis.elep.domain.prescription.command.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.command.ElepSignCommand;
import com.openhis.elep.domain.prescription.result.ElepSignResult;
import com.openhis.elep.domain.prescription.access.ElepPrescriptionOperations;
import com.openhis.health.integration.elep.remote.appservice.ElepRemoteAppServiceClient;
import com.openhis.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.openhis.elep.domain.prescription.command.ElepMedResultQuery;
import com.openhis.elep.domain.prescription.command.ElepPatientHistoryQuery;
import com.openhis.elep.domain.prescription.command.ElepQueryCommand;
import com.openhis.elep.domain.prescription.command.ElepUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepPrescriptionDraft;
import com.openhis.elep.domain.prescription.result.ElepMedResultResult;
import com.openhis.elep.domain.prescription.result.ElepPatientHistoryResult;
import com.openhis.elep.domain.prescription.result.ElepQueryResult;
import com.openhis.elep.domain.prescription.result.ElepUploadResult;
import com.openhis.elep.domain.snapshot.ElepRxNoSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * remote 模式：HTTP 代理至 ELEP 服务 {@code ElepPrescriptionAppService} bridge。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.elep.deployment.mode", havingValue = "remote")
public class ElepPrescriptionOperationsRemoteClientImpl implements ElepPrescriptionOperations {

    private static final String SERVICE = "elepPrescription";

    private final ElepRemoteAppServiceClient remoteClient;

    @Override
    public boolean supportsContract(Long contractId) {
        Boolean supported = remoteClient.post(SERVICE, "supportsContract", Map.of("value", contractId), Boolean.class);
        return Boolean.TRUE.equals(supported);
    }

    @Override
    public ElepPreCheckResult preCheck(ElepPreCheckCommand command) {
        return remoteClient.post(SERVICE, "preCheck", command, ElepPreCheckResult.class);
    }

    @Override
    public ElepFileUploadResult uploadFile(ElepFileUploadCommand command) {
        return remoteClient.post(SERVICE, "uploadFile", command, ElepFileUploadResult.class);
    }

    @Override
    public ElepPdfGenerateResult generatePdf(ElepPdfGenerateCommand command) {
        return remoteClient.post(SERVICE, "generatePdf", command, ElepPdfGenerateResult.class);
    }

    @Override
    public ElepSignResult sign(ElepSignCommand command) {
        return remoteClient.post(SERVICE, "sign", command, ElepSignResult.class);
    }

    @Override
    public ElepHisproxyRevokeResult revokeFromHisproxy(ElepHisproxyRevokeCommand command) {
        return remoteClient.post(SERVICE, "revoke", command, ElepHisproxyRevokeResult.class);
    }

    @Override
    public ElepUploadResult upload(ElepUploadCommand command) {
        return remoteClient.post(SERVICE, "upload", command, ElepUploadResult.class);
    }

    @Override
    public com.openhis.elep.domain.prescription.result.ElepRevokeResult revoke(
            com.openhis.elep.domain.prescription.command.ElepRevokeCommand command) {
        return remoteClient.post(SERVICE, "revokePrescription", command,
                com.openhis.elep.domain.prescription.result.ElepRevokeResult.class);
    }

    @Override
    public ElepQueryResult query(ElepQueryCommand command) {
        return remoteClient.post(SERVICE, "query", command, ElepQueryResult.class);
    }

    @Override
    public ElepMedResultResult queryMedResult(ElepMedResultQuery query) {
        return remoteClient.post(SERVICE, "queryMedResult", query, ElepMedResultResult.class);
    }

    @Override
    public Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId) {
        return remoteClient.postOptional(
                SERVICE, "findRxNoByPrescription", Map.of("value", prescriptionGroupId), ElepRxNoSnapshot.class);
    }

    @Override
    public Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo) {
        return remoteClient.postOptional(
                SERVICE, "findRxNoByPrescriptionNo", Map.of("value", prescriptionNo), ElepRxNoSnapshot.class);
    }

    @Override
    public List<ElepEncounterPrescriptionItem> listEncounterPrescriptions(ElepEncounterListQuery query) {
        return remoteClient.post(
                SERVICE,
                "listEncounterPrescriptions",
                query,
                new TypeReference<List<ElepEncounterPrescriptionItem>>() {});
    }

    @Override
    public ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query) {
        return remoteClient.post(SERVICE, "listPatientHistory", query, ElepPatientHistoryResult.class);
    }

    @Override
    public ElepHistoryDetail getHistoryDetail(Long prescriptionId) {
        return remoteClient.post(SERVICE, "getHistoryDetail", Map.of("value", prescriptionId), ElepHistoryDetail.class);
    }

    @Override
    public ElepPrescriptionDraft cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        return remoteClient.post(SERVICE, "cloneToNewEncounter", command, ElepPrescriptionDraft.class);
    }

    @Override
    public ElepPrescriptionDraft cloneToEncounter(ElepCloneCommand command) {
        return remoteClient.post(SERVICE, "cloneToEncounter", command, ElepPrescriptionDraft.class);
    }

    @Override
    public ElepPrescriptionDraft saveDraft(ElepSaveDraftCommand command) {
        return remoteClient.post(SERVICE, "saveDraft", command, ElepPrescriptionDraft.class);
    }

    @Override
    public ElepPlatformQueryResult queryPlatform(ElepPlatformQueryCommand command) {
        return remoteClient.post(SERVICE, "queryPlatform", command, ElepPlatformQueryResult.class);
    }

    @Override
    public ElepPlatformMedResultResult queryMedResultPlatform(ElepPlatformMedResultQueryCommand command) {
        return remoteClient.post(SERVICE, "queryMedResult", command, ElepPlatformMedResultResult.class);
    }
}
