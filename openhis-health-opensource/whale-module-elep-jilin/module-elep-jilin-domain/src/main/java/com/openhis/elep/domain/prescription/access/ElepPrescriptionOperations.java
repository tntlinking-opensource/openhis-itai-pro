/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.access;

import com.openhis.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.openhis.elep.domain.prescription.command.ElepMedResultQuery;
import com.openhis.elep.domain.prescription.result.ElepMedResultResult;
import com.openhis.elep.domain.prescription.command.ElepPatientHistoryQuery;
import com.openhis.elep.domain.prescription.result.ElepPatientHistoryResult;
import com.openhis.elep.domain.prescription.result.ElepPrescriptionDraft;
import com.openhis.elep.domain.prescription.command.ElepQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepQueryResult;
import com.openhis.elep.domain.snapshot.ElepRxNoSnapshot;
import com.openhis.elep.domain.prescription.command.ElepUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepUploadResult;
import com.openhis.elep.domain.prescription.command.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.command.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.result.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.command.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.result.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.command.ElepCloneCommand;
import com.openhis.elep.domain.prescription.command.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterPrescriptionItem;
import com.openhis.elep.domain.prescription.result.ElepHistoryDetail;
import com.openhis.elep.domain.prescription.command.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.command.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformQueryResult;
import com.openhis.elep.domain.prescription.command.ElepHisproxyRevokeCommand;
import com.openhis.elep.domain.prescription.command.ElepRevokeCommand;
import com.openhis.elep.domain.prescription.result.ElepHisproxyRevokeResult;
import com.openhis.elep.domain.prescription.result.ElepRevokeResult;
import com.openhis.elep.domain.prescription.command.ElepEncounterContextQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterContextResult;
import com.openhis.elep.domain.prescription.command.ElepReviewCommand;
import com.openhis.elep.domain.prescription.result.ElepReviewResult;
import com.openhis.elep.domain.prescription.command.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.command.ElepSignCommand;
import com.openhis.elep.domain.prescription.result.ElepSignResult;

import java.util.List;
import java.util.Optional;

/** 电子处方领域操作端口（由 application {@code ElepPrescriptionOrchestrator} 实现；host Remote 透传）。 */
public interface ElepPrescriptionOperations {

    boolean supportsContract(Long contractId);

    ElepPreCheckResult preCheck(ElepPreCheckCommand command);

    ElepFileUploadResult uploadFile(ElepFileUploadCommand command);

    ElepPdfGenerateResult generatePdf(ElepPdfGenerateCommand command);

    ElepSignResult sign(ElepSignCommand command);

    ElepHisproxyRevokeResult revokeFromHisproxy(ElepHisproxyRevokeCommand command);

    ElepUploadResult upload(ElepUploadCommand command);

    ElepRevokeResult revoke(ElepRevokeCommand command);

    ElepQueryResult query(ElepQueryCommand command);

    ElepMedResultResult queryMedResult(ElepMedResultQuery query);

    Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId);

    Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo);

    List<ElepEncounterPrescriptionItem> listEncounterPrescriptions(ElepEncounterListQuery query);

    ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query);

    ElepHistoryDetail getHistoryDetail(Long prescriptionId);

    ElepPrescriptionDraft cloneToNewEncounter(ElepClonePrescriptionCommand command);

    ElepPrescriptionDraft cloneToEncounter(ElepCloneCommand command);

    ElepPrescriptionDraft saveDraft(ElepSaveDraftCommand command);

    /**
     * 装载当前就诊只读上下文（挂号 map(11) + 医师/科室医保码）。
     *
     * @param query 就诊、患者、契约
     * @return 上下文；不允许开方时 {@code allowed=false}
     */
    ElepEncounterContextResult loadEncounterContext(ElepEncounterContextQuery query);

    /**
     * 院内医保药师审方（写入 phar_*，状态仍为草稿）。
     *
     * @param command 处方主键
     * @return 审方结果
     */
    ElepReviewResult review(ElepReviewCommand command);

    ElepPlatformQueryResult queryPlatform(ElepPlatformQueryCommand command);

    ElepPlatformMedResultResult queryMedResultPlatform(ElepPlatformMedResultQueryCommand command);
}
