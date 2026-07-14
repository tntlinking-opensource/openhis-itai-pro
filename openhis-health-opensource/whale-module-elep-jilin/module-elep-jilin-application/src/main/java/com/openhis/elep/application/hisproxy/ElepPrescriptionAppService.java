/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.application.hisproxy;

import com.openhis.elep.domain.prescription.command.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.command.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.result.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.command.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.result.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.command.ElepHisproxyRevokeCommand;
import com.openhis.elep.domain.prescription.result.ElepHisproxyRevokeResult;
import com.openhis.elep.domain.prescription.command.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.command.ElepSignCommand;
import com.openhis.elep.domain.prescription.result.ElepSignResult;
import com.openhis.elep.domain.prescription.command.ElepCloneCommand;
import com.openhis.elep.domain.prescription.command.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterPrescriptionItem;
import com.openhis.elep.domain.prescription.result.ElepHistoryDetail;
import com.openhis.elep.domain.prescription.command.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.command.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformQueryResult;
import com.openhis.elep.domain.prescription.access.ElepPrescriptionOperations;
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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 电子处方 bridge（方案 1A：前端调 {@code /api/app/elepPrescription/*}）。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(name = {"elepPrescriptionOrchestrator", "elepPrescriptionOperationsRemoteAdapter"})
@ConditionalOnProperty(name = "openhis.modules.elep.enabled", havingValue = "true")
public class ElepPrescriptionAppService {

    private final ElepPrescriptionOperations elepPrescriptionOperations;

    /** 合约是否支持电子处方（Client / remote 2204 挂接）。 */
    public boolean supportsContract(Long contractId) {
        return elepPrescriptionOperations.supportsContract(contractId);
    }

    /** 【A0001】上传预核验。 */
    public ElepPreCheckResult preCheck(ElepPreCheckCommand command) {
        return elepPrescriptionOperations.preCheck(command);
    }

    /** 【A0003】电子处方上传。 */
    public ElepFileUploadResult uploadFile(ElepFileUploadCommand command) {
        return elepPrescriptionOperations.uploadFile(command);
    }

    /** 本地 PDF 生成（A0001 后、A0002 前）。 */
    public ElepPdfGenerateResult generatePdf(ElepPdfGenerateCommand command) {
        return elepPrescriptionOperations.generatePdf(command);
    }

    /** 【A0002】医保电子签名。 */
    public ElepSignResult sign(ElepSignCommand command) {
        return elepPrescriptionOperations.sign(command);
    }

    /** 【A0004】电子处方撤销。 */
    public ElepHisproxyRevokeResult revoke(ElepHisproxyRevokeCommand command) {
        return elepPrescriptionOperations.revokeFromHisproxy(command);
    }

    /** 患者历史上传方列表。 */
    public ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query) {
        return elepPrescriptionOperations.listPatientHistory(query);
    }

    /** 当前就诊电子处方列表（含草稿/在途）。 */
    public List<ElepEncounterPrescriptionItem> listEncounterPrescriptions(ElepEncounterListQuery query) {
        return elepPrescriptionOperations.listEncounterPrescriptions(query);
    }

    /** 历史处方详情（含 snapshot）。 */
    public ElepHistoryDetail getHistoryDetail(Long prescriptionId) {
        return elepPrescriptionOperations.getHistoryDetail(prescriptionId);
    }

    /** 套用/续方 → 新 DRAFT。 */
    public ElepPrescriptionDraft cloneToEncounter(ElepCloneCommand command) {
        return elepPrescriptionOperations.cloneToEncounter(command);
    }

    /** 保存/更新草稿（DRAFT/FAILED）。 */
    public ElepPrescriptionDraft saveDraft(ElepSaveDraftCommand command) {
        return elepPrescriptionOperations.saveDraft(command);
    }

    /** 【A0005】平台处方信息查询。 */
    public ElepPlatformQueryResult queryPlatform(ElepPlatformQueryCommand command) {
        return elepPrescriptionOperations.queryPlatform(command);
    }

    /** 【A0006】取药结果查询。 */
    public ElepPlatformMedResultResult queryMedResult(ElepPlatformMedResultQueryCommand command) {
        return elepPrescriptionOperations.queryMedResultPlatform(command);
    }

    /** contracts：电子处方上传。 */
    public ElepUploadResult upload(ElepUploadCommand command) {
        return elepPrescriptionOperations.upload(command);
    }

    /** contracts：电子处方撤销。 */
    public com.openhis.elep.domain.prescription.result.ElepRevokeResult revokePrescription(
            com.openhis.elep.domain.prescription.command.ElepRevokeCommand command) {
        return elepPrescriptionOperations.revoke(command);
    }

    /** contracts：处方状态查询。 */
    public ElepQueryResult query(ElepQueryCommand command) {
        return elepPrescriptionOperations.query(command);
    }

    /** contracts：取药结果查询。 */
    public ElepMedResultResult queryMedResult(ElepMedResultQuery query) {
        return elepPrescriptionOperations.queryMedResult(query);
    }

    /** Client / 2204：按处方组 ID 查 hi_rxno。 */
    public Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId) {
        return elepPrescriptionOperations.findRxNoByPrescription(prescriptionGroupId);
    }

    /** Client / 2204：按处方号查 hi_rxno。 */
    public Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo) {
        return elepPrescriptionOperations.findRxNoByPrescriptionNo(prescriptionNo);
    }

    /** Client：套用/续方至新就诊。 */
    public ElepPrescriptionDraft cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        return elepPrescriptionOperations.cloneToNewEncounter(command);
    }
}
