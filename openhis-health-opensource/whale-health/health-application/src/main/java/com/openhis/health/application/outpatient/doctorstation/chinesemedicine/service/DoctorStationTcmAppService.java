/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapper;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.assembler.*;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.*;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.mapper.DoctorStationTcmMapper;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request.*;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response.*;
import com.openhis.health.module.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.clinical.condition.command.DelEncounterDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.service.ConditionDomainService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.ReleaseDispenseReserveCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.*;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.module.orchestration.advice.OutpatientMedicationSignChargeSupport;
import com.openhis.health.module.orchestration.financial.OutpatientPaymentDraftLifecycleSupport;
import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogPriceResolveSupport;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.order.OrderRequestFifoSignContextLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 门诊医生站中医 Tab 编排（IF-DOR-30～39）。
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
    private final EncounterContextFacade encounterContextFacade;
    /** 中医 Tab 字段映射。 */
    private final DoctorStationTcmMapper tcmMapper;
    /** 病证组聚合。 */
    private final TcmDiagnosisAssembler diagnosisAssembler;
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
    /** 诊断事实写。 */
    private final ConditionDomainService conditionDomainService;
    /** 就诊诊断关联写。 */
    private final EncounterDomainService encounterDomainService;
    /** 医嘱引用计数。 */
    private final ConditionQueryService conditionQueryService;
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
    /** 账户读。 */
    private final AccountQueryService accountQueryService;
    /** 业务序列。 */
    private final BusinessSequenceService businessSequenceService;
    /** 医嘱开立映射复用（费用/签发命令）。 */
    private final DoctorStationAdviceMapper doctorStationAdviceMapper;
    /** 目录装配。 */
    private final DoctorStationAdviceCatalogAssembler doctorStationAdviceCatalogAssembler;
    /** 目录分页加载。 */
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    /** FIFO 上下文。 */
    private final OrderRequestFifoSignContextLoader orderRequestFifoSignContextLoader;
    /** 定价解析（暂存挂费）。 */
    private final ClinicalCatalogPriceResolveSupport clinicalCatalogPriceResolveSupport;
    /** 门诊药品签发 FIFO 分段挂费（与 IF-DOR-15 共用）。 */
    private final OutpatientMedicationSignChargeSupport outpatientMedicationSignChargeSupport;
    /** 产品定义读模型批量加载（暂存挂费与 IF-DOR-14 西药口径一致）。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    /** 分页窗口门控。 */
    private final PageWindowGate pageWindowGate;
    /** 术语码表读（中药用法 tcm_method_code）。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 用药频次下拉（与医嘱 Tab 同源）。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 签退后作废陈旧预结算草稿（GAP-STALE-001）。 */
    private final OutpatientPaymentDraftLifecycleSupport outpatientPaymentDraftLifecycleSupport;

    /**
     * 中医 Tab 页初始化：下发中药用法与用药频次下拉。
     *
     * @param request 就诊与患者门控
     * @return 中药用法与频次术语
     */
    public InitDoctorStationTcmResponse initDoctorStationTcm(InitDoctorStationTcmRequest request) {
        // Gate：门诊快照加载并校验患者与就诊绑定一致，防止跨就诊误初始化下拉
        OutpatientEncounterContextQueryModel snapshot =
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        InitDoctorStationTcmResponse resp = new InitDoctorStationTcmResponse();
        // Pre：中药用法走 tcm_method_code 术语，不含西医 method_code
        resp.setTcmMethodCodeOptions(
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE));
        // Asm：用药频次与医嘱 Tab IF-DOR-18 同源（cus_frequency 主数据）
        resp.setMedicationRateOptions(frequencyOptionLoader.loadActiveRateOptions());
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
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
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
        // Asm：领域行转展示 DTO
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
     * IF-DOR-32：全量保存中医病证组（一病一证成组）。
     *
     * @param request 全量病证组批次；空组拒绝
     * @return 保存后的组号列表
     */
    public SaveTcmDiagnosisGroupsBatchResponse saveTcmDiagnosisGroupsBatch(SaveTcmDiagnosisGroupsBatchRequest request) {
        // Pre：门诊就诊快照
        OutpatientEncounterContextQueryModel snapshot =
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        // Gate：每组须 1 病 + 1 证，主诊组至多 1 个
        validateTcmDiagnosisBatch(request);
        String diagnosisJson = encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson();
        List<TcmDiagnosisGroupRowDto> existing = diagnosisAssembler.groupTcmDiagnosisRows(request.getEncounterId(), diagnosisJson);
        List<String> removedGroups = tcmMapper.diffRemovedTcmGroups(existing, request);
        // Cmd：全量 diff 后先删不在批次内的历史组
        for (String removed : removedGroups) {
            deleteTcmGroupInternal(request.getEncounterId(), removed, existing, operatorId);
        }
        // Pre：为新组分配 tcm_syndrome_group 序列号
        Map<String, String> groupKeyByItem = assignTcmGroupKeys(request);
        // Cmd：upsert 诊断事实与中医就诊关联（batchUpsertTcm，不误删西医）
        List<Long> savedIds = conditionDomainService.saveDoctorDiagnosisTcm(
            tcmMapper.toSaveDoctorDiagnosisTcmCommand(request, groupKeyByItem, operatorId));
        encounterDomainService.batchUpsertTcmEncounterDiagnosisAssociation(
            tcmMapper.toBatchUpsertTcmEncounterDiagnosisAssociationCommand(request, savedIds, groupKeyByItem, operatorId));
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
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
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
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        // Pre：领域层按 prescription_no / prescriptionGroupKey 聚合
        List<TcmHerbalPrescriptionGroupQueryModel> groups = medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
            request.getEncounterId(), GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // Asm：enrich 目录名与费用态
        List<TcmRxHeaderDto> headers = prescriptionListAssembler.toPrescriptionHeaders(groups);
        return tcmMapper.toListTcmRxResponse(headers);
    }

    /**
     * IF-DOR-35：分页检索中药饮片目录（读路径与 IF-DOR-11 同构，仅强制中草药 category）。
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
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        PageDoctorStationAdviceCatalogRequest catalogRequest = tcmMapper.toPageDoctorStationAdviceCatalogRequest(request);
        ProductDefinitionPageQuery pageQuery = doctorStationAdviceMapper.toProductDefinitionPageQuery(catalogRequest);
        // Pre：仅中草药分类，其余读侧与 IF-DOR-11 共用装配器
        pageQuery.setCategoryCode(DoctorStationTcmConstants.HERBAL_CATEGORY_CODE);
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
        List<DoctorStationAdviceCatalogRowDto> rows =
            doctorStationAdviceCatalogAssembler.assemblePageContent(catalogRequest, page, snapshot);
        // Asm：与 IF-DOR-11 相同分页契约，避免饮片单独换算单位/单价
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
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        // Pre：自费账户用于挂费用草稿
        Long accountId = accountQueryService.getSelfPayAccount(request.getEncounterId());
        if (accountId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊未解析到自费账户，无法生成费用草稿");
        }
        Set<String> validGroups = loadValidTcmGroupKeys(request.getEncounterId(), request.getPatientId());
        List<Long> newIds = new ArrayList<>();
        for (TcmRxDraftHeaderRequest header : request.getPrescriptions()) {
            // Gate：每张方须绑定已存在的病证组
            if (!validGroups.contains(header.getTcmSyndromeGroup())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "请先维护并绑定中医病证组");
            }
            String contentJson = tcmMapper.buildHerbalContentJson(header);
            for (TcmRxDraftLineRequest line : header.getLines()) {
                String op = StringUtils.upperCase(trimToEmpty(line.getLineOp()));
                // 请求数量 = 单剂用量 × 付数（BR-TCM-RX-005）
                BigDecimal quantity = line.getDose().multiply(header.getChineseHerbsDoseQuantity());
                // Cmd：按 lineOp 三选一执行 CREATE / UPDATE / DELETE
                if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                    Long newId = medicationRequestDomainService.createHerbalMedicationOrderDraft(
                        tcmMapper.toCreateHerbalMedicationOrderDraftCommand(
                            request, header, line, operatorId, orgId, contentJson, quantity));
                    createHerbalCharge(request, accountId, orgId, operatorId, newId, header, line, quantity);
                    newIds.add(newId);
                } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                    chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                        doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                            request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST,
                            line.getMedicationRequestId(), operatorId));
                    medicationRequestDomainService.changeHerbalMedicationOrderDraft(
                        tcmMapper.toChangeHerbalMedicationOrderDraftCommand(
                            header, line, operatorId, contentJson, quantity, null, null));
                    createHerbalCharge(request, accountId, orgId, operatorId, line.getMedicationRequestId(), header, line, quantity);
                } else if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                    chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                        request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(line.getMedicationRequestId()));
                    medicationRequestDomainService.removeMedicationOrderDraft(
                        doctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(line.getMedicationRequestId(), operatorId));
                } else {
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
        // Pre：门诊就诊快照与自费账户
        OutpatientEncounterContextQueryModel snapshot =
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // Gate：患者就诊一致
        tcmMapper.validatePatientEncounter(snapshot, request.getPatientId());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        Long accountId = accountQueryService.getSelfPayAccount(request.getEncounterId());
        if (accountId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊未解析到自费账户，无法完成签发费用编排");
        }
        // Pre：按 prescriptionGroupKey 分组，并断言均为 tcm_flag 草稿
        Map<String, List<Long>> groupKeyToIds = new LinkedHashMap<>();
        for (SignTcmRxTargetRequest target : request.getSignTargets()) {
            MedicationRequestListQueryModel row = medicationRequestQueryService.getListModelById(target.getMedicationRequestId());
            // Gate：非中药行须在对应 Tab 签发（BR-TCM-ISO-002）
            if (row == null || !Boolean.TRUE.equals(row.getTcmFlag())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "请在对应 Tab 签发");
            }
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
            MedicationRequestFifoSignContext fifoCtx =
                orderRequestFifoSignContextLoader.loadMedicationFifoSignContextOrThrow(target.getMedicationRequestId());
            // Cmd：FIFO 发放 + 分段挂费 + PLANNED（与 IF-DOR-15 药线共用 Support）
            outpatientMedicationSignChargeSupport.replaceFifoDispenseAndPlanChargesAfterMedicationSigned(
                request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                target.getMedicationRequestId(), fifoCtx);
        }
        // Cmd：按方头代煎标志生成或 void 代煎费
        for (Map.Entry<String, List<Long>> entry : groupKeyToIds.entrySet()) {
            MedicationRequestListQueryModel first = medicationRequestQueryService.getListModelById(entry.getValue().get(0));
            if (first != null && Boolean.TRUE.equals(first.getSufferingFlag())) {
                // Cmd：方头代煎=是，签发 companion 代煎 SR 并按付数挂 PLANNED 费用
                decoctionFeeAssembler.replacePlannedDecoctionCharge(
                    request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
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
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
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
            && StringUtils.isNotBlank(request.getPrescriptionGroupKey())) {
            MedicationRequestListQueryModel row = medicationRequestQueryService.getListModelById(request.getMedicationRequestIds().get(0));
            if (row != null && StringUtils.isNotBlank(row.getPrescriptionNo())) {
                decoctionFeeAssembler.voidDecoctionChargeByPrescriptionNo(
                    request.getEncounterId(), row.getPrescriptionNo(), operatorId);
            }
        }
        if (DoctorStationTcmConstants.ACTION_WITHDRAW.equals(request.getAction())) {
            this.outpatientPaymentDraftLifecycleSupport.cancelDraftsWithNonPlannedBoundCharges(request.getEncounterId());
        }
        return tcmMapper.toWithdrawOrDeleteTcmRxResponse(Boolean.TRUE);
    }

    /**
     * IF-DOR-39：查询患者历史中药处方（排除当前就诊，只读引用）。
     *
     * @param request 含 patientId、currentEncounterId、可选 keyword
     * @return 历史方摘要列表
     */
    public ListPatientTcmRxHistoryResponse listPatientTcmHerbalPrescriptionHistory(
        ListPatientTcmRxHistoryRequest request) {
        // Pre：领域层按 prescription_no 聚合，排除当前就诊
        List<TcmHerbalPrescriptionHistoryGroupQueryModel> groups =
            medicationRequestQueryService.listTcmHerbalPrescriptionHistoryByPatient(request.getPatientId(), request.getCurrentEncounterId());
        // Asm：补药名并按关键字过滤
        List<TcmRxHistoryRowDto> rows = prescriptionListAssembler.enrichHistoryGroups(groups, request.getKeyword());
        return tcmMapper.toListPatientTcmRxHistoryResponse(rows);
    }

    private void validateTcmDiagnosisBatch(SaveTcmDiagnosisGroupsBatchRequest request) {
        int primaryCount = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            if (group.getDiseaseDefinitionId() == null || group.getSyndromeDefinitionId() == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "每组须同时选择病名与证候");
            }
            if (group.getPrimaryFlag() != null
                && group.getPrimaryFlag() == DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES) {
                primaryCount++;
            }
        }
        if (primaryCount > 1) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "请只保留一个主诊病证组");
        }
    }

    private Map<String, String> assignTcmGroupKeys(SaveTcmDiagnosisGroupsBatchRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        int newCount = 0;
        for (TcmDiagnosisGroupItemRequest group : request.getGroups()) {
            if (StringUtils.isBlank(group.getTcmSyndromeGroup())) {
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
            String stableKey = String.valueOf(group.getDiseaseDefinitionId()) + ":" + String.valueOf(group.getSyndromeDefinitionId());
            String key;
            if (StringUtils.isNotBlank(group.getTcmSyndromeGroup())) {
                key = group.getTcmSyndromeGroup().trim();
            } else {
                key = newKeys.get(idx++);
            }
            map.put(stableKey, key);
            group.setTcmSyndromeGroup(key);
        }
        return map;
    }

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
        Long[] conditionIds = new Long[] {target.getDiseaseConditionId(), target.getSyndromeConditionId()};
        for (Long conditionId : conditionIds) {
            if (conditionId == null) {
                continue;
            }
            if (conditionQueryService.countAdviceRefByConditionId(conditionId) > 0) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, "先撤销或调整相关处方后再删");
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
        ChargeItemDefinition priceLine = clinicalCatalogPriceResolveSupport.resolveDraftChargePriceLine(
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
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, id);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "未找到药品产品定义行");
        }
        return row;
    }
}