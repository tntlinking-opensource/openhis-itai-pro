/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.host.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zxplat.plugin.elep.domain.prescription.command.ElepCloneCommand;
import com.zxplat.plugin.elep.domain.prescription.command.ElepEncounterListQuery;
import com.zxplat.plugin.elep.domain.prescription.result.ElepEncounterPrescriptionItem;
import com.zxplat.plugin.elep.domain.prescription.command.ElepFileUploadCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepFileUploadResult;
import com.zxplat.plugin.elep.domain.prescription.result.ElepHistoryDetail;
import com.zxplat.plugin.elep.domain.prescription.command.ElepPdfGenerateCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPdfGenerateResult;
import com.zxplat.plugin.elep.domain.prescription.command.ElepPlatformMedResultQueryCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPlatformMedResultResult;
import com.zxplat.plugin.elep.domain.prescription.command.ElepPlatformQueryCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPlatformQueryResult;
import com.zxplat.plugin.elep.domain.prescription.command.ElepPreCheckCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPreCheckResult;
import com.zxplat.plugin.elep.domain.prescription.command.ElepHisproxyRevokeCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepHisproxyRevokeResult;
import com.zxplat.plugin.elep.domain.prescription.command.ElepEncounterContextQuery;
import com.zxplat.plugin.elep.domain.prescription.result.ElepEncounterContextResult;
import com.zxplat.plugin.elep.domain.prescription.command.ElepReviewCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepReviewResult;
import com.zxplat.plugin.elep.domain.prescription.command.ElepSaveDraftCommand;
import com.zxplat.plugin.elep.domain.prescription.command.ElepSignCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepSignResult;
import com.zxplat.plugin.elep.domain.prescription.access.ElepPrescriptionOperations;
import com.zxplat.plugin.elep.host.appservice.ElepRemoteAppServiceClient;
import com.zxplat.plugin.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.zxplat.plugin.elep.domain.prescription.command.ElepMedResultQuery;
import com.zxplat.plugin.elep.domain.prescription.command.ElepPatientHistoryQuery;
import com.zxplat.plugin.elep.domain.prescription.command.ElepQueryCommand;
import com.zxplat.plugin.elep.domain.prescription.command.ElepUploadCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPrescriptionDraft;
import com.zxplat.plugin.elep.domain.prescription.result.ElepMedResultResult;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPatientHistoryResult;
import com.zxplat.plugin.elep.domain.prescription.result.ElepQueryResult;
import com.zxplat.plugin.elep.domain.prescription.result.ElepUploadResult;
import com.zxplat.plugin.elep.domain.snapshot.ElepRxNoSnapshot;
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
@ConditionalOnProperty(name = "zxplat.elep.deployment.mode", havingValue = "remote")
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
    public com.zxplat.plugin.elep.domain.prescription.result.ElepRevokeResult revoke(
            com.zxplat.plugin.elep.domain.prescription.command.ElepRevokeCommand command) {
        return remoteClient.post(SERVICE, "revokePrescription", command,
                com.zxplat.plugin.elep.domain.prescription.result.ElepRevokeResult.class);
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

    /**
     * remote：装载就诊上下文。
     *
     * @param query 就诊、患者、契约
     * @return 上下文
     */
    @Override
    public ElepEncounterContextResult loadEncounterContext(ElepEncounterContextQuery query) {
        return remoteClient.post(SERVICE, "loadEncounterContext", query, ElepEncounterContextResult.class);
    }

    /**
     * remote：院内药师审方。
     *
     * @param command 处方主键
     * @return 审方结果
     */
    @Override
    public ElepReviewResult review(ElepReviewCommand command) {
        return remoteClient.post(SERVICE, "review", command, ElepReviewResult.class);
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
