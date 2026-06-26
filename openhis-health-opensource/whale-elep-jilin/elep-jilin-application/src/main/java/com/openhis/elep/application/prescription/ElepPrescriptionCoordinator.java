/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.application.prescription;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.api.ElepGatewayClient;
import com.openhis.elep.api.dto.ElepGatewayResponse;
import com.openhis.elep.application.config.ElepContractConfig;
import com.openhis.elep.application.config.ElepContractConfigLoader;
import com.openhis.elep.application.prescription.pdf.ElepPdfPathSupport;
import com.openhis.elep.application.prescription.pdf.ElepPdfTemplateService;
import com.openhis.elep.application.gateway.support.ElepCurrentOperatorResolver;
import com.openhis.elep.domain.gateway.ElepOpCode;
import com.openhis.elep.domain.prescription.ElepLocalStatus;
import com.openhis.elep.application.prescription.support.ElepHistoryDetailMapper;
import com.openhis.elep.application.prescription.support.ElepPrescriptionCloneService;
import com.openhis.elep.application.prescription.support.ElepPrescriptionContinuationRules;
import com.openhis.elep.application.prescription.support.ElepUploadLogWriter;
import com.openhis.elep.domain.prescription.dto.ElepPdfGenerateCommand;
import com.openhis.elep.domain.prescription.dto.ElepPdfGenerateResult;
import com.openhis.elep.domain.prescription.dto.ElepPdfInputDto;
import com.openhis.elep.domain.prescription.dto.ElepCloneCommand;
import com.openhis.elep.domain.prescription.dto.ElepEncounterListQuery;
import com.openhis.elep.domain.prescription.dto.ElepEncounterPrescriptionItemDto;
import com.openhis.elep.domain.prescription.dto.ElepHistoryDetailDto;
import com.openhis.elep.domain.prescription.dto.ElepPlatformMedResultQueryCommand;
import com.openhis.elep.domain.prescription.dto.ElepPlatformMedResultResult;
import com.openhis.elep.domain.prescription.dto.ElepPlatformQueryCommand;
import com.openhis.elep.domain.prescription.dto.ElepPlatformQueryResult;
import com.openhis.elep.domain.prescription.dto.ElepRevokeCommand;
import com.openhis.elep.domain.prescription.dto.ElepSaveDraftCommand;
import com.openhis.elep.domain.prescription.dto.ElepSignCommand;
import com.openhis.elep.domain.prescription.dto.ElepRevokeResult;
import com.alibaba.fastjson2.JSONArray;
import com.openhis.elep.domain.prescription.dto.ElepSignResult;
import com.openhis.elep.domain.prescription.dto.ElepFileUploadCommand;
import com.openhis.elep.domain.prescription.dto.ElepFileUploadResult;
import com.openhis.elep.domain.prescription.dto.ElepPreCheckCommand;
import com.openhis.elep.domain.prescription.dto.ElepPreCheckResult;
import com.openhis.elep.domain.prescription.entity.YbElepPrescription;
import com.openhis.elep.domain.prescription.entity.YbElepPrescriptionSnapshot;
import com.openhis.elep.domain.prescription.facade.ElepPrescriptionOperations;
import com.openhis.elep.domain.prescription.repository.YbElepPrescriptionRepository;
import com.openhis.elep.domain.prescription.repository.YbElepPrescriptionSnapshotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.elep.jilin.contracts.command.ElepClonePrescriptionCommand;
import com.openhis.whale.elep.jilin.contracts.command.ElepMedResultQuery;
import com.openhis.whale.elep.jilin.contracts.result.ElepMedResultResult;
import com.openhis.whale.elep.jilin.contracts.dto.ElepPatientHistoryItem;
import com.openhis.whale.elep.jilin.contracts.command.ElepPatientHistoryQuery;
import com.openhis.whale.elep.jilin.contracts.result.ElepPatientHistoryResult;
import com.openhis.whale.elep.jilin.contracts.dto.ElepPrescriptionDraftDto;
import com.openhis.whale.elep.jilin.contracts.command.ElepQueryCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepQueryResult;
import com.openhis.whale.elep.jilin.contracts.snapshot.ElepRxNoSnapshot;
import com.openhis.whale.elep.jilin.contracts.command.ElepUploadCommand;
import com.openhis.whale.elep.jilin.contracts.result.ElepUploadResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 电子处方编排（内部 Coordinator，不对外 REST；前端经 {@code ElepPrescriptionAppService} bridge）。
 * <p>写路径参与调用方 {@code *AppService} 由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 开启的 UoW；本类禁止 {@code @Transactional}。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.elep.deployment.mode", havingValue = "embedded", matchIfMissing = true)
public class ElepPrescriptionCoordinator implements ElepPrescriptionOperations {

    private final ElepGatewayClient gatewayClient;
    private final YbElepPrescriptionRepository prescriptionRepository;
    private final YbElepPrescriptionSnapshotRepository snapshotRepository;
    private final ElepUploadLogWriter uploadLogWriter;
    private final ElepCurrentOperatorResolver operatorResolver;
    private final ElepPrescriptionCloneService cloneService;

    @Override
    public boolean supportsContract(Long contractId) {
        return contractId != null;
    }

    @Override
    public ElepPreCheckResult preCheck(ElepPreCheckCommand command) {
        validateBusNo(command.getContractBusNo());
        if (command.getPlatformBody() == null || command.getPlatformBody().isEmpty()) {
            throw new BusinessException("ELEP_BODY_REQUIRED", "预核验 platformBody 不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        JSONObject body = new JSONObject(command.getPlatformBody());
        String hospRxno = body.getString("hospRxno");
        if (StringUtils.isBlank(hospRxno)) {
            throw new BusinessException("ELEP_HOSP_RXNO_REQUIRED", "platformBody.hospRxno 不能为空");
        }
        YbElepPrescription prescription = loadOrCreateDraft(command, hospRxno, operatorId);
        ElepGatewayResponse response = gatewayClient.invoke(
                command.getContractBusNo(), ElepOpCode.A0001, body, null);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0001,
                body,
                response,
                success,
                success ? null : response.getMessage(),
                operatorId);
        if (!success) {
            prescription.setLocalStatus(ElepLocalStatus.FAILED);
            prescription.setUpdatedAt(LocalDateTime.now());
            prescription.setUpdatedBy(operatorId);
            prescriptionRepository.save(prescription);
            return new ElepPreCheckResult().setSuccess(false).setMessage(response.getMessage());
        }
        JSONObject output = JSON.parseObject(response.getBodyJson());
        String rxTraceCode = output == null ? null : output.getString("rxTraceCode");
        String hiRxno = output == null ? null : output.getString("hiRxno");
        prescription.setRxTraceCode(rxTraceCode);
        if (StringUtils.isNotBlank(hiRxno)) {
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
        ElepPreCheckResult result = new ElepPreCheckResult()
                .setSuccess(true)
                .setPrescriptionId(prescription.getId())
                .setHospRxno(prescription.getHospRxno())
                .setRxTraceCode(rxTraceCode)
                .setHiRxno(hiRxno)
                .setPdfFilePath(pdfFilePath)
                .setMessage("预核验成功");
        return result;
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
        ElepGatewayResponse response = gatewayClient.invoke(
                command.getContractBusNo(), ElepOpCode.A0003, body, null);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0003,
                body,
                response,
                success,
                success ? null : response.getMessage(),
                operatorId);
        if (!success) {
            prescription.setLocalStatus(ElepLocalStatus.FAILED);
            prescription.setUpdatedAt(LocalDateTime.now());
            prescription.setUpdatedBy(operatorId);
            prescriptionRepository.save(prescription);
            return new ElepFileUploadResult().setSuccess(false).setMessage(response.getMessage());
        }
        JSONObject output = JSON.parseObject(response.getBodyJson());
        String hiRxno = output == null ? null : output.getString("hiRxno");
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
        if (StringUtils.isBlank(snapshot.getPdfFilePath())) {
            throw new BusinessException("ELEP_PDF_REQUIRED", "请先生成 PDF 后再签名");
        }
        if (StringUtils.isBlank(snapshot.getUploadRequestJson())) {
            throw new BusinessException("ELEP_SNAPSHOT_REQUIRED", "缺少预核验快照");
        }
        ElepContractConfig config = requireConfig(command.getContractBusNo());
        String pdfBase64 = ElepPdfTemplateService.readPdfBase64(snapshot.getPdfFilePath());
        if (StringUtils.isBlank(pdfBase64)) {
            throw new BusinessException("ELEP_PDF_READ_FAILED", "读取 PDF 文件失败");
        }
        JSONObject body = buildSignBody(command, config, snapshot, pdfBase64);
        ElepGatewayResponse response = gatewayClient.invoke(
                command.getContractBusNo(), ElepOpCode.A0002, body, null);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0002,
                body,
                response,
                success,
                success ? null : response.getMessage(),
                operatorId);
        if (!success) {
            prescription.setLocalStatus(ElepLocalStatus.FAILED);
            prescription.setUpdatedAt(LocalDateTime.now());
            prescription.setUpdatedBy(operatorId);
            prescriptionRepository.save(prescription);
            return new ElepSignResult().setSuccess(false).setMessage(response.getMessage());
        }
        JSONObject output = JSON.parseObject(response.getBodyJson());
        String rxFile = output == null ? null : output.getString("rxFile");
        String signDigest = output == null ? null : output.getString("signDigest");
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
    public ElepRevokeResult revokeFromHisproxy(ElepRevokeCommand command) {
        com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult portResult = revoke(
                new com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand()
                .setContractBusNo(command.getContractBusNo())
                .setPrescriptionId(command.getPrescriptionId())
                .setPlatformBody(command.getPlatformBody()));
        return new ElepRevokeResult()
                .setSuccess(portResult.isSuccess())
                .setMessage(portResult.getMessage())
                .setPrescriptionId(command.getPrescriptionId());
    }

    @Override
    public ElepUploadResult upload(ElepUploadCommand command) {
        throw new BusinessException("ELEP_USE_BRIDGE", "请使用 preCheck → generatePdf → sign → uploadFile 分步上传");
    }

    @Override
    public com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult revoke(
            com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand command) {
        if (command.getPrescriptionId() == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        YbElepPrescription prescription = loadActivePrescription(command.getPrescriptionId());
        if (ElepLocalStatus.REVOKED.equals(prescription.getLocalStatus())) {
            throw new BusinessException("ELEP_ALREADY_REVOKED", "电子处方已撤销");
        }
        String contractBusNo = StringUtils.firstNonBlank(
                command.getContractBusNo(), prescription.getContractBusNo());
        validateBusNo(contractBusNo);
        ElepContractConfig config = requireConfig(contractBusNo);
        JSONObject body = buildRevokeBody(command, config, prescription);
        ElepGatewayResponse response = gatewayClient.invoke(contractBusNo, ElepOpCode.A0004, body, null);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0004,
                body,
                response,
                success,
                success ? null : response.getMessage(),
                operatorId);
        if (!success) {
            return new com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult()
                    .setSuccess(false).setMessage(response.getMessage());
        }
        prescription.setLocalStatus(ElepLocalStatus.REVOKED);
        prescription.setUpdatedAt(LocalDateTime.now());
        prescription.setUpdatedBy(operatorId);
        prescriptionRepository.save(prescription);
        return new com.openhis.whale.elep.jilin.contracts.result.ElepRevokeResult()
                .setSuccess(true).setMessage("撤销成功");
    }

    @Override
    public ElepQueryResult query(ElepQueryCommand command) {
        YbElepPrescription prescription = resolvePrescriptionForQuery(command);
        String contractBusNo = StringUtils.firstNonBlank(command.getContractBusNo(), prescription.getContractBusNo());
        validateBusNo(contractBusNo);
        ElepContractConfig config = requireConfig(contractBusNo);
        JSONObject body = buildQueryBody(command, config, prescription);
        Long operatorId = operatorResolver.requireOperatorId();
        ElepGatewayResponse response = gatewayClient.invoke(contractBusNo, ElepOpCode.A0005, body, null);
        boolean success = response.isSuccess();
        uploadLogWriter.write(
                prescription.getId(),
                ElepOpCode.A0005,
                body,
                response,
                success,
                success ? null : response.getMessage(),
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
                .setRxStasCodg(output == null ? null : output.getString("rxStasCodg"))
                .setRxUsedStasCodg(output == null ? null : output.getString("rxUsedStasCodg"))
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
        if (StringUtils.isBlank(query.getHiRxno())) {
            throw new BusinessException("ELEP_HI_RXNO_REQUIRED", "hiRxno 不能为空");
        }
        String contractBusNo = query.getContractBusNo();
        if (StringUtils.isBlank(contractBusNo)) {
            throw new BusinessException("ELEP_BUSNO_REQUIRED", "contractBusNo 不能为空");
        }
        validateBusNo(contractBusNo);
        ElepContractConfig config = requireConfig(contractBusNo);
        JSONObject body = query.getPlatformBody() != null && !query.getPlatformBody().isEmpty()
                ? new JSONObject(query.getPlatformBody())
                : buildMedResultBody(query.getHiRxno(), config);
        ElepGatewayResponse response = gatewayClient.invoke(contractBusNo, ElepOpCode.A0006, body, null);
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
        if (StringUtils.isBlank(prescriptionNo)) {
            return Optional.empty();
        }
        return prescriptionRepository
                .findByPrescriptionNoAndLocalStatusAndIsDeletedFalse(
                        prescriptionNo.trim(), ElepLocalStatus.UPLOADED)
                .map(this::toRxNoSnapshot);
    }

    @Override
    public List<ElepEncounterPrescriptionItemDto> listEncounterPrescriptions(ElepEncounterListQuery query) {
        if (query.getEncounterId() == null) {
            return List.of();
        }
        List<ElepEncounterPrescriptionItemDto> items = new ArrayList<>();
        for (YbElepPrescription rx : prescriptionRepository
                .findByEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(query.getEncounterId())) {
            items.add(new ElepEncounterPrescriptionItemDto()
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
        int pageSize = query.getPageSize() <= 0 ? 20 : query.getPageSize();
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
    public ElepHistoryDetailDto getHistoryDetail(Long prescriptionId) {
        if (prescriptionId == null) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
        }
        YbElepPrescription rx = loadActivePrescription(prescriptionId);
        YbElepPrescriptionSnapshot snapshot = snapshotRepository.findById(prescriptionId).orElse(null);
        return ElepHistoryDetailMapper.toDetail(rx, snapshot);
    }

    @Override
    public ElepPrescriptionDraftDto cloneToNewEncounter(ElepClonePrescriptionCommand command) {
        return cloneService.clone(command, operatorResolver.requireOperatorId());
    }

    @Override
    public ElepPrescriptionDraftDto cloneToEncounter(ElepCloneCommand command) {
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
    public ElepPrescriptionDraftDto saveDraft(ElepSaveDraftCommand command) {
        return cloneService.saveDraft(command, operatorResolver.requireOperatorId());
    }

    private YbElepPrescription loadOrCreateDraft(ElepPreCheckCommand command, String hospRxno, Long operatorId) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        return prescriptionRepository
                .findByTenantIdAndContractBusNoAndHospRxnoAndIsDeletedFalse(
                        tenantId, command.getContractBusNo(), hospRxno)
                .orElseGet(() -> createDraft(command, hospRxno, operatorId, tenantId));
    }

    private YbElepPrescription createDraft(
            ElepPreCheckCommand command, String hospRxno, Long operatorId, Long tenantId) {
        JSONObject body = new JSONObject(command.getPlatformBody());
        JSONObject mdtrtinfo = body.getJSONObject("mdtrtinfo");
        JSONObject diseinfo = body.getJSONObject("diseinfo");
        YbElepPrescription rx = new YbElepPrescription();
        rx.setTenantId(tenantId);
        rx.setPatientId(command.getPatientId());
        rx.setEncounterId(command.getEncounterId());
        rx.setContractBusNo(command.getContractBusNo());
        rx.setHospRxno(hospRxno);
        rx.setInitRxno(body.getString("initRxno"));
        rx.setLocalStatus(ElepLocalStatus.DRAFT);
        rx.setPrescriptionNo(body.getString("prescriptionNo"));
        if (mdtrtinfo != null) {
            rx.setPrscDrName(mdtrtinfo.getString("prscDrName"));
            rx.setPrscDeptName(mdtrtinfo.getString("prscDeptName"));
        }
        if (diseinfo != null) {
            rx.setMainDiagCode(diseinfo.getString("diagCode"));
            rx.setMainDiagName(diseinfo.getString("diagName"));
        }
        rx.setCreatedAt(LocalDateTime.now());
        rx.setCreatedBy(operatorId);
        rx.setIsDeleted(false);
        return prescriptionRepository.save(rx);
    }

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

    private void updateSnapshotConfirmJson(Long prescriptionId, String confirmJson, Long operatorId) {
        snapshotRepository.findById(prescriptionId).ifPresent(snapshot -> {
            snapshot.setUploadConfirmJson(confirmJson);
            snapshot.setUpdatedAt(LocalDateTime.now());
            snapshot.setUpdatedBy(operatorId);
            snapshotRepository.save(snapshot);
        });
    }

    private static void validateBusNo(String contractBusNo) {
        if (StringUtils.isBlank(contractBusNo)) {
            throw new BusinessException("ELEP_BUSNO_REQUIRED", "contractBusNo 不能为空");
        }
    }

    private YbElepPrescription loadActivePrescription(Long prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .filter(rx -> !Boolean.TRUE.equals(rx.getIsDeleted()))
                .orElseThrow(() -> new BusinessException("ELEP_RX_NOT_FOUND", "电子处方不存在"));
    }

    private YbElepPrescriptionSnapshot loadSnapshot(Long prescriptionId) {
        return snapshotRepository.findById(prescriptionId)
                .orElseThrow(() -> new BusinessException("ELEP_SNAPSHOT_NOT_FOUND", "电子处方快照不存在"));
    }

    private static void requireStatus(YbElepPrescription prescription, String expectedStatus) {
        if (!expectedStatus.equals(prescription.getLocalStatus())) {
            throw new BusinessException(
                    "ELEP_STATUS_INVALID",
                    "当前状态 " + prescription.getLocalStatus() + " 不允许此操作，期望 " + expectedStatus);
        }
    }

    private ElepContractConfig requireConfig(String contractBusNo) {
        ElepContractConfig config = ElepContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null || !config.isEnabled()) {
            throw new BusinessException("ELEP_CONFIG_NOT_FOUND", "未找到电子处方契约配置: " + contractBusNo);
        }
        return config;
    }

    private String doGeneratePdf(
            String contractBusNo,
            YbElepPrescription prescription,
            ElepPdfInputDto pdfInput,
            Long operatorId) {
        ElepContractConfig config = requireConfig(contractBusNo);
        if (StringUtils.isBlank(config.getTemplatePath())) {
            throw new BusinessException("ELEP_TEMPLATE_REQUIRED", "template_path 未配置");
        }
        if (StringUtils.isBlank(config.getOutputPath())) {
            throw new BusinessException("ELEP_OUTPUT_REQUIRED", "output_path 未配置");
        }
        if (StringUtils.isBlank(pdfInput.getRxTraceCode())) {
            pdfInput.setRxTraceCode(prescription.getRxTraceCode());
        }
        if (StringUtils.isBlank(pdfInput.getPrescriptionNo())) {
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

    private JSONObject buildUploadBody(
            ElepFileUploadCommand command,
            ElepContractConfig config,
            YbElepPrescription prescription,
            YbElepPrescriptionSnapshot snapshot) {
        if (command.getPlatformBody() != null && !command.getPlatformBody().isEmpty()) {
            return new JSONObject(command.getPlatformBody());
        }
        if (StringUtils.isBlank(snapshot.getUploadRequestJson())) {
            throw new BusinessException("ELEP_SNAPSHOT_REQUIRED", "缺少预核验快照");
        }
        if (StringUtils.isBlank(snapshot.getPdfFilePath())) {
            throw new BusinessException("ELEP_PDF_REQUIRED", "缺少签名 PDF");
        }
        String pdfBase64 = ElepPdfTemplateService.readPdfBase64(snapshot.getPdfFilePath());
        if (StringUtils.isBlank(pdfBase64)) {
            throw new BusinessException("ELEP_PDF_READ_FAILED", "读取 PDF 文件失败");
        }
        JSONObject body = JSON.parseObject(snapshot.getUploadRequestJson());
        if (body == null) {
            body = new JSONObject();
        }
        body.put("rxTraceCode", prescription.getRxTraceCode());
        if (StringUtils.isNotBlank(prescription.getHiRxno())) {
            body.put("hiRxno", prescription.getHiRxno());
        }
        body.put("fixmedinsCode", config.getFixmedinsCode());
        if (StringUtils.isNotBlank(config.getFixmedinsName())) {
            body.put("fixmedinsName", config.getFixmedinsName());
        }
        body.put("rxFile", pdfBase64);
        if (StringUtils.isNotBlank(snapshot.getSignResponseJson())) {
            JSONObject signOutput = JSON.parseObject(snapshot.getSignResponseJson());
            if (signOutput != null && StringUtils.isNotBlank(signOutput.getString("signDigest"))) {
                body.put("signDigest", signOutput.getString("signDigest"));
            }
        }
        return body;
    }

    private JSONObject buildRevokeBody(
            com.openhis.whale.elep.jilin.contracts.command.ElepRevokeCommand command,
            ElepContractConfig config,
            YbElepPrescription prescription) {
        if (command.getPlatformBody() != null && !command.getPlatformBody().isEmpty()) {
            return new JSONObject(command.getPlatformBody());
        }
        if (StringUtils.isBlank(command.getDrCode()) || StringUtils.isBlank(command.getUndoDrName())) {
            throw new BusinessException(
                    "ELEP_REVOKE_BODY_REQUIRED",
                    "撤销需提供 platformBody，或 drCode + undoDrName");
        }
        String hiRxno = StringUtils.firstNonBlank(command.getHiRxno(), prescription.getHiRxno());
        if (StringUtils.isBlank(hiRxno)) {
            throw new BusinessException("ELEP_HI_RXNO_REQUIRED", "hiRxno 不能为空");
        }
        JSONObject body = new JSONObject();
        body.put("hiRxno", hiRxno);
        body.put("fixmedinsCode", config.getFixmedinsCode());
        body.put("drCode", command.getDrCode());
        body.put("undoDrName", command.getUndoDrName());
        if (StringUtils.isNotBlank(command.getRevokeReason())) {
            body.put("undoRea", command.getRevokeReason());
        }
        return body;
    }

    private YbElepPrescription resolvePrescriptionForQuery(ElepQueryCommand command) {
        if (command.getPrescriptionId() != null) {
            return loadActivePrescription(command.getPrescriptionId());
        }
        if (StringUtils.isNotBlank(command.getHiRxno())) {
            throw new BusinessException("ELEP_RX_ID_REQUIRED", "按 hiRxno 查询需提供 prescriptionId");
        }
        throw new BusinessException("ELEP_RX_ID_REQUIRED", "prescriptionId 不能为空");
    }

    private JSONObject buildQueryBody(
            ElepQueryCommand command,
            ElepContractConfig config,
            YbElepPrescription prescription) {
        if (command.getPlatformBody() != null && !command.getPlatformBody().isEmpty()) {
            return new JSONObject(command.getPlatformBody());
        }
        String hiRxno = StringUtils.firstNonBlank(command.getHiRxno(), prescription.getHiRxno());
        if (StringUtils.isBlank(hiRxno)) {
            throw new BusinessException("ELEP_HI_RXNO_REQUIRED", "hiRxno 不能为空");
        }
        JSONObject body = new JSONObject();
        body.put("fixmedinsCode", config.getFixmedinsCode());
        body.put("hiRxno", hiRxno);
        return body;
    }

    private static JSONObject buildMedResultBody(String hiRxno, ElepContractConfig config) {
        JSONObject body = new JSONObject();
        body.put("fixmedinsCode", config.getFixmedinsCode());
        body.put("hiRxno", hiRxno);
        return body;
    }

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

    private static Instant parseInstant(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

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
                summary.append(StringUtils.defaultString(line.getString("drugName")));
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
}
