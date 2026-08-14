/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionquery.service;
import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.dto.OutpatientPharmacyPrescriptionDrugLineDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.mapper.OutpatientPharmacyPrescriptionQueryMapper;
import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.request.LoadOutpatientPharmacyPrescriptionQueryPageInitRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.request.PageOutpatientPharmacyPrescriptionQueryRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.response.LoadOutpatientPharmacyPrescriptionQueryPageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.response.PageOutpatientPharmacyPrescriptionQueryResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.support.OutpatientPharmacyPrescriptionQueryReadAssembler;
import com.openhis.health.application.outpatient.pharmacy.prescriptionquery.support.OutpatientPharmacyPrescriptionQuerySupport;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.query.OutpatientSignedPrescriptionDrugLinePageQuery;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.OutpatientSignedPrescriptionSummaryCountQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * 门诊处方查询应用服务（AS-OP-PHA-005，IF-OPQ-01～02）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、开方时间窗、分页窗口（失败码 EX-OPQ-101/102）。</li>
 *   <li>Pre：跨聚合预写 resolved*（B.6.4）与页内批量读；禁止按行 N+1。</li>
 *   <li>Asm：Mapper + ReadAssembler 装配平铺行；本期无写路径 Cmd。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/outpatientPharmacyPrescriptionQuery/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientPharmacyPrescriptionQueryAppService {
    /** 读路径登录门禁。 */
    private final CurrentOperatorResolver currentOperatorResolver;
    /** 分页窗口 Gate（pageNo/pageSize≥1）。 */
    private final PageWindowGate pageWindowGate;
    /** 术语下拉（处方类别 rx_type_code / 355）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 费用性质读侧（分类选项与页内合同索引）。 */
    private final ContractQueryService contractQueryService;
    /** 药品申请主读（已签发处方行分页）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 筛选项与 AMB/患者关键字预解析。 */
    private final OutpatientPharmacyPrescriptionQuerySupport prescriptionQuerySupport;
    /** 药品目录读侧（名称/规格/inject_flag）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    /** 发放读侧（状态筛预解析与行展示）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 收费明细读侧（状态筛预解析与行展示）。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 诊断定义读侧。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    /** 就诊诊断读侧：由 {@code encounterDiagnosisIds} 解析诊断定义。 */
    private final EncounterQueryService encounterQueryService;
    /** 门诊就诊上下文（门诊号、患者名）。 */
    private final EncounterContextReader encounterContextReader;
    /** 开单医生主档。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 开单科室主档（药嘱 org_id → 名称）。 */
    private final OrganizationQueryService organizationQueryService;
    /** 平铺行装配（含方级含输液）。 */
    private final OutpatientPharmacyPrescriptionQueryReadAssembler prescriptionQueryReadAssembler;
    /** Request/Response 与领域 Query 映射。 */
    private final OutpatientPharmacyPrescriptionQueryMapper prescriptionQueryMapper;
    /**
     * IF-OPQ-01：门诊处方查询页初始化。
     *
     * <p>下发收费/发药状态、处方类别 355、医保类别、费用性质三档、含输液三态、开单科室，以及默认本月开方日起止。</p>
     *
     * @param request 空体占位请求
     * @return 筛选项与默认本月开方日
     * @throws BusinessException EX-OPQ-101 未登录或元数据加载失败
     */
    public LoadOutpatientPharmacyPrescriptionQueryPageInitResponse loadOutpatientPharmacyPrescriptionQueryPageInit(
        LoadOutpatientPharmacyPrescriptionQueryPageInitRequest request) {
        // Gate：未登录则拒绝进页，避免匿名拉取术语与合同元数据
        this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyPrescriptionQueryConstants.EX_OPQ_101,
            "页面初始化失败，请稍后重试");
        try {
            // Pre：收费状态下拉（待收费/已结算/退款中/已退款）
            List<OptionDto> chargeStatusOptions = this.prescriptionQuerySupport.buildChargeStatusOptions();
            // Pre：发药状态下拉（覆盖未发与已发）
            List<OptionDto> dispenseStatusOptions = this.prescriptionQuerySupport.buildDispenseStatusOptions();
            // Pre：术语 355 处方类别
            List<OptionDto> rxTypeOptions =
                this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.RX_TYPE_CODE);
            // Pre：医保类别（门诊向 YbMedType）
            List<OptionDto> medTypeOptions = this.prescriptionQuerySupport.buildMedTypeOptions();
            // Pre：费用性质分类三档（自费/省医保/市医保）
            List<OptionDto> contractCategoryOptions =
                this.prescriptionQuerySupport.buildContractCategoryOptions();
            // Pre：含输液 ALL/YES/NO
            List<OptionDto> infusionFilterOptions =
                this.prescriptionQuerySupport.buildInfusionFilterOptions();
            // Pre：开单科室（门诊启用科室）
            List<OptionDto> organizationOptions =
                this.prescriptionQuerySupport.buildOrganizationOptions();
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
            String defaultFrom = monthStart.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            String defaultTo = monthEnd.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Asm：默认本月窗 + 步骤筛选项 → 初始化响应
            return this.prescriptionQueryMapper.toLoadPageInitResponse(
                defaultFrom,
                defaultTo,
                chargeStatusOptions,
                dispenseStatusOptions,
                rxTypeOptions,
                medTypeOptions,
                contractCategoryOptions,
                infusionFilterOptions,
                organizationOptions);
        } catch (RuntimeException ex) {
            log.warn("门诊处方查询页初始化失败", ex);
            throw new BusinessException(
                OutpatientPharmacyPrescriptionQueryConstants.EX_OPQ_101,
                "页面初始化失败，请稍后重试");
        }
    }
    /**
     * IF-OPQ-02：分页查询门诊已签发处方药品平铺明细。
     *
     * <p>主事实为 {@code med_medication_request} 已签发行；正方与退方均纳入；不做服务端按方小计。
     * 可选 {@code prescriptionNo} 精确匹配供详情整方重载；行内含用药明细字段供前端抽屉展示。</p>
     *
     * @param request 筛选与分页参数（开方时间窗必填）
     * @return 平铺分页明细（{@code totalCount} 对应 AS-SDD 字段 {@code total}）
     * @throws BusinessException EX-OPQ-101 未登录；EX-OPQ-102 时间窗非法；EX-OPQ-103 查询失败
     */
    public PageOutpatientPharmacyPrescriptionQueryResponse pageOutpatientPharmacyPrescriptionQuery(
        PageOutpatientPharmacyPrescriptionQueryRequest request) {
        // Gate：未登录禁止查询
        this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyPrescriptionQueryConstants.EX_OPQ_101,
            "页面初始化失败，请稍后重试");
        // Gate：分页窗口非法时直接拒绝，避免无界扫描
        this.pageWindowGate.requirePositivePageWindow(
            request,
            OutpatientPharmacyPrescriptionQueryConstants.EX_OPQ_103,
            "分页参数无效");
        // Gate：开立日起止必填且 from≤to，非法码 EX-OPQ-102，禁止进入 Pre
        LocalDate openedFrom = this.parseOpenedDateOrThrow(request.getOpenedDateFrom());
        LocalDate openedTo = this.parseOpenedDateOrThrow(request.getOpenedDateTo());
        if (openedFrom.isAfter(openedTo)) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionQueryConstants.EX_OPQ_102,
                "请选择有效的开方时间范围");
        }
        LocalDateTime openedStart = openedFrom.atStartOfDay();
        LocalDateTime openedEnd = openedTo.atTime(LocalTime.MAX);
        try {
            // Pre：患者关键字或全院 AMB 窗 → resolvedEncounterIds（AS-SDD §3.3 步骤 3）
            OutpatientPharmacyPrescriptionQuerySupport.PatientScope patientScope =
                this.prescriptionQuerySupport.enrichPatientKeywordFilters(
                    request.getPatientKeyword(),
                    openedStart,
                    openedEnd);
            // Pre：收费状态非空时预解析药品申请 id；空则不写 charge resolved
            List<Long> chargeResolvedIds = null;
            if (StringUtils.isNotEmpty(request.getChargeStatusEnum())) {
                chargeResolvedIds =
                    this.chargeItemQueryService.listDistinctServiceIdsByStatusEnumAndServiceTable(
                        request.getChargeStatusEnum().trim(),
                        TableNameConstants.MED_MEDICATION_REQUEST);
            }
            // Pre：发药状态非空时预解析药品申请 id；空则跳过
            List<Long> dispenseResolvedIds = null;
            if (StringUtils.isNotEmpty(request.getDispenseStatusEnum())) {
                dispenseResolvedIds =
                    this.medicationDispenseQueryService.listDistinctMedicationRequestIdsByStatusEnum(
                        request.getDispenseStatusEnum().trim());
            }
            // Pre：含输液≠ALL 时拉取注射目录主键；ALL 或空则跳过
            List<Long> injectMedicationIds = null;
            String infusionFilter = request.getInfusionFilterCode();
            if (StringUtils.isNotEmpty(infusionFilter)
                && !OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_ALL.equals(
                    infusionFilter.trim())) {
                injectMedicationIds = this.medicationDefinitionQueryService.listIdsByInjectFlag(Boolean.TRUE);
            }
            // Pre：费用性质分类非空时预解析合同 id；空则跳过
            List<Long> contractResolvedIds = null;
            if (StringUtils.isNotEmpty(request.getContractCategoryCode())) {
                contractResolvedIds =
                    this.contractQueryService.listIdsByCategoryEnum(request.getContractCategoryCode().trim());
            }
            // Asm：Request + 患者范围 → 领域分页 Query（开方时间窗随请求映射）
            OutpatientSignedPrescriptionDrugLinePageQuery query =
                this.prescriptionQueryMapper.toPageQuery(request, patientScope);
            query.setResolvedMedicationRequestIdsForChargeStatus(chargeResolvedIds);
            query.setResolvedMedicationRequestIdsForDispenseStatus(dispenseResolvedIds);
            query.setResolvedMedicationIdsForInjectFlag(injectMedicationIds);
            query.setResolvedContractIdsForCategory(contractResolvedIds);
            // Pre：医保类别非空时：诊断 id（AMB 范围）→ 引用该诊断的药品申请 id
            if (StringUtils.isNotEmpty(request.getMedTypeCode())) {
                List<Long> encounterScopeIds = query.getResolvedEncounterIds();
                List<Long> diagnosisIdsForMedType =
                    this.encounterQueryService.listEncounterDiagnosisIdsByMedTypeAndEncounterIdIn(
                        request.getMedTypeCode().trim(),
                        encounterScopeIds);
                List<Long> medTypeResolvedIds =
                    this.medicationRequestQueryService.listIdsReferencingAnyEncounterDiagnosisId(
                        diagnosisIdsForMedType);
                query.setResolvedMedicationRequestIdsForMedType(medTypeResolvedIds);
            }
            // Pre：主事实分页（已签发、正方+退方、BR-OPQ-001～016）
            Page<MedicationRequestListQueryModel> page =
                this.medicationRequestQueryService.pageOutpatientSignedPrescriptionDrugLines(query);
            // Pre：同规约轻量 COUNT DISTINCT（页脚总处方量 / 就诊人数；禁止二次拉明细）
            OutpatientSignedPrescriptionSummaryCountQueryModel summary =
                this.medicationRequestQueryService.countOutpatientSignedPrescriptionSummary(query);
            // Pre+Asm：页内批量装关联后装配平铺行
            List<OutpatientPharmacyPrescriptionDrugLineDto> rows =
                this.assemblePageRows(page.getContent());
            // Asm：分页元数据 + 平铺行 + 去重汇总
            return this.prescriptionQueryMapper.toPageResponse(
                page,
                rows,
                summary.getPrescriptionCount(),
                summary.getVisitPatientCount());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊处方查询分页失败", ex);
            throw new BusinessException(
                OutpatientPharmacyPrescriptionQueryConstants.EX_OPQ_103,
                "查询失败，请稍后重试");
        }
    }
    /**
     * 解析开方日；空白或格式非法抛 EX-OPQ-102。
     *
     * @param openedDate 开立日字符串（yyyy-MM-dd）
     * @return 解析后的本地日期
     */
    private LocalDate parseOpenedDateOrThrow(String openedDate) {
        if (StringUtils.isEmpty(openedDate)) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionQueryConstants.EX_OPQ_102,
                "请选择有效的开方时间范围");
        }
        try {
            return LocalDate.parse(openedDate.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionQueryConstants.EX_OPQ_102,
                "请选择有效的开方时间范围");
        }
    }
    /**
     * 页内批量预热关联读后装配平铺行（AS-SDD §3.3 步骤 10～17）。
     *
     * @param requestRows 本页药品申请读模型
     * @return 应用层明细行；无数据时返回空列表
     */
    private List<OutpatientPharmacyPrescriptionDrugLineDto> assemblePageRows(
        List<MedicationRequestListQueryModel> requestRows) {
        if (requestRows == null || requestRows.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> medicationRequestIds = new LinkedHashSet<>();
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterDiagnosisIds = new LinkedHashSet<>();
        LinkedHashSet<Long> contractIds = new LinkedHashSet<>();
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        // Loop：收集页内主键，供批量仓储读（禁止按行查）
        for (MedicationRequestListQueryModel row : requestRows) {
            if (row == null) {
                continue;
            }
            if (row.getId() != null) {
                medicationRequestIds.add(row.getId());
            }
            if (row.getMedicationId() != null) {
                medicationIds.add(row.getMedicationId());
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            collectEncounterDiagnosisIdsFromIds(row.getEncounterDiagnosisIds(), encounterDiagnosisIds);
            if (row.getContractId() != null) {
                contractIds.add(row.getContractId());
            }
            if (row.getRequesterId() != null) {
                practitionerIds.add(row.getRequesterId());
            }
            // 开单科室：药嘱 org_id（BR-MRQ-029）
            if (row.getOrgId() != null) {
                organizationIds.add(row.getOrgId());
            }
        }
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId = new HashMap<>();
        Map<Long, String> medTypeByEncounterDiagnosisId = new HashMap<>();
        Set<Long> conditionDefinitionIds = new HashSet<>();
        if (!encounterDiagnosisIds.isEmpty()) {
            for (EncounterDiagnosis ed : this.encounterQueryService.listEncounterDiagnosisEntitiesByIds(encounterDiagnosisIds)) {
                if (ed == null || ed.getId() == null) {
                    continue;
                }
                if (ed.getConditionDefinitionId() != null) {
                    conditionDefinitionIdByEncounterDiagnosisId.put(ed.getId(), ed.getConditionDefinitionId());
                    conditionDefinitionIds.add(ed.getConditionDefinitionId());
                }
                // 医保类别：就诊诊断行 med_type（方头取首位诊断）
                if (StringUtils.isNotEmpty(ed.getMedType())) {
                    medTypeByEncounterDiagnosisId.put(ed.getId(), ed.getMedType());
                }
            }
        }
        // Pre：药品定义（名称/规格/inject_flag）
        List<MedicationDefinition> definitions =
            this.medicationDefinitionQueryService.findAllByIds(medicationIds);
        // Pre：收费明细（service_id = 药品申请 id）
        ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
        chargeCmd.setServiceIds(medicationRequestIds);
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
        // Pre：发放行（代表态展示）
        List<MedicationDispense> dispenses =
            this.medicationDispenseQueryService.listMedicationDispensesByMedReqIds(medicationRequestIds);
        // Pre：诊断名称（ids 串首位就诊诊断 → conditionDefinitionId → 名称）
        List<ConditionDefinitionBatchItemQueryModel> conditions =
            this.conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(conditionDefinitionIds));
        // Pre：费用性质分类（全量合同索引后按页内 id 裁剪，避免逐条 findOptional）
        List<ContractListItemQueryModel> allContracts = this.contractQueryService.listAllContracts();
        List<ContractListItemQueryModel> pageContracts = new ArrayList<>();
        Map<Long, ContractListItemQueryModel> contractIndex =
            this.prescriptionQueryReadAssembler.indexContractById(allContracts);
        for (Long contractId : contractIds) {
            ContractListItemQueryModel contract = contractIndex.get(contractId);
            if (contract != null) {
                pageContracts.add(contract);
            }
        }
        // Pre：门诊号与患者名、电话
        List<OutpatientEncounterContextQueryModel> encounterContexts =
            this.encounterContextReader.listOutpatientContextsByEncounterIdsOrdered(
                new ArrayList<>(encounterIds));
        // Pre：开单医生姓名
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        // Pre：开单科室名称（页内 org_id 批量，禁止 N+1）
        Map<Long, String> organizationNameById = this.organizationQueryService.mapNamesByIds(organizationIds);
        // Asm：索引 + 方级含输液 + 平铺行
        return this.prescriptionQueryReadAssembler.assembleRows(
            requestRows,
            this.prescriptionQueryReadAssembler.indexMedicationById(definitions),
            this.prescriptionQueryReadAssembler.indexChargeByMedicationRequestId(chargeItems),
            this.prescriptionQueryReadAssembler.indexDispenseByMedicationRequestId(dispenses),
            this.prescriptionQueryReadAssembler.indexConditionById(conditions),
            conditionDefinitionIdByEncounterDiagnosisId,
            medTypeByEncounterDiagnosisId,
            this.prescriptionQueryReadAssembler.indexContractById(pageContracts),
            this.prescriptionQueryReadAssembler.indexEncounterContextById(encounterContexts),
            this.prescriptionQueryReadAssembler.indexPractitionerNames(practitioners),
            organizationNameById);
    }

    /**
     * 从医嘱绑定 ids 串 收集就诊诊断主键（去重写入 target）。
     *
     * @param idsText    {@code encounter_diagnosis_ids} 原始串
     * @param target 收集容器
     */
    private static void collectEncounterDiagnosisIdsFromIds(String idsText, Set<Long> target) {
        if (target == null || StringUtils.isEmpty(idsText)) {
            return;
        }
        String[] parts = idsText.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            try {
                target.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 跳过非法段，避免整页装配失败
            }
        }
    }
}
