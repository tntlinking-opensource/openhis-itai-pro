/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.application.prescription.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.domain.catalog.command.ElepCatalogValidateCommand;
import com.openhis.elep.domain.prescription.command.ElepSaveDraftCommand;
import com.openhis.elep.domain.catalog.access.ElepCatalogSearchOperations;
import com.openhis.elep.domain.prescription.ElepLocalStatus;
import com.openhis.elep.domain.prescription.entity.YbElepPrescription;
import com.openhis.elep.domain.prescription.entity.YbElepPrescriptionSnapshot;
import com.openhis.elep.domain.prescription.repository.YbElepPrescriptionRepository;
import com.openhis.elep.domain.prescription.repository.YbElepPrescriptionSnapshotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.id.SnowflakeIdGenerator;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.openhis.elep.domain.prescription.result.ElepPrescriptionDraft;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 套用/续方：读 snapshot → 校验目录 → 写 DRAFT。 */
@Service
@RequiredArgsConstructor
public class ElepPrescriptionCloneService {

    private static final DateTimeFormatter PRSC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final YbElepPrescriptionRepository prescriptionRepository;
    private final YbElepPrescriptionSnapshotRepository snapshotRepository;
    private final ElepCatalogSearchOperations catalogSearchOperations;

    public ElepPrescriptionDraft clone(ElepClonePrescriptionCommand command, Long operatorId) {
        if (command.getSourcePrescriptionId() == null) {
            throw new BusinessException("ELEP_SOURCE_REQUIRED", "sourcePrescriptionId 不能为空");
        }
        if (command.getTargetEncounterId() == null) {
            throw new BusinessException("ELEP_ENCOUNTER_REQUIRED", "targetEncounterId 不能为空");
        }
        if (command.getPatientId() == null) {
            throw new BusinessException("ELEP_PATIENT_REQUIRED", "patientId 不能为空");
        }
        YbElepPrescription source = prescriptionRepository.findById(command.getSourcePrescriptionId())
                .filter(rx -> !Boolean.TRUE.equals(rx.getIsDeleted()))
                .orElseThrow(() -> new BusinessException("ELEP_RX_NOT_FOUND", "源电子处方不存在"));
        if (!ElepLocalStatus.UPLOADED.equals(source.getLocalStatus())) {
            throw new BusinessException("ELEP_SOURCE_NOT_UPLOADED", "仅已上传处方可套用/续方");
        }
        YbElepPrescriptionSnapshot sourceSnapshot = snapshotRepository.findById(source.getId())
                .filter(s -> StringUtils.isNotBlank(s.getUploadRequestJson()))
                .orElseThrow(() -> new BusinessException("ELEP_SNAPSHOT_REQUIRED", "源方缺少 upload_request_json"));
        String contractBusNo = StringUtils.firstNonBlank(command.getContractBusNo(), source.getContractBusNo());
        boolean continuation = resolveContinuation(command);
        if (continuation) {
            ElepPrescriptionContinuationRules.validate(source);
        }
        JSONObject newBody = JSONObject.parseObject(sourceSnapshot.getUploadRequestJson());
        String newHospRxno = StringUtils.firstNonBlank(command.getNewHospRxno(), "E" + SnowflakeIdGenerator.nextId());
        newBody.put("hospRxno", newHospRxno);
        if (continuation) {
            newBody.put("initRxno", StringUtils.firstNonBlank(command.getInitRxno(), source.getHiRxno()));
        } else {
            newBody.remove("initRxno");
        }
        if (Boolean.FALSE.equals(command.getCopyDiagnosis())) {
            newBody.remove("diseinfo");
        }
        if (command.getMdtrtinfoPatch() != null && !command.getMdtrtinfoPatch().isEmpty()) {
            JSONObject mdtrtinfo = newBody.getJSONObject("mdtrtinfo");
            if (mdtrtinfo == null) {
                mdtrtinfo = new JSONObject();
            }
            mdtrtinfo.putAll(command.getMdtrtinfoPatch());
            newBody.put("mdtrtinfo", mdtrtinfo);
        }
        newBody.put("prscTime", PRSC_TIME.format(LocalDateTime.now()));
        validateCatalogLines(command.getContractId(), contractBusNo, newBody);
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (prescriptionRepository.findByTenantIdAndContractBusNoAndHospRxnoAndIsDeletedFalse(
                tenantId, contractBusNo, newHospRxno).isPresent()) {
            throw new BusinessException("ELEP_HOSP_RXNO_DUP", "院内处方号已存在: " + newHospRxno);
        }
        YbElepPrescription draft = buildDraft(source, command, contractBusNo, newHospRxno, newBody, tenantId, operatorId);
        prescriptionRepository.save(draft);
        saveDraftSnapshot(draft.getId(), newBody, tenantId, operatorId);
        return new ElepPrescriptionDraft()
                .setPrescriptionId(draft.getId())
                .setHospRxno(draft.getHospRxno())
                .setInitRxno(draft.getInitRxno())
                .setLocalStatus(draft.getLocalStatus());
    }

    public ElepPrescriptionDraft saveDraft(ElepSaveDraftCommand command, Long operatorId) {
        if (command.getEncounterId() == null) {
            throw new BusinessException("ELEP_ENCOUNTER_REQUIRED", "encounterId 不能为空");
        }
        if (command.getPatientId() == null) {
            throw new BusinessException("ELEP_PATIENT_REQUIRED", "patientId 不能为空");
        }
        if (StringUtils.isBlank(command.getContractBusNo())) {
            throw new BusinessException("ELEP_BUSNO_REQUIRED", "contractBusNo 不能为空");
        }
        if (command.getPlatformBody() == null || command.getPlatformBody().isEmpty()) {
            throw new BusinessException("ELEP_BODY_REQUIRED", "platformBody 不能为空");
        }
        JSONObject body = JSONObject.parseObject(JSONObject.toJSONString(command.getPlatformBody()));
        String contractBusNo = command.getContractBusNo().trim();
        String hospRxno = body.getString("hospRxno");
        if (StringUtils.isBlank(hospRxno)) {
            hospRxno = "E" + SnowflakeIdGenerator.nextId();
            body.put("hospRxno", hospRxno);
        }
        if (!body.containsKey("prscTime")) {
            body.put("prscTime", PRSC_TIME.format(LocalDateTime.now()));
        }
        validateCatalogLines(command.getContractId(), contractBusNo, body);
        Long tenantId = TenantUtils.getCurrentTenantId();
        YbElepPrescription rx;
        if (command.getPrescriptionId() != null) {
            rx = prescriptionRepository.findById(command.getPrescriptionId())
                    .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                    .orElseThrow(() -> new BusinessException("ELEP_RX_NOT_FOUND", "电子处方不存在"));
            if (!ElepLocalStatus.DRAFT.equals(rx.getLocalStatus())
                    && !ElepLocalStatus.FAILED.equals(rx.getLocalStatus())) {
                throw new BusinessException("ELEP_STATUS_INVALID", "仅草稿/失败状态可保存");
            }
            if (!contractBusNo.equals(rx.getContractBusNo())) {
                rx.setContractBusNo(contractBusNo);
            }
            applyBodyToPrescription(rx, body, operatorId);
            prescriptionRepository.save(rx);
            upsertDraftSnapshot(rx.getId(), body, tenantId, operatorId);
        } else {
            if (prescriptionRepository.findByTenantIdAndContractBusNoAndHospRxnoAndIsDeletedFalse(
                    tenantId, contractBusNo, hospRxno).isPresent()) {
                throw new BusinessException("ELEP_HOSP_RXNO_DUP", "院内处方号已存在: " + hospRxno);
            }
            rx = new YbElepPrescription();
            rx.setTenantId(tenantId);
            rx.setPatientId(command.getPatientId());
            rx.setEncounterId(command.getEncounterId());
            rx.setContractBusNo(contractBusNo);
            rx.setHospRxno(hospRxno);
            rx.setLocalStatus(ElepLocalStatus.DRAFT);
            rx.setCreatedAt(LocalDateTime.now());
            rx.setCreatedBy(operatorId);
            rx.setIsDeleted(false);
            applyBodyToPrescription(rx, body, operatorId);
            prescriptionRepository.saveAndFlush(rx);
            saveDraftSnapshot(rx.getId(), body, tenantId, operatorId);
        }
        return new ElepPrescriptionDraft()
                .setPrescriptionId(rx.getId())
                .setHospRxno(rx.getHospRxno())
                .setInitRxno(rx.getInitRxno())
                .setLocalStatus(rx.getLocalStatus());
    }

    private void applyBodyToPrescription(YbElepPrescription rx, JSONObject body, Long operatorId) {
        rx.setInitRxno(body.getString("initRxno"));
        rx.setPrescriptionNo(body.getString("prescriptionNo"));
        rx.setRxTypeCode(body.getString("rxTypeCode"));
        JSONObject mdtrtinfo = body.getJSONObject("mdtrtinfo");
        JSONObject diseinfo = body.getJSONObject("diseinfo");
        if (mdtrtinfo != null) {
            rx.setPrscDrName(mdtrtinfo.getString("prscDrName"));
            rx.setPrscDeptName(mdtrtinfo.getString("prscDeptName"));
        }
        if (diseinfo != null) {
            rx.setMainDiagCode(diseinfo.getString("diagCode"));
            rx.setMainDiagName(diseinfo.getString("diagName"));
        }
        JSONArray drugLines = body.getJSONArray("rxdrugdetail");
        if (drugLines != null) {
            rx.setLineCount(drugLines.size());
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
                rx.setDrugSummary(summary.toString());
            }
        }
        if (rx.getPrscTime() == null) {
            rx.setPrscTime(Instant.now());
        }
        rx.setUpdatedAt(LocalDateTime.now());
        rx.setUpdatedBy(operatorId);
        if (ElepLocalStatus.FAILED.equals(rx.getLocalStatus())) {
            rx.setLocalStatus(ElepLocalStatus.DRAFT);
        }
    }

    private void upsertDraftSnapshot(Long prescriptionId, JSONObject request, Long tenantId, Long operatorId) {
        YbElepPrescriptionSnapshot snapshot = snapshotRepository.findById(prescriptionId)
                .orElseGet(YbElepPrescriptionSnapshot::new);
        snapshot.setId(prescriptionId);
        snapshot.setTenantId(tenantId);
        snapshot.setUploadRequestJson(request.toJSONString());
        if (snapshot.getCreatedAt() == null) {
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshot.setCreatedBy(operatorId);
        }
        snapshot.setUpdatedAt(LocalDateTime.now());
        snapshot.setUpdatedBy(operatorId);
        snapshot.setIsDeleted(false);
        snapshotRepository.save(snapshot);
    }

    private static boolean resolveContinuation(ElepClonePrescriptionCommand command) {
        if (command.getContinuationRx() != null) {
            return command.getContinuationRx();
        }
        return StringUtils.isNotBlank(command.getInitRxno());
    }

    private void validateCatalogLines(Long contractId, String contractBusNo, JSONObject body) {
        if (contractId == null) {
            return;
        }
        JSONArray drugLines = body.getJSONArray("rxdrugdetail");
        if (drugLines == null || drugLines.isEmpty()) {
            return;
        }
        ElepCatalogValidateCommand validateCommand = new ElepCatalogValidateCommand()
                .setContractId(contractId)
                .setContractBusNo(contractBusNo)
                .setCatalogKind(ElepCatalogValidateCommand.KIND_DRUG);
        for (int i = 0; i < drugLines.size(); i++) {
            JSONObject line = drugLines.getJSONObject(i);
            if (line == null) {
                continue;
            }
            validateCommand.getLines().add(new ElepCatalogValidateCommand.Line()
                    .setLineNo(i + 1)
                    .setHilistCode(line.getString("medListCodg")));
        }
        var invalid = catalogSearchOperations.validateLines(validateCommand);
        if (!invalid.isEmpty()) {
            throw new BusinessException(
                    "ELEP_CATALOG_INVALID",
                    "药品医保码校验失败，共 " + invalid.size() + " 行需重新选码");
        }
        JSONObject diseinfo = body.getJSONObject("diseinfo");
        if (diseinfo != null && StringUtils.isNotBlank(diseinfo.getString("diagCode"))) {
            var diagInvalid = catalogSearchOperations.validateLines(new ElepCatalogValidateCommand()
                    .setContractId(contractId)
                    .setContractBusNo(contractBusNo)
                    .setCatalogKind(ElepCatalogValidateCommand.KIND_DIAGNOSIS)
                    .setLines(List.of(new ElepCatalogValidateCommand.Line()
                            .setLineNo(1)
                            .setHilistCode(diseinfo.getString("diagCode")))));
            if (!diagInvalid.isEmpty()) {
                throw new BusinessException("ELEP_CATALOG_INVALID", "诊断医保码校验失败");
            }
        }
    }

    private static YbElepPrescription buildDraft(
            YbElepPrescription source,
            ElepClonePrescriptionCommand command,
            String contractBusNo,
            String newHospRxno,
            JSONObject newBody,
            Long tenantId,
            Long operatorId) {
        JSONObject mdtrtinfo = newBody.getJSONObject("mdtrtinfo");
        JSONObject diseinfo = newBody.getJSONObject("diseinfo");
        YbElepPrescription draft = new YbElepPrescription();
        draft.setTenantId(tenantId);
        draft.setPatientId(command.getPatientId());
        draft.setEncounterId(command.getTargetEncounterId());
        draft.setContractBusNo(contractBusNo);
        draft.setHospRxno(newHospRxno);
        draft.setInitRxno(newBody.getString("initRxno"));
        draft.setLocalStatus(ElepLocalStatus.DRAFT);
        draft.setPrescriptionNo(newBody.getString("prescriptionNo"));
        draft.setDrugSummary(source.getDrugSummary());
        draft.setLineCount(source.getLineCount());
        draft.setRxTypeCode(source.getRxTypeCode());
        draft.setPrscTime(Instant.now());
        if (mdtrtinfo != null) {
            draft.setPrscDrName(mdtrtinfo.getString("prscDrName"));
            draft.setPrscDeptName(mdtrtinfo.getString("prscDeptName"));
        }
        if (diseinfo != null) {
            draft.setMainDiagCode(diseinfo.getString("diagCode"));
            draft.setMainDiagName(diseinfo.getString("diagName"));
        }
        draft.setCreatedAt(LocalDateTime.now());
        draft.setCreatedBy(operatorId);
        draft.setIsDeleted(false);
        return draft;
    }

    private void saveDraftSnapshot(Long prescriptionId, JSONObject request, Long tenantId, Long operatorId) {
        YbElepPrescriptionSnapshot snapshot = new YbElepPrescriptionSnapshot();
        snapshot.setId(prescriptionId);
        snapshot.setTenantId(tenantId);
        snapshot.setUploadRequestJson(request.toJSONString());
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setCreatedBy(operatorId);
        snapshot.setUpdatedAt(LocalDateTime.now());
        snapshot.setUpdatedBy(operatorId);
        snapshot.setIsDeleted(false);
        snapshotRepository.save(snapshot);
    }
}
