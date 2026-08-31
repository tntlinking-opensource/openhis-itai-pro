/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapper;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.assembler.*;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.*;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.mapper.DoctorStationTcmMapper;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.policy.TcmDiagnosisGroupPairingPolicy;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request.*;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response.*;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisReferenceSupport;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.application.shared.financial.EncounterChargeAccountOpener;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.ReleaseDispenseReserveCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeHerbalMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ClearHerbalSufferingFlagCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CreateHerbalMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.MarkHerbalSufferingFlagCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.*;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.application.outpatient.doctorstation.advice.support.OutpatientMedicationSignChargeOrchestrator;
import com.openhis.health.application.outpatient.financial.charge.support.OutpatientPaymentDraftLifecycleOrchestrator;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.outpatient.doctorstation.advice.support.OrderRequestFifoSignContextLoader;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.application.shared.product.DoctorStationCatalogPositiveStockLocationBinder;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 门诊医生站中医 Tab 编排（IF-DOR-30～39；另含已签发方代煎费取消/补充）。
 *
 * <p>职责边界：病证组成组 CRUD、中药处方暂存/签发/签退、饮片目录与历史方只读；组套维护不在本服务。</p>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/doctorStationTcm/{methodName}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorStationTcmAppService {

    /** 写路径操作人解析。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 门诊就诊读模型门控。 */
    private final EncounterContextReader EncounterContextReader;
    /** 中医 Tab 字段映射。 */
    private final DoctorStationTcmMapper tcmMapper;
    /** 病证组聚合。 */
    private final TcmDiagnosisAssembler diagnosisAssembler;
    /** 病证组成组完整性策略（默认一病一证；院点可 @Primary 放宽）。 */
    private final TcmDiagnosisGroupPairingPolicy tcmDiagnosisGroupPairingPolicy;
    /** 处方列表 enrich。 */
    private final TcmRxListAssembler prescriptionListAssembler;
    /** 签发取号。 */
    private final TcmRxSignAssembler prescriptionSignAssembler;
    /** 代煎费。 */
    private final TcmDecoctionAssembler decoctionFeeAssembler;
    /** 诊断快照。 */
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    /** 诊断定义分页。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    /** 就诊诊断关联写。 */
    private final EncounterDomainService encounterDomainService;
    /** 就诊诊断读。 */
    private final EncounterQueryService encounterQueryService;
    /** 就诊诊断引用门禁（医嘱/账单 ids 串）。 */
    private final EncounterDiagnosisReferenceSupport encounterDiagnosisReferenceSupport;
    /** 药品申请读。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 药品申请写。 */
    private final MedicationRequestDomainService medicationRequestDomainService;
    /** 发放写。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 发放读。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 费用写。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** 就诊挂账账户解析（饮片默认医保户；代煎 preferInsurance=false 挂个人现金自费户）。 */
    private final EncounterChargeAccountOpener encounterChargeAccountOpener;
    /** 合同读侧：方头 contractId → bus_no，供挂账账户解析。 */
    private final ContractQueryService contractQueryService;
    /** 业务序列。 */
    private final BusinessSequenceService businessSequenceService;
    /** 医嘱开立映射复用（费用/签发命令）。 */
    private final DoctorStationAdviceMapper doctorStationAdviceMapper;
    /** 目录装配。 */
    private final DoctorStationAdviceCatalogAssembler doctorStationAdviceCatalogAssembler;
    /** 目录分页加载。 */
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    /** 医生站「只看库存>0」：模板库位下推至目录分页 Spec。 */
    private final DoctorStationCatalogPositiveStockLocationBinder doctorStationCatalogPositiveStockLocationBinder;
    /** FIFO 上下文。 */
    private final OrderRequestFifoSignContextLoader orderRequestFifoSignContextLoader;
    /** 定价解析（暂存挂费）。 */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    /** 门诊药品签发 FIFO 分段挂费（与 IF-DOR-15 共用）。 */
    private final OutpatientMedicationSignChargeOrchestrator outpatientMedicationSignChargeOrchestrator;
    /** 产品定义读模型批量加载（暂存挂费与 IF-DOR-14 西药口径一致）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 分页窗口门控。 */
    private final PageWindowGate pageWindowGate;
    /** 术语码表读（中药用法 tcm_method_code）。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 用药频次下拉（与医嘱 Tab 同源）。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 签退后作废陈旧预结算草稿（GAP-STALE-001）。 */
    private final OutpatientPaymentDraftLifecycleOrchestrator outpatientPaymentDraftLifecycleOrchestrator;

    /**
     * 中医 Tab 页初始化：下发中药用法与用药频次下拉。
     *
     * @param request 就诊与患者门控
     * @return 中药用法与频次术语
     */
    public InitDoctorStationTcmResponse initDoctorStationTcm(InitDoctorStationTcmRequest request) {
        // Gate：门诊快照加载并校验患者与就诊绑定一致，防止跨就诊误初始化下拉
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        InitDoctorStationTcmResponse resp = new InitDoctorStationTcmResponse();
        // Pre：中药用法走 tcm_method_code 术语，不含西医 method_code
        resp.setTcmMethodCodeOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE));
        // Asm：用药频次与医嘱 Tab IF-DOR-18 同源（cus_frequency 主数据）
        resp.setMedicationRateOptions(frequencyOptionLoader.loadActiveRateOptions());
        // Pre：单味特殊煎法走 dosage_instruction 术语，与 med_medication_request.dosage_instruction 同源
        resp.setDosageInstructionOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION));
        return resp;
    }

    /**
     * IF-DOR-30：加载当前就诊中医病证组列表。
     *
     * @param request 含 encounterId、patientId
     * @return 按 tcm_syndrome_group 聚合的病证组列表
     */
    public ListTcmDiagnosisGroupsResponse listTcmDiagnosisGroups(ListTcmDiagnosisGroupsRequest request) {
        // Pre：门诊就诊快照
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者与就诊须同属一次接诊
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        String diagnosisJson = encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson();
        // Asm：按 tcm_syndrome_group 聚合一病一证
        List<TcmDiagnosisGroupRowDto> groups = diagnosisAssembler.groupTcmDiagnosisRows(request.getEncounterId(), diagnosisJson);
        return tcmMapper.toListTcmDiagnosisGroupsResponse(groups);
    }

    /**
     * IF-DOR-31：分页检索中医病名/证候目录。
     *
     * @param request 含 catalogRole（DISEASE/SYNDROME）、keyword、分页参数
     * @return 有效目录分页结果
     */
    public PageTcmConditionDefinitionResponse pageTcmConditionDefinitionCatalog(PageTcmConditionDefinitionRequest request) {
        // Gate：分页窗口须为正
        pageWindowGate.requirePositivePageWindow(request,
            ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "分页参数非法");
        List<String> sourceIn;
        // Gate：catalogRole 决定检索 source=05 病名或 source=06 证候
        if (DoctorStationTcmConstants.CATALOG_ROLE_DISEASE.equals(request.getCatalogRole())) {
            sourceIn = DoctorStationTcmConstants.TCM_DISEASE_SOURCES;
        } else if (DoctorStationTcmConstants.CATALOG_ROLE_SYNDROME.equals(request.getCatalogRole())) {
            sourceIn = DoctorStationTcmConstants.TCM_SYNDROME_SOURCES;
        } else {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "每组须同时选择病名与证候");
        }
        // Pre：仅 ACTIVE 目录分页读
        Page<DoctorStationConditionDefinitionRowModel> page = conditionDefinitionQueryService.pageActiveDoctorStationDefinitions(
            request.getKeyword(), sourceIn, request.getPageNo(), request.getPageSize());
        // Asm：领域行转展示 DTO（含医保对码，供开诊弹窗列表展示）
        List<TcmDiagnosisCatalogRowDto> rows = new ArrayList<>();
        for (DoctorStationConditionDefinitionRowModel model : page.getContent()) {
            TcmDiagnosisCatalogRowDto row = new TcmDiagnosisCatalogRowDto();
            row.setDefinitionId(model.getId());
            row.setConditionCode(model.getConditionCode());
            row.setConditionName(model.getName());
            row.setSourceCode(model.getSourceEnum());
            row.setSourceText(model.getSourceEnum());
            // 透传目录医保编码；空值由前端显示「-」
            row.setYbNo(model.getYbNo());
            rows.add(row);
        }
        return tcmMapper.toPageTcmConditionDefinitionResponse(page, rows);
    }

    /**
     * IF-DOR-32：全量保存中医病证组（一病一证成组）。
     *
     * @param request 全量病证组批次；空组拒绝
     * @return 保存后的组号列表
     */
    public SaveTcmDiagnosisGroupsBatchResponse saveTcmDiagnosisGroupsBatch(SaveTcmDiagnosisGroupsBatchRequest request) {
        // Pre：门诊就诊快照
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        // Gate：成组完整性由 TcmDiagnosisGroupPairingPolicy 决定（默认一病一证；院点可放宽）
        validateTcmDiagnosisBatch(request);
        String diagnosisJson = encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson();
        List<TcmDiagnosisGroupRowDto> existing = diagnosisAssembler.groupTcmDiagnosisRows(request.getEncounterId(), diagnosisJson);
        List<String> removedGroups = tcmMapper.diffRemovedTcmGroups(existing, request);
        // Cmd：全量 diff 后先删不在批次内的历史组
        for (String removed : removedGroups) {
            deleteTcmGroupInternal(request.getEncounterId(), removed, existing, operatorId);
        }
        // Gate：保留组若改病/证目录定义且仍被医嘱/账单引用则拒绝（DD §8.1）
        this.assertTcmRetainedGroupsContentChangeAllowed(existing, request);
        // Pre：为新组分配 tcm_syndrome_group 序列号
        Map<String, String> groupKeyByItem = assignTcmGroupKeys(request);
        // Cmd：batchUpsertTcm 直挂 conditionDefinitionId，不经 Condition
        encounterDomainService.batchUpsertTcmEncounterDiagnosisAssociation(
            tcmMapper.toBatchUpsertTcmEncounterDiagnosisAssociationCommand(request, groupKeyByItem, operatorId));
        // Asm：返回已保存组号
        return tcmMapper.toSaveTcmDiagnosisGroupsBatchResponse(new ArrayList<>(new LinkedHashSet<>(groupKeyByItem.values())));
    }

    /**
     * IF-DOR-33：删除指定中医病证组。
     *
     * @param request 含 tcmSyndromeGroup、encounterId、patientId
     * @return 删除是否成功
     */
    public DeleteTcmDiagnosisGroupResponse deleteTcmDiagnosisGroup(DeleteTcmDiagnosisGroupRequest request) {
        // Pre：门诊就诊快照
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        String diagnosisJson = encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson();
        List<TcmDiagnosisGroupRowDto> existing = diagnosisAssembler.groupTcmDiagnosisRows(request.getEncounterId(), diagnosisJson);
        // Cmd：校验引用后删除组内关联与事实
        deleteTcmGroupInternal(request.getEncounterId(), request.getTcmSyndromeGroup(), existing, operatorId);
        return tcmMapper.toDeleteTcmDiagnosisGroupResponse(Boolean.TRUE);
    }

    /**
     * IF-DOR-34：加载当前就诊中药处方列表（按方聚合）。
     *
     * @param request 含 encounterId、patientId
     * @return 方头+明细列表
     */
    public ListTcmRxResponse listTcmHerbalPrescriptions(ListTcmRxRequest request) {
        // Pre：门诊就诊快照
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        // Pre：领域层按 prescriptionGroupKey（优先）聚合同方行
        List<TcmHerbalPrescriptionGroupQueryModel> groups = medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
            request.getEncounterId(), GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // Asm：enrich 目录名与费用态
        List<TcmRxHeaderDto> headers = prescriptionListAssembler.toPrescriptionHeaders(groups);
        return tcmMapper.toListTcmRxResponse(headers);
    }

    /**
     * IF-DOR-35：分页检索中药饮片目录（读路径与 IF-DOR-11 同构，仅强制中草药 category）。
     *
     * <p>选品下拉与西药一致：同名多厂家/多目录各行展示，不做同名折叠。
     * 暂存身份仍绑所选目录；签发写路径仍按库存 FIFO 跨批分段发放与挂费。</p>
     *
     * @param request 含 encounterId、keyword、prepickedProductKeys、分页参数
     * @return 与 IF-DOR-11 相同的目录行契约（单位/单价/库存预减口径一致）
     */
    public PageTcmMedCatalogResponse pageTcmHerbalMedicationCatalog(PageTcmMedCatalogRequest request) {
        // Gate：分页窗口须为正
        pageWindowGate.requirePositivePageWindow(request,
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "分页参数非法");
        // Pre：就诊上下文用于库存预减与定价
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        PageDoctorStationAdviceCatalogRequest catalogRequest = tcmMapper.toPageDoctorStationAdviceCatalogRequest(request);
        ProductDefinitionPageQuery pageQuery = doctorStationAdviceMapper.toProductDefinitionPageQuery(catalogRequest);
        // Pre：只看库存>0 时按取药模板库位收窄 EXISTS
        Long catalogOrgId = snapshot.getOrganizationId();
        if (catalogRequest.getOrganizationUnitId() != null) {
            catalogOrgId = catalogRequest.getOrganizationUnitId();
        }
        doctorStationCatalogPositiveStockLocationBinder.bindIfPositiveQuantityOnly(
            pageQuery, catalogOrgId, catalogRequest.getPharmacyLocationId());
        // Pre：仅中草药分类，其余读侧与 IF-DOR-11 共用装配器
        pageQuery.setCategoryCode(DoctorStationTcmConstants.HERBAL_CATEGORY_CODE);
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
        // Asm：与西药目录同构下发库存预减与定价，不做同名折叠
        List<DoctorStationAdviceCatalogRowDto> rows =
            doctorStationAdviceCatalogAssembler.assemblePageContent(catalogRequest, page, snapshot);
        return PagedResultBuilder.build(new PageTcmMedCatalogResponse(), rows, page);
    }

    /**
     * IF-DOR-36：暂存一张或多张中药处方（方头+明细行）。
     *
     * @param request 含 prescriptions、prescriptionGroupKey、病证组绑定
     * @return 新建药品申请 id 列表
     */
    public SaveTcmRxDraftBatchResponse saveTcmHerbalPrescriptionDraftBatch(
        SaveTcmRxDraftBatchRequest request) {
        // Pre：门诊就诊快照
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        Set<String> validGroups = loadValidTcmGroupKeys(request.getEncounterId(), request.getPatientId());
        // Pre：病证组 → 同源字段 encounter_diagnosis_ids（病+证 有序 ids 串，主诊病名优先；与西医开嘱同一列）
        Map<String, String> encounterDiagnosisIdsByGroup =
            this.buildEncounterDiagnosisIdsByTcmGroup(request.getEncounterId(), request.getPatientId());
        List<Long> newIds = new ArrayList<>();
        for (TcmRxDraftHeaderRequest header : request.getPrescriptions()) {
            // Gate：每张方须绑定已存在的病证组
            if (!validGroups.contains(header.getTcmSyndromeGroup())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "请先维护并绑定中医病证组");
            }
            // 方头可显式传多选就诊诊断 ids；未传时回落病证组 → 病+证有序串
            String encounterDiagnosisIds;
            if (StringUtils.isNotEmpty(header.getEncounterDiagnosisIds())) {
                encounterDiagnosisIds = header.getEncounterDiagnosisIds().trim();
            } else {
                encounterDiagnosisIds = encounterDiagnosisIdsByGroup.get(header.getTcmSyndromeGroup());
            }
            String contentJson = tcmMapper.buildHerbalContentJson(header);
            for (TcmRxDraftLineRequest line : header.getLines()) {
                String op = trimToEmpty(line.getLineOp()).toUpperCase(java.util.Locale.ROOT);
                // DELETE 仅需 medicationRequestId，不要求 dose / 付数；先作废未计费费用草稿再删药品申请
                if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                    // Gate：删除行必须属于当前就诊/患者
                    this.assertMedicationRequestBelongsToEncounterPatient(
                        request.getEncounterId(), request.getPatientId(),
                        this.requireMedicationRequestListModel(line.getMedicationRequestId()));
                    chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                        request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(line.getMedicationRequestId()));
                    medicationRequestDomainService.removeMedicationOrderDraft(
                        doctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(line.getMedicationRequestId(), operatorId));
                    continue;
                }
                // Gate：CREATE/UPDATE 缺单剂用量则无法按 BR-TCM-RX-005 计算请求数量
                if (line.getDose() == null) {
                    throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "单剂用量不能为空");
                }
                // Gate：CREATE/UPDATE 缺付数则无法按 BR-TCM-RX-005 计算请求数量
                if (header.getChineseHerbsDoseQuantity() == null) {
                    throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "付数不能为空");
                }
                BigDecimal quantity = line.getDose().multiply(header.getChineseHerbsDoseQuantity());
                // Pre：以行为主解析挂账账户（行 contractId 优先，空回落方头；双账户默认医保户）
                Long lineContractId = line.getContractId();
                if (lineContractId == null) {
                    lineContractId = header.getContractId();
                }
                Long accountId = this.resolveTcmChargeAccountId(request.getEncounterId(), lineContractId);
                // Cmd：按 lineOp 执行 CREATE / UPDATE
                if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                    CreateHerbalMedicationOrderDraftCommand createCmd = tcmMapper.toCreateHerbalMedicationOrderDraftCommand(
                        request, header, line, operatorId, orgId, contentJson, quantity);
                    createCmd.setEncounterDiagnosisIds(encounterDiagnosisIds);
                    // Pre：签退回草稿后同方续开——继承同 prescriptionGroupKey 已有处方号，避免签发时另取新号
                    if (StringUtils.isEmpty(createCmd.getPrescriptionNo())) {
                        String existingPrescriptionNo = medicationRequestQueryService.findTcmPrescriptionNoByGroupKey(
                            request.getEncounterId(),
                            GenerateSource.DOCTOR_PRESCRIPTION.getCode(),
                            header.getPrescriptionGroupKey());
                        if (StringUtils.isNotEmpty(existingPrescriptionNo)) {
                            createCmd.setPrescriptionNo(existingPrescriptionNo);
                        }
                    }
                    Long newId = medicationRequestDomainService.createHerbalMedicationOrderDraft(createCmd);
                    createHerbalCharge(request, accountId, orgId, operatorId, newId, header, line, quantity);
                    newIds.add(newId);
                } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                    // Gate：更新行必须属于当前就诊/患者
                    this.assertMedicationRequestBelongsToEncounterPatient(
                        request.getEncounterId(), request.getPatientId(),
                        this.requireMedicationRequestListModel(line.getMedicationRequestId()));
                    chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                        doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                            request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST,
                            line.getMedicationRequestId(), operatorId));
                    ChangeHerbalMedicationOrderDraftCommand changeCmd = tcmMapper.toChangeHerbalMedicationOrderDraftCommand(
                        header, line, operatorId, contentJson, quantity, null, null);
                    changeCmd.setEncounterDiagnosisIds(encounterDiagnosisIds);
                    medicationRequestDomainService.changeHerbalMedicationOrderDraft(changeCmd);
                    createHerbalCharge(request, accountId, orgId, operatorId, line.getMedicationRequestId(), header, line, quantity);
                } else {
                    // 未知 lineOp：拒绝写入，避免静默跳过导致草稿与费用不一致
                    throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的行操作: " + line.getLineOp());
                }
            }
        }
        return tcmMapper.toSaveTcmRxDraftBatchResponse(newIds);
    }

    /**
     * IF-DOR-37：签发中药处方（按 prescriptionGroupKey 取号，禁止 splitPrescription）。
     *
     * @param request 含 signTargets（方标识 + 药品申请 id）
     * @return 方标识 → 处方号
     */
    public SignTcmRxBatchResponse signTcmHerbalPrescriptionBatch(SignTcmRxBatchRequest request) {
        // Pre：门诊就诊快照与挂账账户（双账户默认医保户）
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        // Pre：按 prescriptionGroupKey 分组，并断言均为 tcm_flag 草稿
        List<Long> herbalMedicationRequestIds = new ArrayList<>();
        for (SignTcmRxTargetRequest target : request.getSignTargets()) {
            herbalMedicationRequestIds.add(target.getMedicationRequestId());
        }
        // Pre：批量装载列表模型与 FIFO 上下文（与逐条查询同口径，仅减 round-trip）
        Map<Long, MedicationRequestListQueryModel> herbalListById = new HashMap<>();
        List<MedicationRequestListQueryModel> herbalListRows =
            medicationRequestQueryService.listMedicationRequestListModelsByIds(herbalMedicationRequestIds);
        for (MedicationRequestListQueryModel row : herbalListRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            herbalListById.put(row.getId(), row);
        }
        Map<Long, MedicationRequestFifoSignContext> herbalFifoById =
            orderRequestFifoSignContextLoader.loadMedicationFifoSignContextsOrThrow(herbalMedicationRequestIds);
        OutpatientMedicationSignChargeOrchestrator.SignChargeReadCache signChargeReadCache =
            new OutpatientMedicationSignChargeOrchestrator.SignChargeReadCache();
        Set<Long> herbalDefinitionIds = new LinkedHashSet<>();
        for (MedicationRequestFifoSignContext fifoCtx : herbalFifoById.values()) {
            if (fifoCtx == null || fifoCtx.getMedicationId() == null) {
                continue;
            }
            herbalDefinitionIds.add(fifoCtx.getMedicationId());
        }
        outpatientMedicationSignChargeOrchestrator.prefetchMedicationCatalogReads(signChargeReadCache, herbalDefinitionIds);
        Map<String, List<Long>> groupKeyToIds = new LinkedHashMap<>();
        for (SignTcmRxTargetRequest target : request.getSignTargets()) {
            MedicationRequestListQueryModel row = herbalListById.get(target.getMedicationRequestId());
            // Gate：非中药行须在对应 Tab 签发（BR-TCM-ISO-002）
            if (row == null || !Boolean.TRUE.equals(row.getTcmFlag())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "请在对应 Tab 签发");
            }
            // Gate：医嘱必须属于当前就诊/患者，防止切患者后串挂费
            this.assertMedicationRequestBelongsToEncounterPatient(
                request.getEncounterId(), request.getPatientId(), row);
            if (!RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "当前状态不允许签发");
            }
            List<Long> bucket = groupKeyToIds.get(target.getPrescriptionGroupKey());
            if (bucket == null) {
                bucket = new ArrayList<>();
                groupKeyToIds.put(target.getPrescriptionGroupKey(), bucket);
            }
            bucket.add(target.getMedicationRequestId());
        }
        // Pre：按 key 取/复用 prescription_no（禁止 splitPrescription）
        Map<String, String> prescriptionNoByKey = prescriptionSignAssembler.resolvePrescriptionNoByGroupKey(
            request.getEncounterId(), groupKeyToIds);
        String rxTypeCode = prescriptionSignAssembler.deriveHerbalRxTypeCode(YbMedType.GENERAL_OUTPATIENT.getCode());
        // Cmd：签发前预写处方号与 rx_type_code
        for (SignTcmRxTargetRequest target : request.getSignTargets()) {
            String prescriptionNo = prescriptionNoByKey.get(target.getPrescriptionGroupKey());
            medicationRequestDomainService.changeHerbalMedicationOrderDraft(
                tcmMapper.toPreSignHerbalMedicationOrderDraftCommand(
                    target.getMedicationRequestId(), operatorId, prescriptionNo, rxTypeCode));
        }
        // Cmd：逐行 sign → FIFO 发放 → PLANNED 费用
        for (SignTcmRxTargetRequest target : request.getSignTargets()) {
            String prescriptionNo = prescriptionNoByKey.get(target.getPrescriptionGroupKey());
            SignMedicationOrderCommand signCmd = doctorStationAdviceMapper.toSignMedicationOrderCommand(
                target.getMedicationRequestId(), prescriptionNo, operatorId);
            medicationRequestDomainService.signMedicationOrder(signCmd);
            MedicationRequestFifoSignContext fifoCtx = herbalFifoById.get(target.getMedicationRequestId());
            if (fifoCtx == null) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品申请不存在");
            }
            // Pre：方头处方号写回 FIFO 切片（装载早于预写 prescription_no）
            fifoCtx.setPrescriptionNo(prescriptionNo);
            // Pre：按行已落库 contractId 解析挂账，避免整批默认医保覆盖医生选自费
            MedicationRequestListQueryModel herbalRow = herbalListById.get(target.getMedicationRequestId());
            Long lineContractId = null;
            if (herbalRow != null) {
                lineContractId = herbalRow.getContractId();
            }
            Long lineAccountId = this.resolveTcmChargeAccountId(request.getEncounterId(), lineContractId);
            // Cmd：FIFO 发放 + 分段挂费 + PLANNED（与 IF-DOR-15 药线共用 Support；FIFO 仍逐条）
            outpatientMedicationSignChargeOrchestrator.replaceFifoDispenseAndPlanChargesAfterMedicationSigned(
                request.getEncounterId(), request.getPatientId(), lineAccountId, orgId, operatorId,
                target.getMedicationRequestId(), fifoCtx, signChargeReadCache);
        }
        // Pre：代煎强制挂个人现金自费户（与转自费目标户同口径）；饮片按行方头费用性质挂账
        Long decoctionSelfPayAccountId = encounterChargeAccountOpener.resolveChargeAccountId(
            request.getEncounterId(), null, false);
        // Cmd：按方头代煎标志生成或 void 代煎费
        for (Map.Entry<String, List<Long>> entry : groupKeyToIds.entrySet()) {
            MedicationRequestListQueryModel first = herbalListById.get(entry.getValue().get(0));
            if (first != null && Boolean.TRUE.equals(first.getSufferingFlag())) {
                // Cmd：方头代煎=是，签发 companion 代煎 SR 并按付数挂 PLANNED 费用（账单挂自费户）
                decoctionFeeAssembler.replacePlannedDecoctionCharge(
                    request.getEncounterId(), request.getPatientId(), decoctionSelfPayAccountId, orgId, operatorId,
                    prescriptionNoByKey.get(entry.getKey()), first.getChineseHerbsDoseQuantity(),
                    first, entry.getValue().get(0));
            } else {
                // Cmd：方头未勾选代煎，清理同处方号历史代煎 SR 与未计费费用
                decoctionFeeAssembler.voidDecoctionChargeByPrescriptionNo(
                    request.getEncounterId(), prescriptionNoByKey.get(entry.getKey()), operatorId);
            }
        }
        return tcmMapper.toSignTcmRxBatchResponse(prescriptionNoByKey);
    }

    /**
     * IF-DOR-38：签退或删除中药处方/行。
     *
     * @param request 含 action（WITHDRAW/DELETE）、scope、medicationRequestIds
     * @return 操作是否成功
     */
    public WithdrawOrDeleteTcmRxResponse withdrawOrDeleteTcmHerbalPrescription(
        WithdrawOrDeleteTcmRxRequest request) {
        // Pre：门诊就诊快照
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        for (Long medReqId : request.getMedicationRequestIds()) {
            // Cmd：签退路径——费用回退、signOff、释放预占
            if (DoctorStationTcmConstants.ACTION_WITHDRAW.equals(request.getAction())) {
                if (!medicationRequestQueryService.checkMedicationRequestSignOffAllowed(medReqId)) {
                    throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "当前状态不允许撤回");
                }
                List<Long> dispenseIds = medicationDispenseQueryService.listMedicationDispenseIdsByMedicationRequest(medReqId);
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, dispenseIds);
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, medReqId);
                medicationRequestDomainService.signOffMedicationOrder(
                    doctorStationAdviceMapper.toSignOffMedicationOrderCommand(medReqId, operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, dispenseIds);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(medReqId));
                ReleaseDispenseReserveCommand release = new ReleaseDispenseReserveCommand();
                release.setMedReqId(medReqId);
                release.setOperatorId(operatorId);
                medicationDispenseDomainService.releaseDispenseReserve(release);
            // Cmd：删除路径——仅 DRAFT 行可物理删
            } else if (DoctorStationTcmConstants.ACTION_DELETE.equals(request.getAction())) {
                if (!medicationRequestQueryService.checkMedicationRequestDeletable(medReqId)) {
                    throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "当前状态不允许删除");
                }
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(medReqId));
                medicationRequestDomainService.removeMedicationOrderDraft(
                    doctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(medReqId, operatorId));
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的操作类型");
            }
        }
        // Cmd：整方撤回时同步清理同处方号代煎 companion SR 与费用
        if (DoctorStationTcmConstants.ACTION_WITHDRAW.equals(request.getAction())
            && DoctorStationTcmConstants.SCOPE_PRESCRIPTION.equals(request.getScope())
            && StringUtils.isNotEmpty(request.getPrescriptionGroupKey())) {
            MedicationRequestListQueryModel row = medicationRequestQueryService.getListModelById(request.getMedicationRequestIds().get(0));
            if (row != null && StringUtils.isNotEmpty(row.getPrescriptionNo())) {
                decoctionFeeAssembler.voidDecoctionChargeByPrescriptionNo(
                    request.getEncounterId(), row.getPrescriptionNo(), operatorId);
            }
        }
        if (DoctorStationTcmConstants.ACTION_WITHDRAW.equals(request.getAction())) {
            this.outpatientPaymentDraftLifecycleOrchestrator.cancelDraftsWithNonPlannedBoundCharges(request.getEncounterId());
        }
        return tcmMapper.toWithdrawOrDeleteTcmRxResponse(Boolean.TRUE);
    }

    /**
     * 单独取消已签发中药方的代煎费（撤回代煎 companion SR 与未结算费用，并回写方头代煎=否）。
     *
     * @param request 含就诊、患者、处方方标识
     * @return 是否成功
     */
    public VoidTcmDecoctionChargeResponse voidTcmDecoctionCharge(VoidTcmDecoctionChargeRequest request) {
        // Pre：门诊就诊快照
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        // Pre：按方标识定位当前就诊中药方
        List<TcmHerbalPrescriptionGroupQueryModel> groups =
            medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
                request.getEncounterId(), GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        TcmHerbalPrescriptionGroupQueryModel targetGroup = null;
        String groupKey = StringUtils.trimToEmpty(request.getPrescriptionGroupKey());
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            if (group == null) {
                continue;
            }
            if (groupKey.equals(StringUtils.trimToEmpty(group.getPrescriptionGroupKey()))) {
                targetGroup = group;
                break;
            }
        }
        if (targetGroup == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "未找到对应中药处方");
        }
        if (!Boolean.TRUE.equals(targetGroup.getSufferingFlag())) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "当前处方未勾选代煎，无需取消代煎费");
        }
        String prescriptionNo = StringUtils.trimToEmpty(targetGroup.getPrescriptionNo());
        if (StringUtils.isEmpty(prescriptionNo)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "处方尚未签发，请直接取消代煎勾选后保存");
        }
        // Gate：已结算代煎费不可直接取消
        decoctionFeeAssembler.assertDecoctionChargeVoidable(request.getEncounterId(), prescriptionNo);
        // Cmd：清理代煎 companion SR 与未结算费用
        decoctionFeeAssembler.voidDecoctionChargeByPrescriptionNo(
            request.getEncounterId(), prescriptionNo, operatorId);
        // Cmd：回写方内饮片行代煎标志
        List<Long> lineIds = new ArrayList<>();
        if (targetGroup.getLines() != null) {
            for (MedicationRequestListQueryModel line : targetGroup.getLines()) {
                if (line != null && line.getId() != null) {
                    lineIds.add(line.getId());
                }
            }
        }
        ClearHerbalSufferingFlagCommand clearCmd = new ClearHerbalSufferingFlagCommand();
        clearCmd.setMedicationRequestIds(lineIds);
        clearCmd.setOperatorId(operatorId);
        medicationRequestDomainService.clearSufferingFlagAfterDecoctionVoided(clearCmd);
        this.outpatientPaymentDraftLifecycleOrchestrator.cancelDraftsWithNonPlannedBoundCharges(
            request.getEncounterId());
        VoidTcmDecoctionChargeResponse response = new VoidTcmDecoctionChargeResponse();
        response.setSuccess(Boolean.TRUE);
        return response;
    }

    /**
     * 已签发中药方补充代煎费（回写方头代煎=是，并按付数挂 PLANNED 代煎费；药材已收费亦可）。
     *
     * <p>仅补充，不撤销已收费代煎；退费走门诊正常退费流程。</p>
     *
     * @param request 含就诊、患者、处方方标识
     * @return 是否成功
     */
    public SupplementTcmDecoctionChargeResponse supplementTcmDecoctionCharge(
        SupplementTcmDecoctionChargeRequest request) {
        // Pre：门诊就诊快照与自费账户
        OutpatientEncounterContextQueryModel snapshot =
            EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        // Pre：代煎强制挂个人现金自费户（与签发代煎 preferInsurance=false 同口径）
        Long accountId = encounterChargeAccountOpener.resolveChargeAccountId(
            request.getEncounterId(), null, false);
        // Pre：按方标识定位当前就诊中药方
        List<TcmHerbalPrescriptionGroupQueryModel> groups =
            medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
                request.getEncounterId(), GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        TcmHerbalPrescriptionGroupQueryModel targetGroup = null;
        String groupKey = StringUtils.trimToEmpty(request.getPrescriptionGroupKey());
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            if (group == null) {
                continue;
            }
            if (groupKey.equals(StringUtils.trimToEmpty(group.getPrescriptionGroupKey()))) {
                targetGroup = group;
                break;
            }
        }
        if (targetGroup == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "未找到对应中药处方");
        }
        String prescriptionNo = StringUtils.trimToEmpty(targetGroup.getPrescriptionNo());
        if (StringUtils.isEmpty(prescriptionNo)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "处方尚未签发，请勾选代煎后直接签发");
        }
        // Gate：同方已有代煎费则拒绝（已收费代煎须走退费，不可在此覆盖）
        decoctionFeeAssembler.assertDecoctionChargeSupplementable(request.getEncounterId(), prescriptionNo);
        List<Long> lineIds = new ArrayList<>();
        MedicationRequestListQueryModel anchorRow = null;
        Long anchorMedicationRequestId = null;
        if (targetGroup.getLines() != null) {
            for (MedicationRequestListQueryModel line : targetGroup.getLines()) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                lineIds.add(line.getId());
                if (anchorRow == null) {
                    anchorRow = line;
                    anchorMedicationRequestId = line.getId();
                }
            }
        }
        if (lineIds.isEmpty() || anchorRow == null || anchorMedicationRequestId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "处方无有效药材行，无法补充代煎费");
        }
        BigDecimal doseQuantity = targetGroup.getChineseHerbsDoseQuantity();
        if (doseQuantity == null || doseQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "处方付数无效，无法补充代煎费");
        }
        // Cmd：回写方内饮片行代煎标志
        MarkHerbalSufferingFlagCommand markCmd = new MarkHerbalSufferingFlagCommand();
        markCmd.setMedicationRequestIds(lineIds);
        markCmd.setOperatorId(operatorId);
        medicationRequestDomainService.markSufferingFlagForDecoctionSupplement(markCmd);
        // Cmd：按付数签发代煎 companion SR 并挂 PLANNED 费用
        decoctionFeeAssembler.replacePlannedDecoctionCharge(
            request.getEncounterId(),
            request.getPatientId(),
            accountId,
            orgId,
            operatorId,
            prescriptionNo,
            doseQuantity,
            anchorRow,
            anchorMedicationRequestId);
        SupplementTcmDecoctionChargeResponse response = new SupplementTcmDecoctionChargeResponse();
        response.setSuccess(Boolean.TRUE);
        return response;
    }

    /**
     * IF-DOR-39：查询患者历史中药处方（含当前就诊已签方，只读引用）。
     *
     * <p>摘要行附带 {@code detail} 完整方头明细，供前端点击引用；明细按就诊直读领域聚合，
     * 不走「当前就诊 list」门控，避免住院/跨就诊引用时可见性校验失败。</p>
     * <p>与当前就诊同号的行标记 {@code currentEncounterFlag=true}，侧栏展示「本次」；本次方排在列表前部。</p>
     *
     * @param request 含 patientId、currentEncounterId、可选 keyword
     * @return 历史方摘要列表（含引用用 detail）
     */
    public ListPatientTcmRxHistoryResponse listPatientTcmHerbalPrescriptionHistory(
        ListPatientTcmRxHistoryRequest request) {
        // Pre：领域层按 prescription_no 聚合（含当前就诊已签方）
        List<TcmHerbalPrescriptionHistoryGroupQueryModel> groups =
            medicationRequestQueryService.listTcmHerbalPrescriptionHistoryByPatient(request.getPatientId(), request.getCurrentEncounterId());
        // Asm：补药名并按关键字过滤
        List<TcmRxHistoryRowDto> rows = prescriptionListAssembler.enrichHistoryGroups(groups, request.getKeyword());
        // Asm：按就诊批量补齐完整方明细，供侧栏点击引用
        Map<String, TcmRxHeaderDto> detailByKey = this.loadHistoryPrescriptionDetailsByEncounter(groups);
        Long currentEncounterId = request.getCurrentEncounterId();
        for (TcmRxHistoryRowDto row : rows) {
            if (row == null || row.getEncounterId() == null || StringUtils.isEmpty(row.getPrescriptionNo())) {
                continue;
            }
            String detailKey = row.getEncounterId() + "|" + row.getPrescriptionNo().trim();
            row.setDetail(detailByKey.get(detailKey));
            // 本次就诊已签方：侧栏「本次」标识
            row.setCurrentEncounterFlag(Objects.equals(row.getEncounterId(), currentEncounterId));
        }
        // 本次方置顶，同组内仍按开立时间倒序
        rows.sort(Comparator
            .comparing((TcmRxHistoryRowDto row) -> {
                if (Boolean.TRUE.equals(row.getCurrentEncounterFlag())) {
                    return 0;
                }
                return 1;
            })
            .thenComparing(TcmRxHistoryRowDto::getReqOpenedTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TcmRxHistoryRowDto::getPrescriptionNo, Comparator.nullsLast(Comparator.naturalOrder())));
        return tcmMapper.toListPatientTcmRxHistoryResponse(rows);
    }

    /**
     * 按历史方涉及的就诊批量加载完整中药方头（含明细行），供 IF-DOR-39 引用。
     *
     * @param groups 历史方摘要聚合
     * @return key=encounterId|prescriptionNo → 方头 DTO
     */
    private Map<String, TcmRxHeaderDto> loadHistoryPrescriptionDetailsByEncounter(
        List<TcmHerbalPrescriptionHistoryGroupQueryModel> groups) {
        Map<String, TcmRxHeaderDto> detailByKey = new HashMap<>();
        if (groups == null || groups.isEmpty()) {
            return detailByKey;
        }
        Set<Long> historyEncounterIds = new LinkedHashSet<>();
        for (TcmHerbalPrescriptionHistoryGroupQueryModel group : groups) {
            if (group != null && group.getEncounterId() != null) {
                historyEncounterIds.add(group.getEncounterId());
            }
        }
        for (Long historyEncounterId : historyEncounterIds) {
            try {
                // Pre：直读历史就诊中药方（无门诊/住院队列可见性门控）
                List<TcmHerbalPrescriptionGroupQueryModel> encounterGroups =
                    medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
                        historyEncounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
                List<TcmRxHeaderDto> headers = prescriptionListAssembler.toPrescriptionHeaders(encounterGroups);
                if (headers == null || headers.isEmpty()) {
                    continue;
                }
                // Pre：同就诊病证组快照（无可见性门控），供历史方复用诊断
                String diagnosisJson =
                    encounterDiagnosisSnapshotAssembler.buildSnapshot(historyEncounterId).diagnosisJson();
                List<TcmDiagnosisGroupRowDto> encounterDiagnosisGroups =
                    diagnosisAssembler.groupTcmDiagnosisRows(historyEncounterId, diagnosisJson);
                for (TcmRxHeaderDto header : headers) {
                    if (header == null || StringUtils.isEmpty(header.getPrescriptionNo())) {
                        continue;
                    }
                    // Asm：方头挂上可复用病证目录对，前端跨就诊落到本次
                    header.setReuseDiagnosisGroups(
                        this.pickReuseDiagnosisGroupsForHistoryHeader(header, encounterDiagnosisGroups));
                    String detailKey = historyEncounterId + "|" + header.getPrescriptionNo().trim();
                    detailByKey.put(detailKey, header);
                }
            } catch (RuntimeException ex) {
                // 单就诊明细失败不阻断历史摘要列表展示
                log.warn("加载历史中药方明细失败, historyEncounterId={}", historyEncounterId, ex);
            }
        }
        return detailByKey;
    }

    /**
     * 从历史就诊病证组中挑选与方头绑定相关的组（供跨就诊复用诊断）。
     *
     * @param header 历史方头（含 tcmSyndromeGroup / encounterDiagnosisIds）
     * @param encounterGroups 历史就诊全部中医病证组
     * @return 与方头相关的病证组；无匹配时空列表
     */
    private List<TcmDiagnosisGroupRowDto> pickReuseDiagnosisGroupsForHistoryHeader(
        TcmRxHeaderDto header,
        List<TcmDiagnosisGroupRowDto> encounterGroups
    ) {
        if (header == null || encounterGroups == null || encounterGroups.isEmpty()) {
            return List.of();
        }
        // 优先按方头病证组号精确匹配
        String syndromeGroup = trimToEmpty(header.getTcmSyndromeGroup());
        if (StringUtils.isNotEmpty(syndromeGroup)) {
            List<TcmDiagnosisGroupRowDto> byGroup = new ArrayList<>();
            for (TcmDiagnosisGroupRowDto group : encounterGroups) {
                if (group == null) {
                    continue;
                }
                if (syndromeGroup.equals(trimToEmpty(group.getTcmSyndromeGroup()))) {
                    byGroup.add(group);
                }
            }
            if (!byGroup.isEmpty()) {
                return byGroup;
            }
        }
        // 组号对不上时：按方头绑定诊断 id 命中病名/证候事实行
        List<Long> boundDxIds =
            EncounterDiagnosisIdsSupport.parseUniqueOrderedIds(header.getEncounterDiagnosisIds(), true);
        if (!boundDxIds.isEmpty()) {
            Set<Long> boundIdSet = new LinkedHashSet<>(boundDxIds);
            List<TcmDiagnosisGroupRowDto> byDxId = new ArrayList<>();
            for (TcmDiagnosisGroupRowDto group : encounterGroups) {
                if (group == null) {
                    continue;
                }
                Long diseaseDxId = group.getDiseaseEncounterDiagnosisId();
                Long syndromeDxId = group.getSyndromeEncounterDiagnosisId();
                if ((diseaseDxId != null && boundIdSet.contains(diseaseDxId))
                    || (syndromeDxId != null && boundIdSet.contains(syndromeDxId))) {
                    byDxId.add(group);
                }
            }
            if (!byDxId.isEmpty()) {
                return byDxId;
            }
        }
        // 历史就诊仅一组病证时默认挂上，便于复用
        if (encounterGroups.size() == 1) {
            TcmDiagnosisGroupRowDto only = encounterGroups.get(0);
            if (only != null) {
                return List.of(only);
            }
        }
        return List.of();
    }

    /**
     * 批量保存中医病证组前置校验：成组完整性委托策略，且主诊组至多一个。
     *
     * @param request 批量保存请求
     * @throws BusinessException 组项不完整或主诊超过一个
     */
    private void validateTcmDiagnosisBatch(SaveTcmDiagnosisGroupsBatchRequest request) {
        int primaryCount = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            // Gate：默认一病一证；朝阳等院点可通过 @Primary 策略允许单侧
            tcmDiagnosisGroupPairingPolicy.assertGroupAcceptable(
                group.getDiseaseDefinitionId(), group.getSyndromeDefinitionId());
            if (group.getPrimaryFlag() != null
                && group.getPrimaryFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                primaryCount++;
            }
        }
        if (primaryCount > 1) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "请只保留一个主诊病证组");
        }
    }

    /**
     * 为批量保存的病证组分配稳定 tcm_syndrome_group 键：保留已有键，新组按日序取号。
     *
     * @param request 批量保存请求（组项会被回填 group 键）
     * @return 稳定键 → group 键映射
     */
    private Map<String, String> assignTcmGroupKeys(SaveTcmDiagnosisGroupsBatchRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        int newCount = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            if (StringUtils.isEmpty(group.getTcmSyndromeGroup())) {
                newCount++;
            }
        }
        List<String> newKeys = new ArrayList<>();
        if (newCount > 0) {
            newKeys = businessSequenceService.getSeqByDay(
                AggregateTypeConstants.CONDITION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, newCount);
        }
        int idx = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            // 单侧组用 d:{id} / s:{id}，双侧仍为 diseaseId:syndromeId
            String stableKey = tcmMapper.resolveTcmGroupStableKey(group);
            String key;
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
     * 保留中的病证组若改病/证目录定义，且对应就诊诊断行仍被医嘱或账单引用，则拒绝保存。
     * 与西医诊断保存门禁同源：共用 {@link EncounterDiagnosisReferenceSupport}。
     *
     * @param existing 当前就诊已有病证组
     * @param request  本批全量保存请求
     */
    private void assertTcmRetainedGroupsContentChangeAllowed(
        List<TcmDiagnosisGroupRowDto> existing,
        SaveTcmDiagnosisGroupsBatchRequest request
    ) {
        Map<String, TcmDiagnosisGroupItemRequest> incomingByKey = new HashMap<>();
        if (request.getGroups() != null) {
            for (TcmDiagnosisGroupItemRequest item : request.getGroups()) {
                if (item == null) {
                    continue;
                }
                String key = trimToEmpty(item.getTcmSyndromeGroup());
                if (key.isEmpty()) {
                    continue;
                }
                incomingByKey.put(key, item);
            }
        }
        for (TcmDiagnosisGroupRowDto old : existing) {
            if (old == null) {
                continue;
            }
            String key = trimToEmpty(old.getTcmSyndromeGroup());
            TcmDiagnosisGroupItemRequest incoming = incomingByKey.get(key);
            if (incoming == null) {
                // 已删除组走 deleteTcmGroupInternal，此处只盯内容变更
                continue;
            }
            boolean diseaseDefChanged =
                !Objects.equals(old.getDiseaseDefinitionId(), incoming.getDiseaseDefinitionId());
            boolean syndromeDefChanged =
                !Objects.equals(old.getSyndromeDefinitionId(), incoming.getSyndromeDefinitionId());
            if (!diseaseDefChanged && !syndromeDefChanged) {
                continue;
            }
            encounterDiagnosisReferenceSupport.assertNotReferenced(old.getDiseaseEncounterDiagnosisId());
            encounterDiagnosisReferenceSupport.assertNotReferenced(old.getSyndromeEncounterDiagnosisId());
        }
    }

    /**
     * 按病证组解析 encounter_diagnosis_ids 有序串（病名在前；与西医开嘱同一字段，非中医专用通道）。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @return 组键 → 有序 ids 串；无有效行时为空 map
     */
    private Map<String, String> buildEncounterDiagnosisIdsByTcmGroup(Long encounterId, Long patientId) {
        ListTcmDiagnosisGroupsRequest req =
            new ListTcmDiagnosisGroupsRequest().setEncounterId(encounterId).setPatientId(patientId);
        ListTcmDiagnosisGroupsResponse resp = listTcmDiagnosisGroups(req);
        Map<String, String> map = new HashMap<>();
        if (resp.getGroups() == null) {
            return map;
        }
        for (TcmDiagnosisGroupRowDto group : resp.getGroups()) {
            if (group == null) {
                continue;
            }
            String key = trimToEmpty(group.getTcmSyndromeGroup());
            if (key.isEmpty()) {
                continue;
            }
            String idsText = this.toTcmGroupEncounterDiagnosisIds(group);
            if (idsText != null) {
                map.put(key, idsText);
            }
        }
        return map;
    }

    /**
     * 病证组 → 有序就诊诊断有序 ids 串（病名、证候；去重）。
     *
     * @param group 病证组行
     * @return 有序 ids 串；两端均空时 null
     */
    private String toTcmGroupEncounterDiagnosisIds(TcmDiagnosisGroupRowDto group) {
        List<Long> ordered = new ArrayList<>();
        Long diseaseId = group.getDiseaseEncounterDiagnosisId();
        Long syndromeId = group.getSyndromeEncounterDiagnosisId();
        if (diseaseId != null) {
            ordered.add(diseaseId);
        }
        if (syndromeId != null && !syndromeId.equals(diseaseId)) {
            ordered.add(syndromeId);
        }
        if (ordered.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ordered.get(i));
        }
        return sb.toString();
    }

    /**
     * 删除单个中医病证组：校验无医嘱引用后移除就诊关联并软删 condition。
     *
     * @param encounterId 就诊 ID
     * @param groupKey      病证组键
     * @param existing      当前病证组列表
     * @param operatorId    操作人
     * @throws BusinessException 组不存在或仍有处方引用
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
        if (target == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_NOT_FOUND, "病证组不存在，请刷新后重试");
        }
        Long[] encounterDiagnosisIds = new Long[] {
            target.getDiseaseEncounterDiagnosisId(), target.getSyndromeEncounterDiagnosisId()};
        for (Long encounterDiagnosisId : encounterDiagnosisIds) {
            if (encounterDiagnosisId == null) {
                continue;
            }
            encounterDiagnosisReferenceSupport.assertNotReferenced(encounterDiagnosisId);
        }
        for (Long encounterDiagnosisId : encounterDiagnosisIds) {
            if (encounterDiagnosisId == null) {
                continue;
            }
            RemoveEncounterDiagnosisAssociationCommand rm = new RemoveEncounterDiagnosisAssociationCommand();
            RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item =
                new RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand();
            item.setEncounterId(encounterId);
            item.setEncounterDiagnosisId(encounterDiagnosisId);
            item.setRemoveReason("TCM_GROUP_DELETE");
            rm.setItem(item);
            rm.setOperatorId(operatorId);
            encounterDomainService.removeEncounterDiagnosisAssociation(rm);
        }
    }

    /**
     * 加载就诊下当前有效中医病证组键集合（保存处方草稿时校验 group 合法）。
     *
     * @param encounterId 就诊 ID
     * @param patientId   患者 ID
     * @return 病证组键集合
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
     * 中药饮片行挂费草稿：解析目录定价并创建未计费 ChargeItem（锚定 med_medication_request）。
     *
     * @param request    批量保存请求（就诊上下文）
     * @param accountId  账户 ID
     * @param orgId      机构 ID
     * @param operatorId 操作人
     * @param medReqId   药品请求 ID
     * @param header     方头（用法参与定价）
     * @param line       饮片行
     * @param quantity   挂费数量
     * @throws BusinessException 未配置符合开立规则的收费定价
     */
    private void createHerbalCharge(
        SaveTcmRxDraftBatchRequest request,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long medReqId,
        TcmRxDraftHeaderRequest header,
        TcmRxDraftLineRequest line,
        BigDecimal quantity) {
        // Pre：加载产品读模型，供定价解析与挂费单位换算（与 IF-DOR-14 西药路径一致）
        ProductDefinitionDisplayDto productRow = loadSingleProduct(
            TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationDefinitionId());
        ChargeItemDefinition priceLine = clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            line.getMedicationDefinitionId(),
            productRow,
            orgId,
            request.getEncounterId(),
            line.getDispenseLocationId(),
            header.getMethodCode());
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "未配置符合开立规则的收费定价（诊疗须零售价 SALE，药品/耗材须批号售价 LOT_NUMBER_PRICE）。请先维护收费项定价后再保存医嘱。");
        }
        chargeItemDomainService.createDraftChargeItemFromOrder(
            doctorStationAdviceMapper.toCreateChargeItemDraftFromOrder(
                request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                medReqId, TableNameConstants.MED_MEDICATION_REQUEST, TableNameConstants.MED_MEDICATION_DEFINITION,
                line.getMedicationDefinitionId(), priceLine, quantity, line.getDoseUnitCode(), productRow));
    }

    /**
     * 按表名与定义主键加载单条产品读模型（暂存挂费与 IF-DOR-14 西药路径一致）。
     */
    private ProductDefinitionDisplayDto loadSingleProduct(String table, Long id) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(table);
        lookup.setProductDefinitionId(id);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, id);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "未找到药品产品定义行");
        }
        return row;
    }

    /**
     * 按合同解析挂账账户：有合同则匹配账户 contract_no；无合同双账户默认医保户。
     *
     * @param encounterId 就诊主键
     * @param contractId  行级/方头合同主键；可空
     * @return 挂账账户主键
     */
    private Long resolveTcmChargeAccountId(Long encounterId, Long contractId) {
        String contractBusNo = null;
        boolean preferInsurance = true;
        if (contractId != null) {
            ContractDetailQueryModel contract = contractQueryService.findContractById(contractId);
            if (contract != null) {
                if (contract.getBusNo() != null) {
                    contractBusNo = contract.getBusNo().trim();
                }
                preferInsurance = encounterChargeAccountOpener.needsParallelMedicalInsuranceAccount(
                    contract.getYbFlag(), contract.getCategoryEnum());
            }
        }
        return encounterChargeAccountOpener.resolveChargeAccountId(encounterId, contractBusNo, preferInsurance);
    }

    /**
     * Gate：药品申请必须属于当前就诊与患者，防止切患者后旧处方行串挂费。
     *
     * @param encounterId 当前请求就诊
     * @param patientId   当前请求患者
     * @param row         已装载的药品申请列表模型
     */
    private void assertMedicationRequestBelongsToEncounterPatient(
        Long encounterId, Long patientId, MedicationRequestListQueryModel row) {
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品申请不存在");
        }
        if (!java.util.Objects.equals(encounterId, row.getEncounterId())
            || !java.util.Objects.equals(patientId, row.getPatientId())) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "处方不属于当前患者/就诊，请刷新后重试");
        }
    }

    /**
     * 按主键装载药品申请列表模型；不存在则抛业务异常。
     *
     * @param medicationRequestId 药品申请主键
     * @return 列表模型
     */
    private MedicationRequestListQueryModel requireMedicationRequestListModel(Long medicationRequestId) {
        if (medicationRequestId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "药品申请主键不能为空");
        }
        List<MedicationRequestListQueryModel> rows =
            medicationRequestQueryService.listMedicationRequestListModelsByIds(List.of(medicationRequestId));
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品申请不存在");
        }
        return rows.get(0);
    }


}
