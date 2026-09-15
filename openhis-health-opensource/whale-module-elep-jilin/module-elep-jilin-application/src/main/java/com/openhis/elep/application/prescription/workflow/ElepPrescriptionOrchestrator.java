/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.application.prescription.workflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.domain.prescription.command.ElepRevokeCommand;
import com.openhis.elep.domain.prescription.command.ElepUploadCommand;
import com.openhis.elep.domain.prescription.command.ElepQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepRevokeResult;
import com.openhis.elep.domain.prescription.result.ElepUploadResult;
import com.openhis.elep.domain.prescription.result.ElepQueryResult;
import com.openhis.elep.domain.prescription.access.ElepA0001InvokeAccess;
import com.openhis.elep.domain.prescription.access.ElepA0002InvokeAccess;
import com.openhis.elep.domain.prescription.access.ElepA0003InvokeAccess;
import com.openhis.elep.domain.prescription.access.ElepA0004InvokeAccess;
import com.openhis.elep.domain.prescription.access.ElepA0005InvokeAccess;
import com.openhis.elep.domain.prescription.access.ElepA0006InvokeAccess;
import com.openhis.elep.domain.prescription.result.ElepPlatformInvokeResult;
import com.openhis.elep.domain.config.ElepContractConfig;
import com.openhis.elep.domain.config.ElepContractConfigLoader;
import com.openhis.elep.application.prescription.pdf.ElepPdfPathSupport;
import com.openhis.elep.application.prescription.pdf.ElepPdfTemplateService;
import com.openhis.elep.gateway.support.ElepCurrentOperatorResolver;
import com.openhis.elep.domain.gateway.ElepOpCode;
import com.openhis.elep.domain.prescription.ElepLocalStatus;
import com.openhis.elep.application.prescription.support.ElepHistoryDetailMapper;
import com.openhis.elep.application.prescription.support.ElepPrescriptionCloneService;
import com.openhis.elep.application.prescription.support.ElepPrescriptionContinuationRules;
import com.openhis.elep.application.prescription.support.ElepUploadLogWriter;
import com.openhis.elep.domain.prescription.command.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.result.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.result.ElepPdfInput;
import com.openhis.elep.domain.prescription.command.ElepCloneCommand;
import com.openhis.elep.domain.prescription.command.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterPrescriptionItem;
import com.openhis.elep.domain.prescription.result.ElepHistoryDetail;
import com.openhis.elep.domain.prescription.command.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.command.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.result.ElepPlatformQueryResult;
import com.openhis.elep.domain.prescription.command.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.command.ElepSignCommand;
import com.alibaba.fastjson2.JSONArray;
import com.openhis.elep.domain.prescription.result.ElepSignResult;
import com.openhis.elep.domain.prescription.command.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.result.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.command.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.result.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.entity.YbElepPrescription;
import com.openhis.elep.domain.prescription.entity.YbElepPrescriptionSnapshot;
import com.openhis.elep.domain.prescription.access.ElepPrescriptionOperations;
import com.openhis.elep.domain.prescription.repository.YbElepPrescriptionRepository;
import com.openhis.elep.domain.prescription.repository.YbElepPrescriptionSnapshotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.elep.domain.prescription.access.ElepEncounterContextAccess;
import com.openhis.elep.application.prescription.support.ElepA0001BodyAssembler;
import com.openhis.elep.domain.prescription.command.ElepEncounterContextQuery;
import com.openhis.elep.domain.prescription.result.ElepEncounterContextResult;
import com.openhis.elep.domain.prescription.command.ElepReviewCommand;
import com.openhis.elep.domain.prescription.result.ElepReviewResult;
import com.openhis.elep.domain.prescription.result.ElepPharmacistReviewerResult;
import com.openhis.elep.domain.prescription.constants.ElepPrescriptionConstants;
import com.openhis.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.openhis.elep.domain.prescription.command.ElepMedResultQuery;
import com.openhis.elep.domain.prescription.result.ElepMedResultResult;
import com.openhis.elep.domain.prescription.result.ElepPatientHistoryItem;
import com.openhis.elep.domain.prescription.command.ElepPatientHistoryQuery;
import com.openhis.elep.domain.prescription.result.ElepPatientHistoryResult;
import com.openhis.elep.domain.prescription.result.ElepPrescriptionDraft;
import com.openhis.elep.domain.snapshot.ElepRxNoSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 电子处方多步编排（实现 {@link ElepPrescriptionOperations}；不对外 REST，前端经 {@code ElepPrescriptionAppService}）。
 * <p>上传主链：A0001 预核验 → 本地 PDF → A0002 签名 → A0003 上传；查询/撤销分别走 A0005/A0004/A0006。</p>
 * <p>写路径参与调用方 {@code *AppService} 由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 开启的 UoW；本类禁止 {@code @Transactional}。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.elep.deployment.mode", havingValue = "embedded", matchIfMissing = true)
public class ElepPrescriptionOrchestrator implements ElepPrescriptionOperations {

    private final ElepA0001InvokeAccess a0001Invoker;
    private final ElepA0002InvokeAccess a0002Invoker;
    private final ElepA0003InvokeAccess a0003Invoker;
    private final ElepA0004InvokeAccess a0004Invoker;
    private final ElepA0005InvokeAccess a0005Invoker;
    private final ElepA0006InvokeAccess a0006Invoker;
    private final YbElepPrescriptionRepository prescriptionRepository;
    private final YbElepPrescriptionSnapshotRepository snapshotRepository;
    private final ElepUploadLogWriter uploadLogWriter;
    private final ElepCurrentOperatorResolver operatorResolver;
    private final ElepPrescriptionCloneService cloneService;
    private final ElepEncounterContextAccess encounterContextAccess;
    private final ElepA0001BodyAssembler a0001BodyAssembler;

    @Override
    public boolean supportsContract(Long contractId) {
        return contractId != null;
    }

    /**
     * 装载当前就诊只读上下文（挂号 map(11) + 医师/科室医保码）。
     *
     * @param query 就诊、患者、契约
     * @return 上下文；不允许开方时 {@code allowed=false}
     */
    @Override
    public ElepEncounterContextResult loadEncounterContext(ElepEncounterContextQuery query) {
        return encounterContextAccess.load(query);
    }

    /**
     * 院内医保药师审方：写入 phar_*，不新增状态；未审不得 A0001。
     *
     * @param command 处方主键
     * @return 审方结果
     */
    @Override
    public ElepReviewResult review(ElepReviewCommand command) {
        // Gate：必须已有草稿主键
        if (command.getPrescriptionId() == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        // Pre：仅草稿/失败可审；已预核验后改内容须先回草稿
        YbElepPrescription prescription = loadActivePrescription(command.getPrescriptionId());
        if (!ElepLocalStatus.DRAFT.equals(prescription.getLocalStatus())
                && !ElepLocalStatus.FAILED.equals(prescription.getLocalStatus())) {
            throw new BusinessException("ELEP_STATUS_INVALID", "仅草稿或失败状态可审方");
        }
        ElepPharmacistReviewerResult pharmacist = encounterContextAccess.loadCurrentPharmacist();
        // Gate：审方人必须是当前登录药师且已维护国家医保码
        if (!pharmacist.isAllowed()) {
            throw new BusinessException("ELEP_REVIEWER_BLOCKED", pharmacist.getBlockReason());
        }
        // Cmd：审方信息只写电子处方表，不写 HIS 发药审方
        prescription.setPharCode(pharmacist.getPharCode());
        prescription.setPharName(pharmacist.getPharName());
        prescription.setPharDeptCode(pharmacist.getPharDeptCode());
        prescription.setPharDeptName(pharmacist.getPharDeptName());
        prescription.setPharProfttlCodg(pharmacist.getPharProfttlCodg());
        prescription.setPharPracCertNo(pharmacist.getPharPracCertNo());
        prescription.setReviewedAt(Instant.now());
        prescription.setReviewedBy(operatorId);
        if (ElepLocalStatus.FAILED.equals(prescription.getLocalStatus())) {
            prescription.setLocalStatus(ElepLocalStatus.DRAFT);
        }
        prescription.setUpdatedAt(LocalDateTime.now());
        prescription.setUpdatedBy(operatorId);
        prescriptionRepository.save(prescription);
        return new ElepReviewResult()
                .setSuccess(true)
                .setPrescriptionId(prescription.getId())
                .setPharCode(pharmacist.getPharCode())
                .setPharName(pharmacist.getPharName())
                .setMessage("审方通过");
    }

    @Override
    public ElepPreCheckResult preCheck(ElepPreCheckCommand command) {
        validateBusNo(command.getContractBusNo());
        // Gate：必须先保存草稿；禁止前端直传完整 A0001 跳过审方
        if (command.getPrescriptionId() == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "请先保存草稿并完成审方后再预核验");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        YbElepPrescription prescription = loadActivePrescription(command.getPrescriptionId());
        if (!ElepLocalStatus.DRAFT.equals(prescription.getLocalStatus())
                && !ElepLocalStatus.FAILED.equals(prescription.getLocalStatus())) {
            throw new BusinessException("ELEP_STATUS_INVALID", "仅草稿或失败状态可预核验");
        }
        // Gate：3-03 上传门禁，未审方不得出站 A0001
        if (isEmpty(prescription.getPharCode()) || prescription.getReviewedAt() == null) {
            throw new BusinessException("ELEP_REVIEW_REQUIRED", "请先由院内医保药师审方后再预核验");
        }
        // Pre：刷新挂号 map(11) 与定点机构，慢病仍只用挂号号
        ElepEncounterContextResult context = encounterContextAccess.load(new ElepEncounterContextQuery()
                .setEncounterId(prescription.getEncounterId())
                .setPatientId(prescription.getPatientId())
                .setContractBusNo(command.getContractBusNo()));
        if (!context.isAllowed()) {
            throw new BusinessException("ELEP_CONTEXT_BLOCKED", context.getBlockReason());
        }
        YbElepPrescriptionSnapshot snapshot = loadSnapshot(prescription.getId());
        if (isEmpty(snapshot.getUploadRequestJson())) {
            throw new BusinessException("ELEP_SNAPSHOT_REQUIRED", "缺少草稿快照，请先保存");
        }
        JSONObject draftBody = JSONObject.parseObject(snapshot.getUploadRequestJson());
        JSONObject body = a0001BodyAssembler.refreshForPreCheck(prescription, context, draftBody);
        ElepPlatformInvokeResult response = a0001Invoker.invoke(command.getContractBusNo(), body);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0001,
                body,
                response,
                success,
                failLogMessage(success, response),
                operatorId);
        if (!success) {
            prescription.setLocalStatus(ElepLocalStatus.FAILED);
            prescription.setUpdatedAt(LocalDateTime.now());
            prescription.setUpdatedBy(operatorId);
            prescriptionRepository.save(prescription);
            return new ElepPreCheckResult().setSuccess(false).setMessage(response.getMessage());
        }
        JSONObject output = JSON.parseObject(response.getBodyJson());
        String rxTraceCode = jsonString(output, "rxTraceCode");
        String hiRxno = jsonString(output, "hiRxno");
        prescription.setRxTraceCode(rxTraceCode);
        if (isNotEmpty(hiRxno)) {
            prescription.setHiRxno(hiRxno);
        }
        prescription.setLocalStatus(ElepLocalStatus.PRE_CHECKED);
        prescription.setUpdatedAt(LocalDateTime.now());
        prescription.setUpdatedBy(operatorId);
        prescriptionRepository.save(prescription);
        saveSnapshot(prescription.getId(), body, response.getBodyJson(), null, null, operatorId);
        String pdfFilePath = null;
        if (command.getPdfInput() != null) {
            pdfFilePath = doGeneratePdf(
                    command.getContractBusNo(),
                    prescription,
                    command.getPdfInput(),
                    operatorId);
        }
        return new ElepPreCheckResult()
                .setSuccess(true)
                .setPrescriptionId(prescription.getId())
                .setHospRxno(prescription.getHospRxno())
                .setRxTraceCode(rxTraceCode)
                .setHiRxno(hiRxno)
                .setPdfFilePath(pdfFilePath)
                .setMessage("预核验成功");
    }

    @Override
    public ElepFileUploadResult uploadFile(ElepFileUploadCommand command) {
        validateBusNo(command.getContractBusNo());
        if (command.getPrescriptionId() == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        YbElepPrescription prescription = prescriptionRepository.findById(command.getPrescriptionId())
                .filter(rx -> !Boolean.TRUE.equals(rx.getIsDeleted()))
                .orElseThrow(() -> new BusinessException("ELEP_RX_NOT_FOUND", "电子处方不存在"));
        if (!ElepLocalStatus.SIGNED.equals(prescription.getLocalStatus())) {
            throw new BusinessException("ELEP_STATUS_INVALID", "请先完成 A0002 签名后再上传");
        }
        YbElepPrescriptionSnapshot snapshot = loadSnapshot(prescription.getId());
        ElepContractConfig config = requireConfig(command.getContractBusNo());
        JSONObject body = buildUploadBody(command, config, prescription, snapshot);
        ElepPlatformInvokeResult response = a0003Invoker.invoke(command.getContractBusNo(), body);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0003,
                body,
                response,
                success,
                failLogMessage(success, response),
                operatorId);
        if (!success) {
            prescription.setLocalStatus(ElepLocalStatus.FAILED);
            prescription.setUpdatedAt(LocalDateTime.now());
            prescription.setUpdatedBy(operatorId);
            prescriptionRepository.save(prescription);
            return new ElepFileUploadResult().setSuccess(false).setMessage(response.getMessage());
        }
        JSONObject output = JSON.parseObject(response.getBodyJson());
        String hiRxno = jsonString(output, "hiRxno");
        prescription.setHiRxno(hiRxno);
        if (output != null) {
            prescription.setRxStasCodg(output.getString("rxStasCodg"));
        }
        enrichPrescriptionSummary(prescription, body);
        prescription.setLocalStatus(ElepLocalStatus.UPLOADED);
        prescription.setUpdatedAt(LocalDateTime.now());
        prescription.setUpdatedBy(operatorId);
        prescriptionRepository.save(prescription);
        updateSnapshotConfirmJson(prescription.getId(), response.getBodyJson(), operatorId);
        return new ElepFileUploadResult()
                .setSuccess(true)
                .setPrescriptionId(prescription.getId())
                .setHiRxno(hiRxno)
                .setRxStasCodg(prescription.getRxStasCodg())
                .setMessage("上传成功");
    }

    @Override
    public ElepPdfGenerateResult generatePdf(ElepPdfGenerateCommand command) {
        validateBusNo(command.getContractBusNo());
        if (command.getPrescriptionId() == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
        }
        if (command.getPdfInput() == null) {
            throw new BusinessException("ELEP_PDF_INPUT_REQUIRED", "pdfInput 不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        YbElepPrescription prescription = loadActivePrescription(command.getPrescriptionId());
        requireStatus(prescription, ElepLocalStatus.PRE_CHECKED);
        String pdfFilePath = doGeneratePdf(
                command.getContractBusNo(),
                prescription,
                command.getPdfInput(),
                operatorId);
        return new ElepPdfGenerateResult()
                .setSuccess(true)
                .setPrescriptionId(prescription.getId())
                .setPdfFilePath(pdfFilePath)
                .setMessage("PDF 生成成功");
    }

    @Override
    public ElepSignResult sign(ElepSignCommand command) {
        validateBusNo(command.getContractBusNo());
        if (command.getPrescriptionId() == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        YbElepPrescription prescription = loadActivePrescription(command.getPrescriptionId());
        requireStatus(prescription, ElepLocalStatus.PRE_CHECKED);
        YbElepPrescriptionSnapshot snapshot = loadSnapshot(prescription.getId());
        if (isEmpty(snapshot.getPdfFilePath())) {
            throw new BusinessException("ELEP_PDF_REQUIRED", "请先生成 PDF 后再签名");
        }
        if (isEmpty(snapshot.getUploadRequestJson())) {
            throw new BusinessException("ELEP_SNAPSHOT_REQUIRED", "缺少预核验快照");
        }
        ElepContractConfig config = requireConfig(command.getContractBusNo());
        String pdfBase64 = ElepPdfTemplateService.readPdfBase64(snapshot.getPdfFilePath());
        if (isEmpty(pdfBase64)) {
            throw new BusinessException("ELEP_PDF_READ_FAILED", "读取 PDF 文件失败");
        }
        JSONObject body = buildSignBody(command, config, snapshot, pdfBase64);
        ElepPlatformInvokeResult response = a0002Invoker.invoke(command.getContractBusNo(), body);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0002,
                body,
                response,
                success,
                failLogMessage(success, response),
                operatorId);
        if (!success) {
            prescription.setLocalStatus(ElepLocalStatus.FAILED);
            prescription.setUpdatedAt(LocalDateTime.now());
            prescription.setUpdatedBy(operatorId);
            prescriptionRepository.save(prescription);
            return new ElepSignResult().setSuccess(false).setMessage(response.getMessage());
        }
        JSONObject output = JSON.parseObject(response.getBodyJson());
        String rxFile = jsonString(output, "rxFile");
        String signDigest = jsonString(output, "signDigest");
        ElepPdfTemplateService.writeSignedPdf(rxFile, snapshot.getPdfFilePath());
        snapshot.setSignResponseJson(response.getBodyJson());
        snapshot.setUpdatedAt(LocalDateTime.now());
        snapshot.setUpdatedBy(operatorId);
        snapshotRepository.save(snapshot);
        prescription.setLocalStatus(ElepLocalStatus.SIGNED);
        prescription.setUpdatedAt(LocalDateTime.now());
        prescription.setUpdatedBy(operatorId);
        prescriptionRepository.save(prescription);
        return new ElepSignResult()
                .setSuccess(true)
                .setPrescriptionId(prescription.getId())
                .setSignDigest(signDigest)
                .setPdfFilePath(snapshot.getPdfFilePath())
                .setMessage("签名成功");
    }

    @Override
    public com.openhis.elep.domain.prescription.result.ElepHisproxyRevokeResult revokeFromHisproxy(
            com.openhis.elep.domain.prescription.command.ElepHisproxyRevokeCommand command) {
        ElepRevokeResult portResult = revoke(
                new ElepRevokeCommand()
                .setContractBusNo(command.getContractBusNo())
                .setPrescriptionId(command.getPrescriptionId())
                .setPlatformBody(command.getPlatformBody()));
        return new com.openhis.elep.domain.prescription.result.ElepHisproxyRevokeResult()
                .setSuccess(portResult.isSuccess())
                .setMessage(portResult.getMessage())
                .setPrescriptionId(command.getPrescriptionId());
    }

    @Override
    public ElepUploadResult upload(ElepUploadCommand command) {
        throw new BusinessException("ELEP_USE_BRIDGE", "请使用 preCheck → generatePdf → sign → uploadFile 分步上传");
    }

    @Override
    public com.openhis.elep.domain.prescription.result.ElepRevokeResult revoke(
            ElepRevokeCommand command) {
        if (command.getPrescriptionId() == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        YbElepPrescription prescription = loadActivePrescription(command.getPrescriptionId());
        if (ElepLocalStatus.REVOKED.equals(prescription.getLocalStatus())) {
            throw new BusinessException("ELEP_ALREADY_REVOKED", "电子处方已撤销");
        }
        String contractBusNo = firstNonEmpty(
                command.getContractBusNo(), prescription.getContractBusNo());
        validateBusNo(contractBusNo);
        ElepContractConfig config = requireConfig(contractBusNo);
        JSONObject body = buildRevokeBody(command, config, prescription);
        ElepPlatformInvokeResult response = a0004Invoker.invoke(contractBusNo, body);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0004,
                body,
                response,
                success,
                failLogMessage(success, response),
                operatorId);
        if (!success) {
            return new com.openhis.elep.domain.prescription.result.ElepRevokeResult()
                    .setSuccess(false).setMessage(response.getMessage());
        }
        prescription.setLocalStatus(ElepLocalStatus.REVOKED);
        prescription.setUpdatedAt(LocalDateTime.now());
        prescription.setUpdatedBy(operatorId);
        prescriptionRepository.save(prescription);
        return new com.openhis.elep.domain.prescription.result.ElepRevokeResult()
                .setSuccess(true).setMessage("撤销成功");
    }

    @Override
    public ElepQueryResult query(ElepQueryCommand command) {
        YbElepPrescription prescription = resolvePrescriptionForQuery(command);
        String contractBusNo = firstNonEmpty(command.getContractBusNo(), prescription.getContractBusNo());
        validateBusNo(contractBusNo);
        ElepContractConfig config = requireConfig(contractBusNo);
        JSONObject body = buildQueryBody(command, config, prescription);
        Long operatorId = operatorResolver.requireOperatorId();
        ElepPlatformInvokeResult response = a0005Invoker.invoke(contractBusNo, body);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0005,
                body,
                response,
                success,
                failLogMessage(success, response),
                operatorId);
        if (!success) {
            return new ElepQueryResult().setSuccess(false).setMessage(response.getMessage());
        }
        JSONObject output = JSON.parseObject(response.getBodyJson());
        boolean refreshLocal = command.getRefreshLocal() == null || command.getRefreshLocal();
        if (refreshLocal) {
            applyPlatformQueryFields(prescription, output, operatorId);
        }
        return new ElepQueryResult()
                .setSuccess(true)
                .setRxStasCodg(jsonString(output, "rxStasCodg"))
                .setRxUsedStasCodg(jsonString(output, "rxUsedStasCodg"))
                .setQueryJson(response.getBodyJson())
                .setMessage("查询成功");
    }

    @Override
    public ElepPlatformQueryResult queryPlatform(ElepPlatformQueryCommand command) {
        ElepQueryResult portResult = query(new ElepQueryCommand()
                .setPrescriptionId(command.getPrescriptionId())
                .setContractId(command.getContractId())
                .setContractBusNo(command.getContractBusNo())
                .setHiRxno(command.getHiRxno())
                .setPlatformBody(command.getPlatformBody())
                .setRefreshLocal(command.getRefreshLocal()));
        return new ElepPlatformQueryResult()
                .setSuccess(portResult.isSuccess())
                .setMessage(portResult.getMessage())
                .setRxStasCodg(portResult.getRxStasCodg())
                .setRxUsedStasCodg(portResult.getRxUsedStasCodg())
                .setPlatformJson(portResult.getQueryJson());
    }

    @Override
    public ElepMedResultResult queryMedResult(ElepMedResultQuery query) {
        if (isEmpty(query.getHiRxno())) {
            throw new BusinessException("ELEP_HI_RXNO_REQUIRED", "hiRxno 不能为空");
        }
        String contractBusNo = query.getContractBusNo();
        if (isEmpty(contractBusNo)) {
            throw new BusinessException("ELEP_BUSNO_REQUIRED", "contractBusNo 不能为空");
        }
        validateBusNo(contractBusNo);
        ElepContractConfig config = requireConfig(contractBusNo);
        JSONObject body;
        if (query.getPlatformBody() != null && !query.getPlatformBody().isEmpty()) {
            body = new JSONObject(query.getPlatformBody());
        } else {
            body = buildMedResultBody(query.getHiRxno(), config);
        }
        ElepPlatformInvokeResult response = a0006Invoker.invoke(contractBusNo, body);
        if (!response.isSuccess()) {
            return new ElepMedResultResult().setSuccess(false).setMessage(response.getMessage());
        }
        return new ElepMedResultResult()
                .setSuccess(true)
                .setMedResultJson(response.getBodyJson())
                .setMessage("查询成功");
    }

    @Override
    public ElepPlatformMedResultResult queryMedResultPlatform(ElepPlatformMedResultQueryCommand command) {
        ElepMedResultResult portResult = queryMedResult(new ElepMedResultQuery()
                .setContractId(command.getContractId())
                .setContractBusNo(command.getContractBusNo())
                .setHiRxno(command.getHiRxno())
                .setPlatformBody(command.getPlatformBody()));
        return new ElepPlatformMedResultResult()
                .setSuccess(portResult.isSuccess())
                .setMessage(portResult.getMessage())
                .setMedResultJson(portResult.getMedResultJson());
    }

    @Override
    public Optional<ElepRxNoSnapshot> findRxNoByPrescription(Long prescriptionGroupId) {
        if (prescriptionGroupId == null) {
            return Optional.empty();
        }
        return prescriptionRepository.findById(prescriptionGroupId)
                .filter(rx -> !Boolean.TRUE.equals(rx.getIsDeleted()))
                .filter(rx -> ElepLocalStatus.UPLOADED.equals(rx.getLocalStatus()))
                .map(this::toRxNoSnapshot);
    }

    @Override
    public Optional<ElepRxNoSnapshot> findRxNoByPrescriptionNo(String prescriptionNo) {
        if (isEmpty(prescriptionNo)) {
            return Optional.empty();
        }
        return prescriptionRepository
                .findByPrescriptionNoAndLocalStatusAndIsDeletedFalse(
                        prescriptionNo.trim(), ElepLocalStatus.UPLOADED)
                .map(this::toRxNoSnapshot);
    }

    @Override
    public List<ElepEncounterPrescriptionItem> listEncounterPrescriptions(ElepEncounterListQuery query) {
        if (query.getEncounterId() == null) {
            return List.of();
        }
        List<ElepEncounterPrescriptionItem> items = new ArrayList<>();
        for (YbElepPrescription rx : prescriptionRepository
                .findByEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(query.getEncounterId())) {
            items.add(new ElepEncounterPrescriptionItem()
                    .setPrescriptionId(rx.getId())
                    .setHospRxno(rx.getHospRxno())
                    .setHiRxno(rx.getHiRxno())
                    .setPrescriptionNo(rx.getPrescriptionNo())
                    .setLocalStatus(rx.getLocalStatus())
                    .setMainDiagName(rx.getMainDiagName())
                    .setDrugSummary(rx.getDrugSummary())
                    .setPrscTime(rx.getPrscTime())
                    .setPrscDrName(rx.getPrscDrName()));
        }
        return items;
    }

    /**
     * 已上传方映射为收费挂接用的处方号快照。
     *
     * @param rx 电子处方
     * @return hiRxno 快照
     */
    private ElepRxNoSnapshot toRxNoSnapshot(YbElepPrescription rx) {
        return new ElepRxNoSnapshot()
                .setHiRxno(rx.getHiRxno())
                .setHospRxno(rx.getHospRxno())
                .setLocalStatus(rx.getLocalStatus());
    }

    @Override
    public ElepPatientHistoryResult listPatientHistory(ElepPatientHistoryQuery query) {
        if (query.getPatientId() == null) {
            return new ElepPatientHistoryResult()
                    .setPage(query.getPage())
                    .setPageSize(query.getPageSize());
        }
        int page = Math.max(query.getPage(), 0);
        int pageSize = query.getPageSize();
        if (pageSize <= 0) {
            pageSize = 20;
        }
        Page<YbElepPrescription> pageResult = prescriptionRepository
                .findByPatientIdAndLocalStatusAndIsDeletedFalseOrderByPrscTimeDesc(
                        query.getPatientId(),
                        ElepLocalStatus.UPLOADED,
                        PageRequest.of(page, pageSize));
        ElepPatientHistoryResult result = new ElepPatientHistoryResult()
                .setTotal(pageResult.getTotalElements())
                .setPage(page)
                .setPageSize(pageSize);
        for (YbElepPrescription rx : pageResult.getContent()) {
            if (query.getCurrentEncounterId() != null
                    && query.getCurrentEncounterId().equals(rx.getEncounterId())) {
                continue;
            }
            result.getItems().add(new ElepPatientHistoryItem()
                    .setPrescriptionId(rx.getId())
                    .setHiRxno(rx.getHiRxno())
                    .setHospRxno(rx.getHospRxno())
                    .setLocalStatus(rx.getLocalStatus())
                    .setPrscDrName(rx.getPrscDrName())
                    .setPrscDeptName(rx.getPrscDeptName())
                    .setMainDiagName(rx.getMainDiagName())
                    .setDrugSummary(rx.getDrugSummary())
                    .setPrscTime(rx.getPrscTime())
                    .setCanContinuationRx(ElepPrescriptionContinuationRules.canContinuation(rx)));
        }
        return result;
    }

    @Override
    public ElepHistoryDetail getHistoryDetail(Long prescriptionId) {
        if (prescriptionId == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
        }
        YbElepPrescription rx = loadActivePrescription(prescriptionId);
        YbElepPrescriptionSnapshot snapshot = snapshotRepository.findById(prescriptionId).orElse(null);
        return ElepHistoryDetailMapper.toDetail(rx, snapshot);
    }

    @Override
    public ElepPrescriptionDraft cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        return cloneService.clone(command, operatorResolver.requireOperatorId());
    }

    @Override
    public ElepPrescriptionDraft cloneToEncounter(ElepCloneCommand command) {
        return cloneService.clone(new ElepClonePrescriptionCommand()
                .setSourcePrescriptionId(command.getSourcePrescriptionId())
                .setTargetEncounterId(command.getTargetEncounterId())
                .setPatientId(command.getPatientId())
                .setContractId(command.getContractId())
                .setContractBusNo(command.getContractBusNo())
                .setContinuationRx(command.getContinuationRx())
                .setCopyDiagnosis(command.getCopyDiagnosis())
                .setNewHospRxno(command.getNewHospRxno())
                .setMdtrtinfoPatch(command.getMdtrtinfoPatch()),
                operatorResolver.requireOperatorId());
    }

    @Override
    public ElepPrescriptionDraft saveDraft(ElepSaveDraftCommand command) {
        return cloneService.saveDraft(command, operatorResolver.requireOperatorId());
    }

    /**
     * 写入或更新预核验快照（请求/响应 JSON）。
     *
     * @param prescriptionId 处方主键
     * @param request        出站请求
     * @param responseJson   平台响应
     * @param signJson       签名响应，可空
     * @param confirmJson    上传确认，可空
     * @param operatorId     操作人
     */
    private void saveSnapshot(
            Long prescriptionId,
            JSONObject request,
            String responseJson,
            String signJson,
            String confirmJson,
            Long operatorId) {
        YbElepPrescriptionSnapshot snapshot = snapshotRepository.findById(prescriptionId)
                .orElseGet(YbElepPrescriptionSnapshot::new);
        snapshot.setId(prescriptionId);
        snapshot.setTenantId(TenantUtils.getCurrentTenantId());
        snapshot.setUploadRequestJson(request.toJSONString());
        snapshot.setUploadResponseJson(responseJson);
        if (signJson != null) {
            snapshot.setSignResponseJson(signJson);
        }
        if (confirmJson != null) {
            snapshot.setUploadConfirmJson(confirmJson);
        }
        if (snapshot.getCreatedAt() == null) {
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshot.setCreatedBy(operatorId);
        }
        snapshot.setUpdatedAt(LocalDateTime.now());
        snapshot.setUpdatedBy(operatorId);
        snapshot.setIsDeleted(false);
        snapshotRepository.save(snapshot);
    }

    /**
     * 回写 A0003 确认 JSON。
     *
     * @param prescriptionId 处方主键
     * @param confirmJson    平台确认
     * @param operatorId     操作人
     */
    private void updateSnapshotConfirmJson(Long prescriptionId, String confirmJson, Long operatorId) {
        snapshotRepository.findById(prescriptionId).ifPresent(snapshot -> {
            snapshot.setUploadConfirmJson(confirmJson);
            snapshot.setUpdatedAt(LocalDateTime.now());
            snapshot.setUpdatedBy(operatorId);
            snapshotRepository.save(snapshot);
        });
    }

    /**
     * 契约 bus_no 不能为空（密钥只从 elep-contract 取）。
     *
     * @param contractBusNo 契约编号
     */
    private static void validateBusNo(String contractBusNo) {
        if (isEmpty(contractBusNo)) {
            throw new BusinessException("ELEP_BUSNO_REQUIRED", "contractBusNo 不能为空");
        }
    }

    /**
     * 加载未删除的电子处方。
     *
     * @param prescriptionId 主键
     * @return 主表
     */
    private YbElepPrescription loadActivePrescription(Long prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .filter(rx -> !Boolean.TRUE.equals(rx.getIsDeleted()))
                .orElseThrow(() -> new BusinessException("ELEP_RX_NOT_FOUND", "电子处方不存在"));
    }

    /**
     * 加载处方快照。
     *
     * @param prescriptionId 主键
     * @return 快照
     */
    private YbElepPrescriptionSnapshot loadSnapshot(Long prescriptionId) {
        return snapshotRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException("ELEP_SNAPSHOT_NOT_FOUND", "电子处方快照不存在"));
    }

    /**
     * 校验本地状态机，防止跳步签名/上传。
     *
     * @param prescription   主表
     * @param expectedStatus 期望状态 code
     */
    private static void requireStatus(YbElepPrescription prescription, String expectedStatus) {
        if (!expectedStatus.equals(prescription.getLocalStatus())) {
            throw new BusinessException(
                    "ELEP_STATUS_INVALID",
                    "当前状态 " + prescription.getLocalStatus() + " 不允许此操作，期望 " + expectedStatus);
        }
    }

    /**
     * 读取启用中的 elep-contract 段。
     *
     * @param contractBusNo 契约 bus_no
     * @return 配置
     */
    private ElepContractConfig requireConfig(String contractBusNo) {
        ElepContractConfig config = ElepContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null || !config.isEnabled()) {
            throw new BusinessException("ELEP_CONFIG_NOT_FOUND", "未找到电子处方契约配置: " + contractBusNo);
        }
        return config;
    }

    /**
     * 按模板生成本地 PDF 并写入快照路径。
     *
     * @param contractBusNo 契约
     * @param prescription  主表
     * @param pdfInput      填模数据
     * @param operatorId    操作人
     * @return 输出文件路径
     */
    private String doGeneratePdf(
            String contractBusNo,
            YbElepPrescription prescription,
            ElepPdfInput pdfInput,
            Long operatorId) {
        ElepContractConfig config = requireConfig(contractBusNo);
        if (isEmpty(config.getTemplatePath())) {
            throw new BusinessException("ELEP_TEMPLATE_REQUIRED", "template_path 未配置");
        }
        if (isEmpty(config.getOutputPath())) {
            throw new BusinessException("ELEP_OUTPUT_REQUIRED", "output_path 未配置");
        }
        if (isEmpty(pdfInput.getRxTraceCode())) {
            pdfInput.setRxTraceCode(prescription.getRxTraceCode());
        }
        if (isEmpty(pdfInput.getPrescriptionNo())) {
            pdfInput.setPrescriptionNo(prescription.getHospRxno());
        }
        String outputPath = ElepPdfPathSupport.buildOutputFilePath(config, prescription.getHospRxno());
        ElepPdfTemplateService.fillPdfTemplate(
                config.getTemplatePath(),
                outputPath,
                config.getHospitalSealPath(),
                pdfInput);
        YbElepPrescriptionSnapshot snapshot = snapshotRepository.findById(prescription.getId())
                .orElseGet(YbElepPrescriptionSnapshot::new);
        snapshot.setId(prescription.getId());
        snapshot.setTenantId(TenantUtils.getCurrentTenantId());
        snapshot.setPdfFilePath(outputPath);
        if (snapshot.getCreatedAt() == null) {
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshot.setCreatedBy(operatorId);
        }
        snapshot.setUpdatedAt(LocalDateTime.now());
        snapshot.setUpdatedBy(operatorId);
        snapshot.setIsDeleted(false);
        snapshotRepository.save(snapshot);
        return outputPath;
    }

    /**
     * 组装 A0002 签名体；密钥不在此出现。
     *
     * @param command  签名命令
     * @param config   契约
     * @param snapshot 含预核验 JSON 与 PDF
     * @param pdfBase64 PDF Base64
     * @return 出站 JSON
     */
    private JSONObject buildSignBody(
            ElepSignCommand command,
            ElepContractConfig config,
            YbElepPrescriptionSnapshot snapshot,
            String pdfBase64) {
        if (command.getPlatformBody() != null && !command.getPlatformBody().isEmpty()) {
            JSONObject body = new JSONObject(command.getPlatformBody());
            body.remove("prescriptionNo");
            return body;
        }
        String originalValue = Base64.getEncoder().encodeToString(
                snapshot.getUploadRequestJson().getBytes(StandardCharsets.UTF_8));
        JSONObject body = new JSONObject();
        body.put("fixmedinsCode", config.getFixmedinsCode());
        body.put("originalValue", originalValue);
        body.put("originalRxFile", pdfBase64);
        return body;
    }

    /**
     * 组装 A0003 上传体，带上审方药师 phar_*。
     *
     * @param command      上传命令
     * @param config       契约
     * @param prescription 主表
     * @param snapshot     快照
     * @return 出站 JSON
     */
    private JSONObject buildUploadBody(
            ElepFileUploadCommand command,
            ElepContractConfig config,
            YbElepPrescription prescription,
            YbElepPrescriptionSnapshot snapshot) {
        if (command.getPlatformBody() != null && !command.getPlatformBody().isEmpty()) {
            return new JSONObject(command.getPlatformBody());
        }
        if (isEmpty(snapshot.getUploadRequestJson())) {
            throw new BusinessException("ELEP_SNAPSHOT_REQUIRED", "缺少预核验快照");
        }
        if (isEmpty(snapshot.getPdfFilePath())) {
            throw new BusinessException("ELEP_PDF_REQUIRED", "缺少签名 PDF");
        }
        String pdfBase64 = ElepPdfTemplateService.readPdfBase64(snapshot.getPdfFilePath());
        if (isEmpty(pdfBase64)) {
            throw new BusinessException("ELEP_PDF_READ_FAILED", "读取 PDF 文件失败");
        }
        JSONObject body = JSON.parseObject(snapshot.getUploadRequestJson());
        if (body == null) {
            body = new JSONObject();
        }
        body.put("rxTraceCode", prescription.getRxTraceCode());
        if (isNotEmpty(prescription.getHiRxno())) {
            body.put("hiRxno", prescription.getHiRxno());
        }
        body.put("fixmedinsCode", config.getFixmedinsCode());
        if (isNotEmpty(config.getFixmedinsName())) {
            body.put("fixmedinsName", config.getFixmedinsName());
        }
        body.put("rxFile", pdfBase64);
        putIfNotEmpty(body, "pharDeptName", prescription.getPharDeptName());
        putIfNotEmpty(body, "pharDeptCode", prescription.getPharDeptCode());
        putIfNotEmpty(body, "pharCode", prescription.getPharCode());
        putIfNotEmpty(body, "pharName", prescription.getPharName());
        putIfNotEmpty(body, "pharProfttlCodg", prescription.getPharProfttlCodg());
        putIfNotEmpty(body, "pharPracCertNo", prescription.getPharPracCertNo());
        if (isNotEmpty(snapshot.getSignResponseJson())) {
            JSONObject signOutput = JSON.parseObject(snapshot.getSignResponseJson());
            if (signOutput != null && isNotEmpty(signOutput.getString("signDigest"))) {
                body.put("signDigest", signOutput.getString("signDigest"));
            }
        }
        return body;
    }

    /**
     * 组装 A0004 撤销体。
     *
     * @param command      撤销命令
     * @param config       契约
     * @param prescription 主表
     * @return 出站 JSON
     */
    private JSONObject buildRevokeBody(
            com.openhis.elep.domain.prescription.command.ElepRevokeCommand command,
            ElepContractConfig config,
            YbElepPrescription prescription) {
        if (command.getPlatformBody() != null && !command.getPlatformBody().isEmpty()) {
            return new JSONObject(command.getPlatformBody());
        }
        if (isEmpty(command.getDrCode()) || isEmpty(command.getUndoDrName())) {
            throw new BusinessException(
                    "ELEP_REVOKE_BODY_REQUIRED",
                    "撤销需提供 platformBody，或 drCode + undoDrName");
        }
        String hiRxno = firstNonEmpty(command.getHiRxno(), prescription.getHiRxno());
        if (isEmpty(hiRxno)) {
            throw new BusinessException("ELEP_HI_RXNO_REQUIRED", "hiRxno 不能为空");
        }
        JSONObject body = new JSONObject();
        body.put("hiRxno", hiRxno);
        body.put("fixmedinsCode", config.getFixmedinsCode());
        body.put("drCode", command.getDrCode());
        body.put("undoDrName", command.getUndoDrName());
        if (isNotEmpty(command.getRevokeReason())) {
            body.put("undoRea", command.getRevokeReason());
        }
        return body;
    }

    /**
     * 按处方主键定位查询对象。
     *
     * @param command 查询命令
     * @return 主表
     */
    private YbElepPrescription resolvePrescriptionForQuery(ElepQueryCommand command) {
        if (command.getPrescriptionId() != null) {
            return loadActivePrescription(command.getPrescriptionId());
        }
        if (isNotEmpty(command.getHiRxno())) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "按 hiRxno 查询需提供 prescriptionId");
        }
        throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
    }

    /**
     * 组装 A0005 查询体。
     *
     * @param command      查询命令
     * @param config       契约
     * @param prescription 主表
     * @return 出站 JSON
     */
    private JSONObject buildQueryBody(
            ElepQueryCommand command,
            ElepContractConfig config,
            YbElepPrescription prescription) {
        if (command.getPlatformBody() != null && !command.getPlatformBody().isEmpty()) {
            return new JSONObject(command.getPlatformBody());
        }
        String hiRxno = firstNonEmpty(command.getHiRxno(), prescription.getHiRxno());
        if (isEmpty(hiRxno)) {
            throw new BusinessException("ELEP_HI_RXNO_REQUIRED", "hiRxno 不能为空");
        }
        JSONObject body = new JSONObject();
        body.put("fixmedinsCode", config.getFixmedinsCode());
        body.put("hiRxno", hiRxno);
        return body;
    }

    /**
     * 组装 A0006 取药结果查询体（本期前端不做）。
     *
     * @param hiRxno 医保处方号
     * @param config 契约
     * @return 出站 JSON
     */
    private static JSONObject buildMedResultBody(String hiRxno, ElepContractConfig config) {
        JSONObject body = new JSONObject();
        body.put("fixmedinsCode", config.getFixmedinsCode());
        body.put("hiRxno", hiRxno);
        return body;
    }

    /**
     * 把 A0005 平台状态码回写本地，不改 HIS。
     *
     * @param prescription 主表
     * @param output       平台输出
     * @param operatorId   操作人
     */
    private void applyPlatformQueryFields(YbElepPrescription prescription, JSONObject output, Long operatorId) {
        if (output == null) {
            return;
        }
        prescription.setRxStasCodg(output.getString("rxStasCodg"));
        prescription.setRxUsedStasCodg(output.getString("rxUsedStasCodg"));
        prescription.setReptFlag(output.getString("reptFlag"));
        if (output.get("maxReptCnt") != null) {
            prescription.setMaxReptCnt(output.getInteger("maxReptCnt"));
        }
        if (output.get("reptdCnt") != null) {
            prescription.setReptdCnt(output.getInteger("reptdCnt"));
        }
        prescription.setRxTypeCode(output.getString("rxTypeCode"));
        Instant valiEnd = parseInstant(output.getString("valiEndTime"));
        if (valiEnd != null) {
            prescription.setValiEndTime(valiEnd);
        }
        prescription.setPlatformSyncedAt(Instant.now());
        prescription.setUpdatedAt(LocalDateTime.now());
        prescription.setUpdatedBy(operatorId);
        prescriptionRepository.save(prescription);
    }

    /**
     * 解析平台时间串为 Instant。
     *
     * @param text ISO 或 yyyy-MM-dd HH:mm:ss
     * @return 瞬时；无法解析则空
     */
    private static Instant parseInstant(String text) {
        if (isEmpty(text)) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return LocalDateTime.parse(text, ElepPrescriptionConstants.PRSC_TIME_FORMATTER)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * 上传成功后用报文刷新药品摘要与主诊断。
     *
     * @param prescription 主表
     * @param body         A0003 报文
     */
    private static void enrichPrescriptionSummary(YbElepPrescription prescription, JSONObject body) {
        JSONArray drugLines = body.getJSONArray("rxdrugdetail");
        if (drugLines != null) {
            prescription.setLineCount(drugLines.size());
            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < drugLines.size(); i++) {
                JSONObject line = drugLines.getJSONObject(i);
                if (line == null) {
                    continue;
                }
                if (summary.length() > 0) {
                    summary.append("；");
                }
                String drugName = line.getString("drugName");
                if (drugName == null) {
                    drugName = "";
                }
                summary.append(drugName);
                if (line.get("drugCnt") != null) {
                    summary.append("×").append(line.getString("drugCnt"));
                }
            }
            if (summary.length() > 0) {
                prescription.setDrugSummary(summary.toString());
            }
        }
        JSONObject diseinfo = body.getJSONObject("diseinfo");
        if (diseinfo != null) {
            prescription.setMainDiagCode(diseinfo.getString("diagCode"));
            prescription.setMainDiagName(diseinfo.getString("diagName"));
        }
        if (prescription.getPrscTime() == null) {
            prescription.setPrscTime(Instant.now());
        }
    }

    /**
     * 成功时日志错误字段留空，失败时记下平台提示。
     *
     * @param success  是否成功
     * @param response 平台回执
     * @return 失败原因或 null
     */
    private static String failLogMessage(boolean success, ElepPlatformInvokeResult response) {
        if (success) {
            return null;
        }
        return response.getMessage();
    }

    /**
     * 空 JSON 对象上取字符串字段。
     *
     * @param output 平台输出
     * @param key    字段名
     * @return 值或 null
     */
    private static String jsonString(JSONObject output, String key) {
        if (output == null) {
            return null;
        }
        return output.getString(key);
    }

    /**
     * 取第一个非空字符串。
     *
     * @param first  优先
     * @param second 回落
     * @return 非空或 null
     */
    private static String firstNonEmpty(String first, String second) {
        String left = trimToNull(first);
        if (left != null) {
            return left;
        }
        return trimToNull(second);
    }

    /**
     * 非空才写入 JSON，避免把空审方码送给平台。
     *
     * @param body  JSON
     * @param key   字段
     * @param value 值
     */
    private static void putIfNotEmpty(JSONObject body, String key, String value) {
        if (isEmpty(value)) {
            return;
        }
        body.put(key, value);
    }
}
