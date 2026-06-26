/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.facade;

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
import com.openhis.elep.domain.prescription.dto.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.dto.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.dto.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.dto.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.dto.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.dto.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.dto.ElepCloneCommand;
import com.openhis.elep.domain.prescription.dto.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.dto.ElepEncounterPrescriptionItemDto;
import com.openhis.elep.domain.prescription.dto.ElepHistoryDetailDto;
import com.openhis.elep.domain.prescription.dto.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.dto.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.dto.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.dto.ElepPlatformQueryResult;
import com.openhis.elep.domain.prescription.dto.ElepRevokeCommand;
import com.openhis.elep.domain.prescription.dto.ElepRevokeResult;
import com.openhis.elep.domain.prescription.dto.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.dto.ElepSignCommand;
import com.openhis.elep.domain.prescription.dto.ElepSignResult;

import java.util.List;
import java.util.Optional;

/** 电子处方领域操作门面（由 application 实现）。 */
public interface ElepPrescriptionOperations {

    boolean supportsContract(Long contractId);

    ElepPreCheckResult preCheck(ElepPreCheckCommand command);

    ElepFileUploadResult uploadFile(ElepFileUploadCommand command);

    ElepPdfGenerateResult generatePdf(ElepPdfGenerateCommand command);

    ElepSignResult sign(ElepSignCommand command);

    ElepRevokeResult revokeFromHisproxy(ElepRevokeCommand command);

    ElepUploadResult upload(ElepUploadCommand command);

    com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult revoke(
            com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand command);

    ElepQueryResult query(ElepQueryCommand command);

    ElepMedResultResult queryMedResult(ElepMedResultQuery query);

    Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId);

    Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo);

    List<ElepEncounterPrescriptionItemDto> listEncounterPrescriptions(ElepEncounterListQuery query);

    ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query);

    ElepHistoryDetailDto getHistoryDetail(Long prescriptionId);

    ElepPrescriptionDraftDto cloneToNewEncounter(ElepClonePrescriptionCommand command);

    ElepPrescriptionDraftDto cloneToEncounter(ElepCloneCommand command);

    ElepPrescriptionDraftDto saveDraft(ElepSaveDraftCommand command);

    ElepPlatformQueryResult queryPlatform(ElepPlatformQueryCommand command);

    ElepPlatformMedResultResult queryMedResultPlatform(ElepPlatformMedResultQueryCommand command);
}
