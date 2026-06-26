/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.application.hisproxy;

import com.openhis.elep.domain.prescription.dto.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.dto.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.dto.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.dto.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.dto.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.dto.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.dto.ElepRevokeCommand;
import com.openhis.elep.domain.prescription.dto.ElepRevokeResult;
import com.openhis.elep.domain.prescription.dto.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.dto.ElepSignCommand;
import com.openhis.elep.domain.prescription.dto.ElepSignResult;
import com.openhis.elep.domain.prescription.dto.ElepCloneCommand;
import com.openhis.elep.domain.prescription.dto.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.dto.ElepEncounterPrescriptionItemDto;
import com.openhis.elep.domain.prescription.dto.ElepHistoryDetailDto;
import com.openhis.elep.domain.prescription.dto.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.dto.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.dto.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.dto.ElepPlatformQueryResult;
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
@ConditionalOnBean(ElepPrescriptionOperations.class)
@ConditionalOnProperty(name = "openhis.modules.elep.enabled", havingValue = "true")
public class ElepPrescriptionAppService {

    private final ElepPrescriptionOperations elepPrescriptionOperations;

    /** 合约是否支持电子处方（Port / remote 2204 挂接）。 */
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
    public ElepRevokeResult revoke(ElepRevokeCommand command) {
        return elepPrescriptionOperations.revokeFromHisproxy(command);
    }

    /** 患者历史上传方列表。 */
    public ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query) {
        return elepPrescriptionOperations.listPatientHistory(query);
    }

    /** 当前就诊电子处方列表（含草稿/在途）。 */
    public List<ElepEncounterPrescriptionItemDto> listEncounterPrescriptions(ElepEncounterListQuery query) {
        return elepPrescriptionOperations.listEncounterPrescriptions(query);
    }

    /** 历史处方详情（含 snapshot）。 */
    public ElepHistoryDetailDto getHistoryDetail(Long prescriptionId) {
        return elepPrescriptionOperations.getHistoryDetail(prescriptionId);
    }

    /** 套用/续方 → 新 DRAFT。 */
    public ElepPrescriptionDraftDto cloneToEncounter(ElepCloneCommand command) {
        return elepPrescriptionOperations.cloneToEncounter(command);
    }

    /** 保存/更新草稿（DRAFT/FAILED）。 */
    public ElepPrescriptionDraftDto saveDraft(ElepSaveDraftCommand command) {
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
    public com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult revokePrescription(
            com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand command) {
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

    /** Port / 2204：按处方组 ID 查 hi_rxno。 */
    public Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId) {
        return elepPrescriptionOperations.findRxNoByPrescription(prescriptionGroupId);
    }

    /** Port / 2204：按处方号查 hi_rxno。 */
    public Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo) {
        return elepPrescriptionOperations.findRxNoByPrescriptionNo(prescriptionNo);
    }

    /** Port：套用/续方至新就诊。 */
    public ElepPrescriptionDraftDto cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        return elepPrescriptionOperations.cloneToNewEncounter(command);
    }
}
