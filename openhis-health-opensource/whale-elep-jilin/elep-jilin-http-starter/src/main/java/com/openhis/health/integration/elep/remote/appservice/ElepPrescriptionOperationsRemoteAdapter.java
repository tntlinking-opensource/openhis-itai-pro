package com.openhis.health.integration.elep.remote.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openhis.elep.domain.prescription.dto.ElepCloneCommand;
import com.openhis.elep.domain.prescription.dto.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.dto.ElepEncounterPrescriptionItemDto;
import com.openhis.elep.domain.prescription.dto.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.dto.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.dto.ElepHistoryDetailDto;
import com.openhis.elep.domain.prescription.dto.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.dto.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.dto.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.dto.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.dto.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.dto.ElepPlatformQueryResult;
import com.openhis.elep.domain.prescription.dto.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.dto.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.dto.ElepRevokeCommand;
import com.openhis.elep.domain.prescription.dto.ElepRevokeResult;
import com.openhis.elep.domain.prescription.dto.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.dto.ElepSignCommand;
import com.openhis.elep.domain.prescription.dto.ElepSignResult;
import com.openhis.elep.domain.prescription.facade.ElepPrescriptionOperations;
import com.openhis.whale.elep.jilin.contracts.command.ElepClonePrescriptionCommand;
import com.openhis.whale.elep.jilin.contracts.command.ElepMedResultQuery;
import com.openhis.whale.elep.jilin.contracts.result.ElepMedResultResult;
import com.openhis.whale.elep.jilin.contracts.command.ElepPatientHistoryQuery;
import com.openhis.whale.elep.jilin.contracts.result.ElepPatientHistoryResult;
import com.openhis.whale.elep.jilin.contracts.dto.ElepPrescriptionDraftDto;
import com.openhis.whale.elep.jilin.contracts.command.ElepQueryCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepQueryResult;
import com.openhis.whale.elep.jilin.contracts.snapshot.ElepRxNoSnapshot;
import com.openhis.whale.elep.jilin.contracts.command.ElepUploadCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepUploadResult;
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
public class ElepPrescriptionOperationsRemoteAdapter implements ElepPrescriptionOperations {

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
    public ElepRevokeResult revokeFromHisproxy(ElepRevokeCommand command) {
        return remoteClient.post(SERVICE, "revoke", command, ElepRevokeResult.class);
    }

    @Override
    public ElepUploadResult upload(ElepUploadCommand command) {
        return remoteClient.post(SERVICE, "upload", command, ElepUploadResult.class);
    }

    @Override
    public com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult revoke(
            com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand command) {
        return remoteClient.post(
                SERVICE,
                "revokePrescription",
                command,
                com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult.class);
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
    public List<ElepEncounterPrescriptionItemDto> listEncounterPrescriptions(ElepEncounterListQuery query) {
        return remoteClient.post(
                SERVICE,
                "listEncounterPrescriptions",
                query,
                new TypeReference<List<ElepEncounterPrescriptionItemDto>>() {});
    }

    @Override
    public ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query) {
        return remoteClient.post(SERVICE, "listPatientHistory", query, ElepPatientHistoryResult.class);
    }

    @Override
    public ElepHistoryDetailDto getHistoryDetail(Long prescriptionId) {
        return remoteClient.post(SERVICE, "getHistoryDetail", Map.of("value", prescriptionId), ElepHistoryDetailDto.class);
    }

    @Override
    public ElepPrescriptionDraftDto cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        return remoteClient.post(SERVICE, "cloneToNewEncounter", command, ElepPrescriptionDraftDto.class);
    }

    @Override
    public ElepPrescriptionDraftDto cloneToEncounter(ElepCloneCommand command) {
        return remoteClient.post(SERVICE, "cloneToEncounter", command, ElepPrescriptionDraftDto.class);
    }

    @Override
    public ElepPrescriptionDraftDto saveDraft(ElepSaveDraftCommand command) {
        return remoteClient.post(SERVICE, "saveDraft", command, ElepPrescriptionDraftDto.class);
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
