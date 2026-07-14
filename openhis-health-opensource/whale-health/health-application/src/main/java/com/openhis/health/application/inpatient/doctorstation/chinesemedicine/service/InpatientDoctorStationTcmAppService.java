/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.mapper.InpatientDoctorStationAdviceMapper;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.assembler.InpatientTcmDiagnosisAssembler;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.assembler.InpatientTcmRxListAssembler;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.assembler.InpatientTcmRxSignAssembler;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto.*;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.mapper.InpatientDoctorStationTcmMapper;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request.*;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.response.*;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.support.InpatientTcmPrescriptionExpandAssembler;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.support.InpatientTcmRxHeaderGate;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.support.InpatientTcmRxRevokeGate;
import com.openhis.health.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.clinical.condition.command.DelEncounterDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.service.ConditionDomainService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.*;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.support.InpatientDoctorStationEncounterVisibilityResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 住院医生站中医 Tab 编排（IF-IP-TCM-29～41，AS-IP-DOR-006）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>读侧：中医 Tab 初始化、病证组列表、病名/证候目录、中药处方列表与历史、饮片目录分页。</li>
 *   <li>写侧：病证组全量保存/删除、中药草稿暂存、签发、签退/删除、长期停嘱与取消停嘱。</li>
 * </ul>
 *
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/inpatientDoctorStationTcm/{methodName}</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientDoctorStationTcmAppService {

    /** 当前登录操作人解析。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 住院就诊上下文 Facade。 */
    private final InpatientDoctorStationEncounterVisibilityResolver inpatientDoctorStationEncounterVisibilityResolver;

    /** 中医 Tab Request/Command/Response 映射。 */
    private final InpatientDoctorStationTcmMapper tcmMapper;

    /** 住院医嘱通用映射与 Gate。 */
    private final InpatientDoctorStationAdviceMapper adviceMapper;

    /** 中医病证组聚合装配。 */
    private final InpatientTcmDiagnosisAssembler diagnosisAssembler;

    /** 中药处方列表 enrich 装配。 */
    private final InpatientTcmRxListAssembler prescriptionListAssembler;

    /** 中药处方签发辅助（取号、批次码）。 */
    private final InpatientTcmRxSignAssembler prescriptionSignAssembler;

    /** 方头长临/频次/一致性校验。 */
    private final InpatientTcmRxHeaderGate headerValidationSupport;

    /** 签退/删除执行/发药/费用门控。 */
    private final InpatientTcmRxRevokeGate inpatientTcmRxRevokeGate;

    /** 按处方号整方扩行。 */
    private final InpatientTcmPrescriptionExpandAssembler prescriptionExpandSupport;

    /** 就诊诊断快照 JSON 装配。 */
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;

    /** 病名/证候目录读侧。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /** 诊断事实写侧。 */
    private final ConditionDomainService conditionDomainService;

    /** 就诊诊断关联写侧。 */
    private final EncounterDomainService encounterDomainService;

    /** 诊断事实读侧。 */
    private final ConditionQueryService conditionQueryService;

    /** 药品申请读侧。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 药品申请写侧。 */
    private final MedicationRequestDomainService medicationRequestDomainService;

    /** 服务申请读侧（出院/转科门禁）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 业务单号序列。 */
    private final BusinessSequenceService businessSequenceService;

    /** 医嘱目录行装配（定价，无库存）。 */
    private final InpatientDoctorStationAdviceCatalogAssembler adviceCatalogAssembler;

    /** 产品目录分页加载。 */
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;

    /** 产品定义批量加载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 分页窗口 Gate。 */
    private final PageWindowGate pageWindowGate;

    /** 术语/codeSystem 读侧。 */
    private final CodeSystemQueryService codeSystemQueryService;

    /** 用药频次下拉加载。 */
    private final FrequencyOptionLoader frequencyOptionLoader;

    /**
     * IF-IP-TCM-29：中医 Tab 页初始化。
     *
     * @param request 含 encounterId、patientId
     * @return 中药用法、频次下拉及默认长临筛选
     * @throws BusinessException 就诊快照失败或患者不一致时
     */
    public InitInpatientDoctorStationTcmResponse initInpatientDoctorStationTcm(InitInpatientDoctorStationTcmRequest request) {
        // Pre：住院快照
        InpatientEncounterContextQueryModel snapshot =
            this.inpatientDoctorStationEncounterVisibilityResolver.assertVisible(
                request.getEncounterId(), request.getPatientId());
        // Pre：中药用法
        List<com.openhis.whale.core.application.dto.OptionDto> tcmOptions =
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        // Asm：频次下拉
        List<com.openhis.whale.core.application.dto.OptionDto> rateOptions = frequencyOptionLoader.loadActiveRateOptions();
        // Pre：单味特殊煎法走 dosage_instruction 术语
        List<com.openhis.whale.core.application.dto.OptionDto> dosageInstructionOptions =
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        return tcmMapper.toInitInpatientDoctorStationTcmResponse(tcmOptions, rateOptions, dosageInstructionOptions);
    }

    /**
     * IF-IP-TCM-30：加载中医病证组列表。
     *
     * @param request 含 encounterId、patientId
     * @return 病证组行列表
     * @throws BusinessException 就诊快照失败或患者不一致时
     */
    public ListTcmDiagnosisGroupsResponse listTcmDiagnosisGroups(ListTcmDiagnosisGroupsRequest request) {
        // Pre：住院快照
        InpatientEncounterContextQueryModel snapshot =
            this.inpatientDoctorStationEncounterVisibilityResolver.assertVisible(
                request.getEncounterId(), request.getPatientId());
        String diagnosisJson = encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson();
        // Asm：聚合病证组
        List<TcmDiagnosisGroupRowDto> groups = diagnosisAssembler.groupTcmDiagnosisRows(request.getEncounterId(), diagnosisJson);
        return tcmMapper.toListTcmDiagnosisGroupsResponse(groups);
    }

    /**
     * IF-IP-TCM-31：分页检索中医病名/证候目录。
     *
     * @param request 含 catalogRole（DISEASE/SYNDROME）、keyword、分页参数
     * @return 目录分页结果
     * @throws BusinessException 分页非法或 catalogRole 非法时
     */
    public PageTcmConditionDefinitionResponse pageTcmConditionDefinitionCatalog(PageTcmConditionDefinitionRequest request) {
        // Gate：分页窗口
        pageWindowGate.requirePositivePageWindow(request,
            ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "分页参数非法");
        List<String> sourceIn;
        // Gate：catalogRole 决定检索来源集合
        if (DoctorStationTcmConstants.CATALOG_ROLE_DISEASE.equals(request.getCatalogRole())) {
            sourceIn = DoctorStationTcmConstants.TCM_DISEASE_SOURCES;
        } else if (DoctorStationTcmConstants.CATALOG_ROLE_SYNDROME.equals(request.getCatalogRole())) {
            sourceIn = DoctorStationTcmConstants.TCM_SYNDROME_SOURCES;
        } else {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_DIAGNOSIS_GROUP_INCOMPLETE);
        }
        // Pre：ACTIVE 目录分页
        Page<DoctorStationConditionDefinitionRowModel> page = conditionDefinitionQueryService.pageActiveDoctorStationDefinitions(
            request.getKeyword(), sourceIn, request.getPageNo(), request.getPageSize());
        List<TcmDiagnosisCatalogRowDto> rows = new ArrayList<>();
        for (DoctorStationConditionDefinitionRowModel model : page.getContent()) {
            TcmDiagnosisCatalogRowDto row = new TcmDiagnosisCatalogRowDto();
            row.setDefinitionId(model.getId());
            row.setConditionCode(model.getConditionCode());
            row.setConditionName(model.getName());
            row.setSourceCode(model.getSourceEnum());
            row.setSourceText(model.getSourceEnum());
            rows.add(row);
        }
        return tcmMapper.toPageTcmConditionDefinitionResponse(page, rows);
    }

    /**
     * IF-IP-TCM-32：全量保存中医病证组。
     *
     * @param request 含 encounterId、patientId、groups 全量列表
     * @return 保存成功的组号列表
     * @throws BusinessException 组不完整、主诊断超限、组被引用或持久化失败时
     */
    public SaveTcmDiagnosisGroupsBatchResponse saveTcmDiagnosisGroupsBatch(SaveTcmDiagnosisGroupsBatchRequest request) {
        // Pre：住院可写快照
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_ENCOUNTER_SNAPSHOT_FAILED);
        // Gate：批次校验
        validateTcmDiagnosisBatch(request);
        String diagnosisJson = encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson();
        List<TcmDiagnosisGroupRowDto> existing = diagnosisAssembler.groupTcmDiagnosisRows(request.getEncounterId(), diagnosisJson);
        List<String> removedGroups = tcmMapper.diffRemovedTcmGroups(existing, request);
        // Cmd：删除不在批次内的历史组
        for (String removed : removedGroups) {
            deleteTcmGroupInternal(request.getEncounterId(), removed, existing, operatorId);
        }
        Map<String, String> groupKeyByItem = assignTcmGroupKeys(request);
        // Cmd：保存诊断事实与关联
        List<Long> savedIds = conditionDomainService.saveDoctorDiagnosisTcm(
            tcmMapper.toSaveDoctorDiagnosisTcmCommand(request, groupKeyByItem, operatorId));
        encounterDomainService.batchUpsertTcmEncounterDiagnosisAssociation(
            tcmMapper.toBatchUpsertTcmEncounterDiagnosisAssociationCommand(request, savedIds, groupKeyByItem, operatorId));
        return tcmMapper.toSaveTcmDiagnosisGroupsBatchResponse(new ArrayList<>(new LinkedHashSet<>(groupKeyByItem.values())));
    }

    /**
     * IF-IP-TCM-33：删除中医病证组。
     *
     * @param request 含 encounterId、patientId、tcmSyndromeGroup
     * @return 删除成功标志
     * @throws BusinessException 组不存在、被引用或 Gate 失败时
     */
    public DeleteTcmDiagnosisGroupResponse deleteTcmDiagnosisGroup(DeleteTcmDiagnosisGroupRequest request) {
        // Pre：住院可写快照
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_ENCOUNTER_SNAPSHOT_FAILED);
        String diagnosisJson = encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson();
        List<TcmDiagnosisGroupRowDto> existing = diagnosisAssembler.groupTcmDiagnosisRows(request.getEncounterId(), diagnosisJson);
        // Cmd：删除指定组
        deleteTcmGroupInternal(request.getEncounterId(), request.getTcmSyndromeGroup(), existing, operatorId);
        return tcmMapper.toDeleteTcmDiagnosisGroupResponse(Boolean.TRUE);
    }

    /**
     * IF-IP-TCM-34：加载中药处方列表。
     *
     * @param request 含 encounterId、patientId、可选 therapyTypeCode
     * @return 按方聚合的处方方头列表
     * @throws BusinessException 长临筛选非法或 Gate 失败时
     */
    public ListTcmHerbalPrescriptionsResponse listTcmHerbalPrescriptions(ListTcmHerbalPrescriptionsRequest request) {
        // Pre：住院快照
        InpatientEncounterContextQueryModel snapshot =
            this.inpatientDoctorStationEncounterVisibilityResolver.assertVisible(
                request.getEncounterId(), request.getPatientId());
        // Gate：长临筛选
        String therapyEnum = headerValidationSupport.resolveTherapyFilterOrNull(request.getTherapyTypeCode());
        // Pre：领域按方聚合
        List<TcmHerbalPrescriptionGroupQueryModel> groups = medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
            request.getEncounterId(), GenerateSource.DOCTOR_PRESCRIPTION.getCode(), therapyEnum);
        // Asm：enrich（ChargeItem 账单单价优先，对齐 BR-PRICE-SRC-001）
        List<TcmRxHeaderDto> headers = prescriptionListAssembler.enrichInpatientPrescriptionGroups(groups);
        return tcmMapper.toListTcmHerbalPrescriptionsResponse(headers);
    }

    /**
     * IF-IP-TCM-35：分页检索中药饮片目录。
     *
     * @param request 含 encounterId、patientId、keyword、分页参数
     * @return 饮片目录分页（定价 only，无库存 Gate）
     * @throws BusinessException 分页非法或 Gate 失败时
     */
    public PageTcmHerbalMedicationCatalogResponse pageTcmHerbalMedicationCatalog(PageTcmHerbalMedicationCatalogRequest request) {
        // Gate：分页窗口
        pageWindowGate.requirePositivePageWindow(request,
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "分页参数非法");
        // Pre：住院快照
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        PageInpatientDoctorStationAdviceCatalogRequest catalogRequest = tcmMapper.toPageInpatientDoctorStationAdviceCatalogRequest(request);
        ProductDefinitionPageQuery pageQuery = adviceMapper.toProductDefinitionPageQuery(catalogRequest);
        // Pre：强制中草药分类
        pageQuery.setCategoryCode(DoctorStationTcmConstants.HERBAL_CATEGORY_CODE);
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
        // Asm：定价-only，无库存
        List<InpatientDoctorStationAdviceCatalogRowDto> rows =
            adviceCatalogAssembler.assemblePageContentWithoutInventoryGate(catalogRequest, page, snapshot);
        return PagedResultBuilder.build(new PageTcmHerbalMedicationCatalogResponse(), rows, page);
    }

    /**
     * IF-IP-TCM-36：暂存中药处方。
     *
     * @param request 含 encounterId、patientId、prescriptions 方头与明细
     * @return 新建行 id 列表
     * @throws BusinessException 方头校验失败、病证组未绑定或非中草药时
     */
    public SaveTcmHerbalPrescriptionDraftBatchResponse saveTcmHerbalPrescriptionDraftBatch(
        SaveTcmHerbalPrescriptionDraftBatchRequest request) {
        // Pre：住院可写快照
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_ENCOUNTER_SNAPSHOT_FAILED);
        Long orgId = snapshot.getOrganizationId();
        Set<String> validGroups = loadValidTcmGroupKeys(request.getEncounterId(), request.getPatientId());
        // Gate：方头长临/频次/一致性
        headerValidationSupport.validateTherapyAndRateAndHeaderConsistency(request.getPrescriptions());
        // Gate：中草药目录
        assertHerbalProducts(request);
        List<Long> newIds = new ArrayList<>();
        for (TcmRxDraftHeaderRequest header : request.getPrescriptions()) {
            // Gate：绑定病证组
            if (!validGroups.contains(header.getTcmSyndromeGroup())) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    InpatientDoctorStationTcmConstants.MSG_SYNDROME_GROUP_REQUIRED);
            }
            String contentJson = tcmMapper.buildHerbalContentJson(header);
            for (TcmRxDraftLineRequest line : header.getLines()) {
                String op = trimToEmpty(line.getLineOp()).toUpperCase(java.util.Locale.ROOT);
                BigDecimal quantity = line.getDose().multiply(header.getChineseHerbsDoseQuantity());
                // Cmd：按 lineOp 三选一
                if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                    Long newId = medicationRequestDomainService.createHerbalMedicationOrderDraft(
                        tcmMapper.toCreateHerbalMedicationOrderDraftCommand(
                            request, header, line, operatorId, orgId, contentJson, quantity));
                    // 非临嘱创建后补写 therapy_enum
                    if (!TherapyType.TEMPORARY.getCode().equals(header.getTherapyEnum())) {
                        medicationRequestDomainService.changeMedicationOrderDraft(
                            tcmMapper.toChangeMedicationOrderDraftForTherapy(newId, header.getTherapyEnum(), operatorId));
                    }
                    newIds.add(newId);
                } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                    medicationRequestDomainService.changeHerbalMedicationOrderDraft(
                        tcmMapper.toChangeHerbalMedicationOrderDraftCommand(
                            header, line, operatorId, contentJson, quantity, null, null));
                    medicationRequestDomainService.changeMedicationOrderDraft(
                        tcmMapper.toChangeMedicationOrderDraftForTherapy(line.getMedicationRequestId(), header.getTherapyEnum(), operatorId));
                } else if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                    medicationRequestDomainService.removeMedicationOrderDraft(
                        tcmMapper.toRemoveMedicationOrderDraftCommand(line.getMedicationRequestId(), operatorId));
                } else {
                    throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的行操作: " + line.getLineOp());
                }
            }
        }
        return tcmMapper.toSaveTcmHerbalPrescriptionDraftBatchResponse(newIds);
    }

    /**
     * IF-IP-TCM-37：签发中药处方。
     *
     * @param request 含 encounterId、patientId、signTargets
     * @return 组 key → 处方号
     * @throws BusinessException 非中药行、非草稿或方头校验失败时
     */
    public SignTcmHerbalPrescriptionBatchResponse signTcmHerbalPrescriptionBatch(SignTcmHerbalPrescriptionBatchRequest request) {
        // Pre：住院可写快照
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_ENCOUNTER_SNAPSHOT_FAILED);
        // Pre：加载草稿并分组
        List<TcmRxDraftHeaderRequest> headerSnapshots = new ArrayList<>();
        Map<String, List<Long>> groupKeyToIds = new LinkedHashMap<>();
        for (SignTcmRxTargetRequest target : request.getSignTargets()) {
            MedicationRequestListQueryModel row = medicationRequestQueryService.getListModelById(target.getMedicationRequestId());
            // Gate：须为 tcm_flag 草稿
            if (row == null || !Boolean.TRUE.equals(row.getTcmFlag())) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    InpatientDoctorStationTcmConstants.MSG_NON_TCM_LINE);
            }
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, InpatientDoctorStationTcmConstants.MSG_STATUS_NOT_ALLOWED);
            }
            List<Long> bucket = groupKeyToIds.get(target.getPrescriptionGroupKey());
            if (bucket == null) {
                bucket = new ArrayList<>();
                groupKeyToIds.put(target.getPrescriptionGroupKey(), bucket);
            }
            bucket.add(target.getMedicationRequestId());
            headerSnapshots.add(buildHeaderSnapshotFromRow(target.getPrescriptionGroupKey(), row));
        }
        // Gate：方头校验
        headerValidationSupport.validateTherapyAndRateAndHeaderConsistency(headerSnapshots);
        // Asm：取号
        Map<String, String> prescriptionNoByKey = prescriptionSignAssembler.resolvePrescriptionNoByGroupKey(request.getSignTargets());
        String rxTypeCode = prescriptionSignAssembler.deriveInpatientHerbalRxTypeCode(YbMedType.GENERAL_HOSPITALIZATION.getCode());
        // Cmd：预写 rx_type/prescription_no 后 sign
        for (SignTcmRxTargetRequest target : request.getSignTargets()) {
            String prescriptionNo = prescriptionNoByKey.get(target.getPrescriptionGroupKey());
            medicationRequestDomainService.changeHerbalMedicationOrderDraft(
                tcmMapper.toPreSignHerbalMedicationOrderDraftCommand(
                    target.getMedicationRequestId(), operatorId, prescriptionNo, rxTypeCode));
            medicationRequestDomainService.signMedicationOrder(
                tcmMapper.toSignMedicationOrderCommand(target, prescriptionNo, operatorId));
        }
        return tcmMapper.toSignTcmHerbalPrescriptionBatchResponse(prescriptionNoByKey);
    }

    /**
     * IF-IP-TCM-38：签退/删除中药处方。
     *
     * @param request 含 action（DELETE/WITHDRAW）、scope、目标行或整方标识
     * @return 操作成功标志
     * @throws BusinessException 签退门控失败、状态不允许或非中药行时
     */
    public WithdrawOrDeleteTcmHerbalPrescriptionResponse withdrawOrDeleteTcmHerbalPrescription(
        WithdrawOrDeleteTcmHerbalPrescriptionRequest request) {
        // Pre：住院可写快照
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_ENCOUNTER_SNAPSHOT_FAILED);
        // Gate：整方扩行
        List<Long> targetIds = prescriptionExpandSupport.expandToAllTcmLines(
            request.getEncounterId(), request.getScope(), request.getMedicationRequestIds(),
            request.getPrescriptionGroupKey(), request.getPrescriptionNo());
        for (Long medReqId : targetIds) {
            MedicationRequestListQueryModel row = medicationRequestQueryService.getListModelById(medReqId);
            // Pre：须为中药行
            if (row == null || !Boolean.TRUE.equals(row.getTcmFlag())) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    InpatientDoctorStationTcmConstants.MSG_NON_TCM_LINE);
            }
        }
        // Pre：签退门控
        inpatientTcmRxRevokeGate.assertNoNetExecutionOrCompletedDispenseOrLockedCharge(request.getEncounterId(), targetIds);
        for (Long medReqId : targetIds) {
            MedicationRequestListQueryModel row = medicationRequestQueryService.getListModelById(medReqId);
            String status = row.getStatusEnum();
            // Cmd：DELETE 分支
            if (DoctorStationTcmConstants.ACTION_DELETE.equals(request.getAction())) {
                if (!medicationRequestQueryService.checkMedicationRequestDeletable(medReqId)) {
                    throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, InpatientDoctorStationTcmConstants.MSG_STATUS_NOT_ALLOWED);
                }
                medicationRequestDomainService.removeMedicationOrderDraft(
                    tcmMapper.toRemoveMedicationOrderDraftCommand(medReqId, operatorId));
            // Cmd：WITHDRAW 分支
            } else if (DoctorStationTcmConstants.ACTION_WITHDRAW.equals(request.getAction())) {
                // ACTIVE 行走 signOff
                if (RequestStatus.ACTIVE.getCode().equals(status)) {
                    medicationRequestDomainService.signOffMedicationOrder(
                        tcmMapper.toSignOffMedicationOrderCommand(medReqId, operatorId));
                // VERIFIED 行走 reject
                } else if (RequestStatus.VERIFIED.getCode().equals(status)) {
                    medicationRequestDomainService.rejectMedicationOrder(
                        tcmMapper.toRejectMedicationOrderCommand(medReqId, operatorId, "DOCTOR_WITHDRAW"));
                } else {
                    throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, InpatientDoctorStationTcmConstants.MSG_STATUS_NOT_ALLOWED);
                }
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的操作类型");
            }
        }
        return tcmMapper.toWithdrawOrDeleteTcmHerbalPrescriptionResponse(Boolean.TRUE);
    }

    /**
     * IF-IP-TCM-39：查询患者历史中药处方。
     *
     * @param request 含 patientId、currentEncounterId、可选 keyword
     * @return 历史方行列表
     */
    public ListPatientTcmHerbalPrescriptionHistoryResponse listPatientTcmHerbalPrescriptionHistory(
        ListPatientTcmHerbalPrescriptionHistoryRequest request) {
        // Pre：历史方聚合
        List<TcmHerbalPrescriptionHistoryGroupQueryModel> groups =
            medicationRequestQueryService.listTcmHerbalPrescriptionHistoryByPatient(
                request.getPatientId(), request.getCurrentEncounterId());
        // Asm：enrich + 关键字过滤
        List<TcmRxHistoryRowDto> rows = prescriptionListAssembler.enrichHistoryGroups(groups, request.getKeyword());
        return tcmMapper.toListPatientTcmHerbalPrescriptionHistoryResponse(rows);
    }

    /**
     * IF-IP-TCM-40：停嘱长期中药方。
     *
     * @param request 含 encounterId、patientId、prescriptionNos、stopTime
     * @return 停嘱成功标志与提示
     * @throws BusinessException 处方号为空、行状态不允许停嘱或 Gate 失败时
     */
    public StopLongTermTcmHerbalPrescriptionResponse stopLongTermTcmHerbalPrescription(
        StopLongTermTcmHerbalPrescriptionRequest request) {
        // Gate：处方号非空
        if (request.getPrescriptionNos() == null || request.getPrescriptionNos().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "prescriptionNos 不能为空");
        }
        // Pre：住院可写快照
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_ENCOUNTER_SNAPSHOT_FAILED);
        // Pre：整方扩行
        List<Long> requestIds = prescriptionExpandSupport.resolveLongTermTcmRequestIdsByPrescriptionNos(
            request.getEncounterId(), request.getPrescriptionNos());
        // Cmd：逐行停嘱
        for (Long requestId : requestIds) {
            medicationRequestDomainService.stopLongTermMedicationOrder(
                tcmMapper.toStopLongTermMedicationOrderCommand(requestId, request.getStopTime(), operatorId));
        }
        return tcmMapper.toStopLongTermTcmHerbalPrescriptionResponse(Boolean.TRUE, InpatientDoctorStationTcmConstants.RESPONSE_MESSAGE_OK);
    }

    /**
     * IF-IP-TCM-41：取消停嘱长期中药方。
     *
     * @param request 含 encounterId、patientId、prescriptionNos
     * @return 取消停嘱成功标志与提示
     * @throws BusinessException 处方号为空、出院/转科门禁、行非 STOPPED 或 Gate 失败时
     */
    public CancelStopLongTermTcmHerbalPrescriptionResponse cancelStopLongTermTcmHerbalPrescription(
        CancelStopLongTermTcmHerbalPrescriptionRequest request) {
        // Gate：处方号非空
        if (request.getPrescriptionNos() == null || request.getPrescriptionNos().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "prescriptionNos 不能为空");
        }
        // Pre：住院快照
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        // Pre：出院/转科门禁
        assertCancelStopDischargeGate(request.getEncounterId(), snapshot);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_ENCOUNTER_SNAPSHOT_FAILED);
        // Pre：整方 STOPPED 长期行
        List<Long> requestIds = prescriptionExpandSupport.resolveStoppedLongTermTcmRequestIdsByPrescriptionNos(
            request.getEncounterId(), request.getPrescriptionNos());
        // Cmd：逐行取消停嘱
        for (Long requestId : requestIds) {
            medicationRequestDomainService.cancelStopLongTermMedicationOrder(
                tcmMapper.toCancelStopLongTermMedicationOrderCommand(requestId, operatorId));
        }
        return tcmMapper.toCancelStopLongTermTcmHerbalPrescriptionResponse(Boolean.TRUE, InpatientDoctorStationTcmConstants.RESPONSE_MESSAGE_OK);
    }

    /**
     * 加载住院快照并校验患者、就诊类别与可写性。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @param writable    true 时校验可写 Gate
     * @return 住院就诊上下文快照
     * @throws BusinessException Gate 失败时
     */
    private InpatientEncounterContextQueryModel requireInpatientSnapshot(Long encounterId, Long patientId, boolean writable) {
        InpatientEncounterContextQueryModel snapshot =
            this.inpatientDoctorStationEncounterVisibilityResolver.assertVisible(encounterId, patientId);
        adviceMapper.validateInpatientEncounterClassImp(snapshot);
        // 写路径须通过可写 Gate（在院、未结算等）
        if (writable) {
            adviceMapper.validateInpatientEncounterWritableGate(snapshot);
        }
        return snapshot;
    }

    /**
     * 校验病证组批次：病名/证候必填、主诊断组不超过 1 个。
     *
     * @param request 保存批次请求
     * @throws BusinessException 组不完整或主诊断超限时
     */
    private void validateTcmDiagnosisBatch(SaveTcmDiagnosisGroupsBatchRequest request) {
        int primaryCount = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            // 每组须同时有病名与证候 definition
            if (group.getDiseaseDefinitionId() == null || group.getSyndromeDefinitionId() == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_DIAGNOSIS_GROUP_INCOMPLETE);
            }
            if (group.getPrimaryFlag() != null
                && group.getPrimaryFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                primaryCount++;
            }
        }
        // 主诊断组唯一
        if (primaryCount > 1) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, InpatientDoctorStationTcmConstants.MSG_PRIMARY_GROUP_EXCEEDED);
        }
    }

    /**
     * 为无组号的病证组分配稳定 tcmSyndromeGroup（新建组取序列号）。
     *
     * @param request 保存批次请求（会回写 group.tcmSyndromeGroup）
     * @return 病名:证候 → 组号映射
     */
    private Map<String, String> assignTcmGroupKeys(SaveTcmDiagnosisGroupsBatchRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        int newCount = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            // 统计需新分配组号的项
            if (StringUtils.isEmpty(group.getTcmSyndromeGroup())) {
                newCount++;
            }
        }
        List<String> newKeys = new ArrayList<>();
        // 批量取新组号
        if (newCount > 0) {
            newKeys = businessSequenceService.getSeqByDay(
                AggregateTypeConstants.CONDITION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, newCount);
        }
        int idx = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            String stableKey = String.valueOf(group.getDiseaseDefinitionId()) + ":" + String.valueOf(group.getSyndromeDefinitionId());
            String key;
            // 已有组号沿用，否则分配新序列
            if (StringUtils.isNotEmpty(group.getTcmSyndromeGroup())) {
                key = group.getTcmSyndromeGroup().trim();
            } else {
                key = newKeys.get(idx++);
            }
            map.put(stableKey, key);
            group.setTcmSyndromeGroup(key);
        }
        return map;
    }

    /**
     * 删除单个中医病证组：校验无医嘱引用后移除关联与诊断事实。
     *
     * @param encounterId 就诊主键
     * @param groupKey    中医组号
     * @param existing    当前快照中的病证组
     * @param operatorId  操作人
     * @throws BusinessException 组不存在或被医嘱引用时
     */
    private void deleteTcmGroupInternal(
        Long encounterId, String groupKey, List<TcmDiagnosisGroupRowDto> existing, Long operatorId) {
        TcmDiagnosisGroupRowDto target = null;
        for (TcmDiagnosisGroupRowDto row : existing) {
            if (groupKey.equals(row.getTcmSyndromeGroup())) {
                target = row;
                break;
            }
        }
        // 组号在快照中不存在
        if (target == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_NOT_FOUND, "病证组不存在，请刷新后重试");
        }
        Long[] conditionIds = new Long[] {target.getDiseaseConditionId(), target.getSyndromeConditionId()};
        for (Long conditionId : conditionIds) {
            if (conditionId == null) {
                continue;
            }
            // 病名/证候被医嘱引用则禁止删除
            if (conditionQueryService.countAdviceRefByConditionId(conditionId) > 0) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, InpatientDoctorStationTcmConstants.MSG_CONDITION_REFERENCED);
            }
        }
        for (Long conditionId : conditionIds) {
            if (conditionId == null) {
                continue;
            }
            RemoveEncounterDiagnosisAssociationCommand rm = new RemoveEncounterDiagnosisAssociationCommand();
            RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item =
                new RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand();
            item.setEncounterId(encounterId);
            item.setConditionId(conditionId);
            item.setRemoveReason("TCM_GROUP_DELETE");
            rm.setItem(item);
            rm.setOperatorId(operatorId);
            encounterDomainService.removeEncounterDiagnosisAssociation(rm);
            DelEncounterDiagnosisCommand del = new DelEncounterDiagnosisCommand();
            del.setConditionId(conditionId);
            del.setOperatorId(operatorId);
            del.setReason("TCM_GROUP_DELETE");
            conditionDomainService.delEncounterDiagnosis(del);
        }
    }

    /**
     * 加载当前就诊有效中医病证组号集合（供处方绑定校验）。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @return 有效组号集合
     */
    private Set<String> loadValidTcmGroupKeys(Long encounterId, Long patientId) {
        ListTcmDiagnosisGroupsRequest req = new ListTcmDiagnosisGroupsRequest().setEncounterId(encounterId).setPatientId(patientId);
        ListTcmDiagnosisGroupsResponse resp = listTcmDiagnosisGroups(req);
        Set<String> keys = new HashSet<>();
        if (resp.getGroups() != null) {
            for (TcmDiagnosisGroupRowDto g : resp.getGroups()) {
                keys.add(g.getTcmSyndromeGroup());
            }
        }
        return keys;
    }

    /**
     * 校验暂存请求中所有非 DELETE 行均为中草药分类。
     *
     * @param request 暂存批次请求
     * @throws BusinessException 存在非中草药产品时
     */
    private void assertHerbalProducts(SaveTcmHerbalPrescriptionDraftBatchRequest request) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (TcmRxDraftHeaderRequest header : request.getPrescriptions()) {
            for (TcmRxDraftLineRequest line : header.getLines()) {
                String op = trimToEmpty(line.getLineOp()).toUpperCase(java.util.Locale.ROOT);
                // DELETE 行无需校验产品
                if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                    continue;
                }
                if (line.getMedicationDefinitionId() == null) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(line.getMedicationDefinitionId());
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return;
        }
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        headerValidationSupport.assertHerbalCategoryOnly(new ArrayList<>(map.values()));
    }

    /**
     * 从药品申请行还原方头快照（供签发前校验）。
     *
     * @param groupKey 处方组 key
     * @param row      药品申请列表读模型
     * @return 方头草稿请求（含占位明细行）
     */
    private TcmRxDraftHeaderRequest buildHeaderSnapshotFromRow(String groupKey, MedicationRequestListQueryModel row) {
        TcmRxDraftHeaderRequest header = new TcmRxDraftHeaderRequest();
        header.setPrescriptionGroupKey(groupKey);
        header.setTherapyEnum(row.getTherapyEnum());
        header.setTcmSyndromeGroup(com.openhis.health.application.shared.advice.TcmMethodCodeDisplayAssembler.readContentJsonTextField(
            row.getContentJson(), "tcmSyndromeGroup"));
        header.setChineseHerbsDoseQuantity(row.getChineseHerbsDoseQuantity());
        header.setSufferingFlag(row.getSufferingFlag());
        header.setMethodCode(row.getMethodCode());
        header.setRateCode(row.getRateCode());
        header.setDays(row.getDispensePerDuration());
        header.setLines(List.of(new TcmRxDraftLineRequest()));
        return header;
    }

    /**
     * 取消停嘱门禁：禁止在出院/转科流程中取消停嘱。
     *
     * @param encounterId 就诊主键
     * @param snapshot    住院快照
     * @throws BusinessException 存在转科/出院申请或已出院时
     */
    private void assertCancelStopDischargeGate(Long encounterId, InpatientEncounterContextQueryModel snapshot) {
        // 存在在途转科/出院申请
        if (Boolean.TRUE.equals(serviceRequestQueryService.checkDuplicateTransferOrDischargeRequest(encounterId))) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                InpatientDoctorStationTcmConstants.MSG_CANCEL_STOP_DISCHARGE_GATE);
        }
        // 就诊已标记出院
        if (CustomConstants.EncounterHistoryMilestoneName.DISCHARGE.equals(snapshot.getDischargeTypeEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                InpatientDoctorStationTcmConstants.MSG_CANCEL_STOP_DISCHARGE_GATE);
        }
    }
}
