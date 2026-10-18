/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.prescription.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.elep.domain.catalog.command.ElepCatalogValidateCommand;
import com.zxplat.plugin.elep.domain.prescription.access.ElepEncounterContextAccess;
import com.zxplat.plugin.elep.domain.prescription.command.ElepDiagnosisLine;
import com.zxplat.plugin.elep.domain.prescription.command.ElepEncounterContextQuery;
import com.zxplat.plugin.elep.domain.prescription.command.ElepRxDrugLine;
import com.zxplat.plugin.elep.domain.prescription.command.ElepSaveDraftCommand;
import com.zxplat.plugin.elep.domain.catalog.access.ElepCatalogSearchOperations;
import com.zxplat.plugin.elep.domain.prescription.ElepLocalStatus;
import com.zxplat.plugin.elep.domain.prescription.constants.ElepPrescriptionConstants;
import com.zxplat.plugin.elep.domain.prescription.entity.YbElepPrescription;
import com.zxplat.plugin.elep.domain.prescription.entity.YbElepPrescriptionSnapshot;
import com.zxplat.plugin.elep.domain.prescription.repository.YbElepPrescriptionRepository;
import com.zxplat.plugin.elep.domain.prescription.repository.YbElepPrescriptionSnapshotRepository;
import com.zxplat.plugin.elep.domain.prescription.result.ElepEncounterContextResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.id.SnowflakeIdGenerator;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.elep.domain.prescription.command.ElepClonePrescriptionCommand;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPrescriptionDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/** 套用/续方与草稿保存：只写 {@code yb_elep_*}，开方上下文经 Access 只读 HIS。 */
@Service
@RequiredArgsConstructor
public class ElepPrescriptionCloneService {

    private final YbElepPrescriptionRepository prescriptionRepository;
    private final YbElepPrescriptionSnapshotRepository snapshotRepository;
    private final ElepCatalogSearchOperations catalogSearchOperations;
    private final ElepEncounterContextAccess encounterContextAccess;
    private final ElepA0001BodyAssembler a0001BodyAssembler;

    /**
     * 套用/续方：从已上传方复制拟报文到当前就诊草稿（本期前端隐藏入口，接口保留）。
     *
     * @param command    源方与目标就诊
     * @param operatorId 当前登录用户
     * @return 新草稿摘要
     */
    public ElepPrescriptionDraft clone(ElepClonePrescriptionCommand command, Long operatorId) {
        // Gate：源方与目标就诊、患者均必填
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
                .filter(s -> isNotEmpty(s.getUploadRequestJson()))
                .orElseThrow(() -> new BusinessException("ELEP_SNAPSHOT_REQUIRED", "源方缺少 upload_request_json"));
        String contractBusNo = firstNonEmpty(command.getContractBusNo(), source.getContractBusNo());
        boolean continuation = resolveContinuation(command);
        if (continuation) {
            ElepPrescriptionContinuationRules.validate(source);
        }
        JSONObject newBody = JSONObject.parseObject(sourceSnapshot.getUploadRequestJson());
        String newHospRxno = firstNonEmpty(command.getNewHospRxno(),
                ElepPrescriptionConstants.HOSP_RXNO_PREFIX + SnowflakeIdGenerator.nextId());
        newBody.put("hospRxno", newHospRxno);
        if (continuation) {
            newBody.put("initRxno", firstNonEmpty(command.getInitRxno(), source.getHiRxno()));
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
        newBody.put("prscTime", LocalDateTime.now().format(ElepPrescriptionConstants.PRSC_TIME_FORMATTER));
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

    /**
     * 保存独立开方草稿：只写电子处方表；须已有挂号 map(11)。
     *
     * @param command    结构化草稿
     * @param operatorId 当前登录用户
     * @return 草稿摘要
     */
    public ElepPrescriptionDraft saveDraft(ElepSaveDraftCommand command, Long operatorId) {
        // Gate：电子处方只绑当前挂号就诊，禁止无就诊落库
        if (command.getEncounterId() == null) {
            throw new BusinessException("ELEP_ENCOUNTER_REQUIRED", "encounterId 不能为空");
        }
        if (command.getPatientId() == null) {
            throw new BusinessException("ELEP_PATIENT_REQUIRED", "patientId 不能为空");
        }
        // Pre：只读挂号 map(11)；契约由挂号回填，前端不选手选省市工伤
        String contractBusNo = trimToNull(command.getContractBusNo());
        ElepEncounterContextResult context = encounterContextAccess.load(new ElepEncounterContextQuery()
                .setEncounterId(command.getEncounterId())
                .setPatientId(command.getPatientId())
                .setContractBusNo(contractBusNo));
        if (!context.isAllowed()) {
            throw new BusinessException("ELEP_CONTEXT_BLOCKED", context.getBlockReason());
        }
        if (isNotEmpty(context.getContractBusNo())) {
            contractBusNo = context.getContractBusNo().trim();
            command.setContractBusNo(contractBusNo);
        }
        if (context.getContractId() != null) {
            command.setContractId(context.getContractId());
        }
        // Gate：没有挂号契约则不能打电子处方中台
        if (isEmpty(contractBusNo)) {
            throw new BusinessException("ELEP_BUSNO_REQUIRED", "本次就诊未绑定医保契约，不能开电子处方");
        }
        ElepDiagnosisLine diagnosis = command.getDiagnosis();
        List<ElepRxDrugLine> drugLines = command.getDrugLines();
        // Gate：本页独立开方，至少一条医保诊断与一行药品
        if (diagnosis == null || isEmpty(diagnosis.getDiagCode())) {
            throw new BusinessException("ELEP_DIAG_REQUIRED", "请选择医保诊断");
        }
        if (drugLines == null || drugLines.isEmpty()) {
            throw new BusinessException("ELEP_DRUG_REQUIRED", "请至少选择一行医保药品");
        }
        boolean hasDiseCodg = isNotEmpty(command.getDiseCodg());
        boolean hasDiseName = isNotEmpty(command.getDiseName());
        // Gate：病种编码与名称须成对；有病种即慢病，无病种即普通门诊
        if (hasDiseCodg != hasDiseName) {
            throw new BusinessException("ELEP_DISE_REQUIRED", "慢病病种编码和名称须同时填写");
        }
        String medType = ElepPrescriptionConstants.resolveMedTypeFromDisease(
                command.getDiseCodg(), command.getDiseName());
        Long tenantId = TenantUtils.getCurrentTenantId();
        YbElepPrescription rx;
        // Cmd：已有草稿则覆盖，否则新建院内处方号
        if (command.getPrescriptionId() != null) {
            rx = prescriptionRepository.findById(command.getPrescriptionId())
                    .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                    .orElseThrow(() -> new BusinessException("ELEP_RX_NOT_FOUND", "电子处方不存在"));
            if (!ElepLocalStatus.DRAFT.equals(rx.getLocalStatus())
                    && !ElepLocalStatus.FAILED.equals(rx.getLocalStatus())) {
                throw new BusinessException("ELEP_STATUS_INVALID", "仅草稿/失败状态可保存");
            }
            rx.setContractBusNo(contractBusNo);
        } else {
            String hospRxno = ElepPrescriptionConstants.HOSP_RXNO_PREFIX + SnowflakeIdGenerator.nextId();
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
        }
        applyStructuredDraft(rx, command, medType, diagnosis, context, operatorId);
        // Asm：服务端组装拟 A0001，前端不再传 platformBody
        JSONObject body = a0001BodyAssembler.assembleDraftBody(rx, context, diagnosis, drugLines);
        validateCatalogLines(command.getContractId(), contractBusNo, body);
        applyBodyToPrescription(rx, body, operatorId);
        if (command.getPrescriptionId() != null) {
            prescriptionRepository.save(rx);
            upsertDraftSnapshot(rx.getId(), body, tenantId, operatorId);
        } else {
            prescriptionRepository.saveAndFlush(rx);
            saveDraftSnapshot(rx.getId(), body, tenantId, operatorId);
        }
        return new ElepPrescriptionDraft()
                .setPrescriptionId(rx.getId())
                .setHospRxno(rx.getHospRxno())
                .setInitRxno(rx.getInitRxno())
                .setLocalStatus(rx.getLocalStatus());
    }

    /**
     * 把本页医疗类别、病种、挂号号写入主表（不写 HIS）。
     *
     * @param rx         主表
     * @param command    草稿命令
     * @param medType    已解析医疗类别
     * @param diagnosis  诊断
     * @param context    挂号上下文
     * @param operatorId 操作人
     */
    private void applyStructuredDraft(
            YbElepPrescription rx,
            ElepSaveDraftCommand command,
            String medType,
            ElepDiagnosisLine diagnosis,
            ElepEncounterContextResult context,
            Long operatorId) {
        rx.setMedType(medType);
        rx.setDiseCodg(trimToNull(command.getDiseCodg()));
        rx.setDiseName(trimToNull(command.getDiseName()));
        String rxType = trimToNull(command.getRxTypeCode());
        if (rxType == null) {
            rxType = ElepPrescriptionConstants.RX_TYPE_WESTERN;
        }
        rx.setRxTypeCode(rxType);
        String valiDays = trimToNull(command.getValiDays());
        if (valiDays == null) {
            valiDays = ElepPrescriptionConstants.DEFAULT_VALI_DAYS;
        }
        rx.setValiDays(valiDays);
        rx.setMdtrtId(context.getMdtrtId());
        rx.setPsnNo(context.getPsnNo());
        rx.setPrscDrName(context.getDoctorName());
        rx.setPrscDeptName(context.getDeptName());
        rx.setMainDiagCode(trimToNull(diagnosis.getDiagCode()));
        rx.setMainDiagName(trimToNull(diagnosis.getDiagName()));
        rx.setUpdatedAt(LocalDateTime.now());
        rx.setUpdatedBy(operatorId);
        if (ElepLocalStatus.FAILED.equals(rx.getLocalStatus())) {
            rx.setLocalStatus(ElepLocalStatus.DRAFT);
        }
        // 改草稿须重审：清掉审方药师字段，未审不得 A0001
        rx.setPharCode(null);
        rx.setPharName(null);
        rx.setPharDeptCode(null);
        rx.setPharDeptName(null);
        rx.setPharProfttlCodg(null);
        rx.setPharPracCertNo(null);
        rx.setReviewedAt(null);
        rx.setReviewedBy(null);
    }

    /**
     * 用拟报文回写列表摘要与主诊断，不改审方字段。
     *
     * @param rx         主表
     * @param body       拟 A0001
     * @param operatorId 操作人
     */
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

    /**
     * 更新已有草稿快照的拟报文。
     *
     * @param prescriptionId 处方主键
     * @param request        拟 A0001
     * @param tenantId       租户
     * @param operatorId     操作人
     */
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

    /**
     * 是否按续方处理：显式标志优先，否则看是否带了源医保处方号。
     *
     * @param command 套用命令
     * @return 续方为 true
     */
    private static boolean resolveContinuation(ElepClonePrescriptionCommand command) {
        if (command.getContinuationRx() != null) {
            return command.getContinuationRx();
        }
        return isNotEmpty(command.getInitRxno());
    }

    /**
     * 校验药品/诊断医保码仍在目录镜像中；无契约主键时跳过（仅无法对码）。
     *
     * @param contractId    契约主键
     * @param contractBusNo 契约 bus_no
     * @param body          拟 A0001
     */
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
        if (diseinfo != null && isNotEmpty(diseinfo.getString("diagCode"))) {
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

    /**
     * 从已上传源方组装新草稿主表（不写 HIS）。
     *
     * @param source        源方
     * @param command       套用命令
     * @param contractBusNo 契约
     * @param newHospRxno   新院内处方号
     * @param newBody       新拟报文
     * @param tenantId      租户
     * @param operatorId    操作人
     * @return 未落库草稿实体
     */
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

    /**
     * 新建草稿时插入快照行。
     *
     * @param prescriptionId 处方主键
     * @param request        拟 A0001
     * @param tenantId       租户
     * @param operatorId     操作人
     */
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
}
