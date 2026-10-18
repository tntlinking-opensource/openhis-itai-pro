/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.service;

import com.zxplat.application.report.nurseworkload.dto.NurseConsumableDetailExportRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseConsumableDetailRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseConsumableItemChipDto;
import com.zxplat.application.report.nurseworkload.dto.NurseConsumableSummaryRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseExecutionDetailExportRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseExecutionDetailRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseExecutionSummaryRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseWorkloadNurseCardDto;
import com.zxplat.application.report.nurseworkload.mapper.NurseWorkloadReportMapper;
import com.zxplat.application.report.nurseworkload.request.ExportNurseWorkloadDetailRequest;
import com.zxplat.application.report.nurseworkload.request.ListNurseWorkloadNurseCardsRequest;
import com.zxplat.application.report.nurseworkload.request.LoadNurseWorkloadFormInitRequest;
import com.zxplat.application.report.nurseworkload.request.NurseWorkloadQueryRequest;
import com.zxplat.application.report.nurseworkload.request.PageNurseConsumableWorkloadDetailRequest;
import com.zxplat.application.report.nurseworkload.request.PageNurseExecutionWorkloadDetailRequest;
import com.zxplat.application.report.nurseworkload.response.ExportNurseWorkloadDetailResponse;
import com.zxplat.application.report.nurseworkload.response.ListNurseWorkloadNurseCardsResponse;
import com.zxplat.application.report.nurseworkload.response.LoadNurseWorkloadFormInitResponse;
import com.zxplat.application.report.nurseworkload.response.PageNurseConsumableWorkloadDetailResponse;
import com.zxplat.application.report.nurseworkload.response.PageNurseExecutionWorkloadDetailResponse;
import com.zxplat.application.report.nurseworkload.response.SummarizeNurseConsumableWorkloadResponse;
import com.zxplat.application.report.nurseworkload.response.SummarizeNurseExecutionWorkloadResponse;
import com.zxplat.application.shared.chargediscount.ChargeDiscountApplySupport;
import com.zxplat.application.report.nurseworkload.support.NurseWorkloadAssembler;
import com.zxplat.application.report.nurseworkload.support.NurseWorkloadAmountEnricher;
import com.zxplat.application.report.nurseworkload.support.NurseWorkloadExportLoopResult;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.zxplat.domain.clinical.procedure.queries.model.NurseExecutionCategorySummaryQueryModel;
import com.zxplat.domain.clinical.procedure.queries.model.NurseExecutionWorkloadDetailQueryModel;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.zxplat.domain.share.constants.NurseWorkloadConstants;
import com.zxplat.domain.share.constants.NurseWorkloadExportConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.constants.PlatformRoleCodeConstants;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.devicedispense.queries.model.NurseConsumableHvcmSummaryQueryModel;
import com.zxplat.domain.workflow.devicedispense.queries.model.NurseConsumableWorkloadDetailQueryModel;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.practitioner.PractitionerRoleScopeLoader;
import com.zxplat.application.report.nurseworkload.support.NurseWorkloadLoader;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 护士工作量统计只读报表编排（AS-RPT-002 IF-RPT-NWL-01～06）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link NurseWorkloadLoader}：就诊 scope / 耗材开关 / SR·hvcm 预解析</li>
 *   <li>{@link ProcedureQueryService} / {@link DeviceDispenseQueryService}：净执行与耗材发放读侧</li>
 *   <li>{@link NurseWorkloadAssembler}：汇总合计与明细 enrich</li>
 *   <li>{@link NurseWorkloadReportMapper}：Request ↔ Query / Response 映射</li>
 * </ul>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/nurseWorkloadReport/{methodName}；不另写 RestController。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NurseWorkloadReportAppService {

    /** 操作员 Gate（登录上下文；非数据权限 Gate）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 护士工作量 scope 预解析。 */
    private final NurseWorkloadLoader nurseWorkloadLoader;

    /** 诊疗执行读侧。 */
    private final ProcedureQueryService procedureQueryService;

    /** 耗材发放读侧。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** ServiceRequest 批量 enrich。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** ActivityDefinition 批量 enrich。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** DeviceRequest 批量 enrich。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /** DeviceDefinition 批量 enrich。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /** 执业者读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 患者读侧。 */
    private final PatientQueryService patientQueryService;

    /** 就诊读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 科室主数据读侧（工作科室下拉：护士站 + 医技）。 */
    private final OrganizationQueryService organizationQueryService;

    /** 护士岗人员加载。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** 术语展示名加载。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 汇总/明细 enrich。 */
    private final NurseWorkloadAssembler nurseWorkloadAssembler;

    /** 明细金额 enrich。 */
    private final NurseWorkloadAmountEnricher nurseWorkloadAmountEnricher;

    /** 是否已配置有效折扣（ACTIVE 计数）。 */
    private final ChargeDiscountApplySupport chargeDiscountApplySupport;

    /** Request/Response 映射。 */
    private final NurseWorkloadReportMapper nurseWorkloadReportMapper;

    /**
     * IF-RPT-NWL-01：页面初始化（默认门诊+当日+工作科室与人员下拉）。
     *
     * @param request 可选 encounterScope / organizationId（刷新科室下拉与护士下拉口径）
     * @return 初始化响应
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_101} 初始化失败
     */
    public LoadNurseWorkloadFormInitResponse loadNurseWorkloadFormInit(
        @Valid LoadNurseWorkloadFormInitRequest request) {
        try {
            // Gate：须已登录，否则无法进入报表页
            this.currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.RPT_NWL_101,
                "页面配置加载失败，请稍后重试");
            // Gate：租户上下文须可用（AS-RPT-002-SDD §3.2 序号 2）
            this.requireTenantId();
            // Pre：护士 + 医技；已选工作科室时按岗位 org_id 收窄
            Long workDepartmentId = null;
            if (request != null) {
                workDepartmentId = request.getOrganizationId();
            }
            List<Practitioner> nurses = this.listPractitionersByWorkDepartment(workDepartmentId);
            // Asm：默认门诊+当日、就诊范围 / 工作科室 / 人员下拉
            LoadNurseWorkloadFormInitResponse response = new LoadNurseWorkloadFormInitResponse();
            LocalDate today = LocalDate.now();
            response.setDefaultEncounterScope(NurseWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT);
            response.setDefaultDateFrom(today);
            response.setDefaultDateTo(today);
            response.setEncounterScopeOptions(this.buildEncounterScopeOptions());
            // Pre：就诊范围入参空则默认门诊（工作科室下拉不随就诊域变化）
            String organizationScope = NurseWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT;
            if (request != null && StringUtils.isNotEmpty(request.getEncounterScope())) {
                organizationScope = request.getEncounterScope().trim();
            }
            // Pre：工作科室下拉 = 护士站 + 医技，不随门诊/住院收窄
            response.setOrganizationOptions(this.buildOrganizationOptions(organizationScope));
            response.setNurseOptions(this.buildNurseOptions(nurses));
            response.setDiscountFeatureEnabled(this.chargeDiscountApplySupport.isDiscountFeatureEnabled());
            return response;
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出，不包装为 101
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("护士工作量页初始化失败", runtimeException);
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_101,
                "页面配置加载失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-NWL-02：诊疗执行汇总查询。
     *
     * @param request 筛选条件
     * @return 汇总响应
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102} 筛选项非法；
     *         {@link ErrorCodeConstants#RPT_NWL_104} 查询失败
     */
    public SummarizeNurseExecutionWorkloadResponse summarizeNurseExecutionWorkload(
        @Valid NurseWorkloadQueryRequest request) {
        // Gate：门诊/住院与日期窗必填且合法
        this.validateNurseWorkloadQueryRequest(
            request.getEncounterScope(),
            request.getDateFrom(),
            request.getDateTo());
        // Pre：Request → 领域 Query（§3.3 序号 2）
        NurseWorkloadPageQuery query = this.nurseWorkloadReportMapper.toNurseWorkloadPageQuery(request);
        // Pre：工作科室 → 该科护士/医技人员集合（科室合计）
        this.applyWorkDepartmentScope(query, request.getOrganizationId());
        // Pre：就诊 scope + SR 类目；跳过耗材 hvcm（诊疗 IF 不需要）
        this.prepareNurseWorkloadQueryScopesSafely(query, true, false);
        // Pre：按 category_enum 汇总净 COMPLETED 次数（§3.3 序号 4）
        List<NurseExecutionCategorySummaryQueryModel> summaries = this.executeQuerySafely(
            () -> this.procedureQueryService.summarizeNurseExecutionWorkloadByCategory(query));
        // Pre：activity_category_code 术语展示名（§3.3 序号 5）
        Map<String, String> categoryLabelMap = this.terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE);
        // Asm：汇总行 + totalExecutionCount 页脚合计（§3.3 序号 6～7）
        SummarizeNurseExecutionWorkloadResponse response =
            this.nurseWorkloadAssembler.buildExecutionSummaryResponse(summaries, categoryLabelMap);
        NurseExecutionAmountTotals executionTotals = this.sumExecutionAmountsForQuery(query);
        response.setTotalSaleAmount(executionTotals.saleAmount());
        response.setTotalDiscountAmount(executionTotals.discountAmount());
        response.setTotalActualReceivedAmount(executionTotals.actualReceivedAmount());
        response.setTotalPurchaseAmount(null);
        return response;
    }

    /**
     * IF-RPT-NWL-03：诊疗执行明细分页查询。
     *
     * @param request 筛选与分页
     * @return 明细分页响应
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102} 筛选项非法；
     *         {@link ErrorCodeConstants#RPT_NWL_104} 查询失败
     */
    public PageNurseExecutionWorkloadDetailResponse pageNurseExecutionWorkloadDetail(
        @Valid PageNurseExecutionWorkloadDetailRequest request) {
        // Gate：筛选项与 IF-02 一致
        this.validateNurseWorkloadQueryRequest(
            request.getEncounterScope(),
            request.getDateFrom(),
            request.getDateTo());
        // Pre：Request → Query（含分页键；§3.4 序号 2）
        NurseWorkloadPageQuery query = this.nurseWorkloadReportMapper.toNurseWorkloadPageQuery(request);
        // Pre：工作科室 → 该科护士/医技人员集合；单人明细优先
        this.applyWorkDepartmentScope(query, request.getOrganizationId());
        // Pre：就诊 scope + SR 类目；跳过耗材 hvcm
        this.prepareNurseWorkloadQueryScopesSafely(query, true, false);
        // Pre：净 COMPLETED 明细分页，recorded_time DESC（§3.4 序号 4）
        Page<NurseExecutionWorkloadDetailQueryModel> page = this.executeQuerySafely(
            () -> this.procedureQueryService.pageNurseExecutionWorkloadDetail(query));
        List<NurseExecutionDetailRowDto> rows = this.mapExecutionDetailRows(page.getContent());
        // Pre：批量 SR / Activity / 人患就诊 + 类目术语（§3.4 序号 5～11）
        NurseWorkloadAssembler.NurseWorkloadExecutionDetailEnrichmentContext enrichmentContext =
            this.buildExecutionDetailEnrichmentContext(rows);
        // Asm：回填 GenerateSource、项目名等展示字段（§3.4 序号 12）
        this.nurseWorkloadAssembler.enrichExecutionDetailRows(rows, enrichmentContext);
        this.nurseWorkloadAmountEnricher.enrichExecutionDetailAmounts(rows, query.getRequestingOrgId());
        // Asm：组装分页响应（§3.4 序号 13）
        PageNurseExecutionWorkloadDetailResponse response = new PageNurseExecutionWorkloadDetailResponse();
        response.setTotal(page.getTotalElements());
        int resolvedPageNo = 1;
        if (query.getPageNo() != null) {
            resolvedPageNo = query.getPageNo();
        }
        response.setPageNo(resolvedPageNo);
        int resolvedPageSize = NurseWorkloadConstants.DEFAULT_PAGE_SIZE;
        if (query.getPageSize() != null) {
            resolvedPageSize = query.getPageSize();
        }
        response.setPageSize(resolvedPageSize);
        response.setRows(rows);
        return response;
    }

    /**
     * IF-RPT-NWL-04：耗材使用汇总查询。
     *
     * @param request 筛选条件
     * @return 汇总响应
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102} 筛选项非法；
     *         {@link ErrorCodeConstants#RPT_NWL_104} 查询失败
     */
    public SummarizeNurseConsumableWorkloadResponse summarizeNurseConsumableWorkload(
        @Valid NurseWorkloadQueryRequest request) {
        // Gate：筛选项合法
        this.validateNurseWorkloadQueryRequest(
            request.getEncounterScope(),
            request.getDateFrom(),
            request.getDateTo());
        // Pre：Request → Query（§3.5 序号 2）
        NurseWorkloadPageQuery query = this.nurseWorkloadReportMapper.toNurseWorkloadPageQuery(request);
        // Pre：工作科室 → 该科护士/医技人员集合（科室合计）
        this.applyWorkDepartmentScope(query, request.getOrganizationId());
        // Pre：就诊 scope + 耗材 hvcm；跳过 SR 类目
        this.prepareNurseWorkloadQueryScopesSafely(query, false, true);
        // Pre：按 hvcm_flag 汇总发放数量（§3.5 序号 4）
        List<NurseConsumableHvcmSummaryQueryModel> summaries = this.executeQuerySafely(
            () -> this.deviceDispenseQueryService.summarizeNurseConsumableWorkloadByHvcm(query));
        // Asm：汇总行 + totalQuantity 页脚合计（§3.5 序号 5～6）
        SummarizeNurseConsumableWorkloadResponse response =
            this.nurseWorkloadAssembler.buildConsumableSummaryResponse(summaries);
        // Pre：售价/进价同一次事实扫描 + 分块 enrich，禁止为页脚扫库两次
        NurseConsumableAmountTotals consumableTotals = this.sumConsumableAmountsForQuery(query);
        response.setTotalSaleAmount(consumableTotals.saleAmount());
        response.setTotalDiscountAmount(consumableTotals.discountAmount());
        response.setTotalActualReceivedAmount(consumableTotals.actualReceivedAmount());
        response.setTotalPurchaseAmount(consumableTotals.purchaseAmount());
        return response;
    }

    /**
     * 护士卡片墙批量查询：诊疗+耗材事实一次加载后按护士分桶（轻量 enrich，禁止患者/就诊展示装载）。
     *
     * @param request 与汇总同筛选
     * @return 有数据的护士卡片
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102}/{@link ErrorCodeConstants#RPT_NWL_104}
     */
    public ListNurseWorkloadNurseCardsResponse listNurseWorkloadNurseCards(
        @Valid ListNurseWorkloadNurseCardsRequest request) {
        // Gate：筛选项合法
        this.validateNurseWorkloadQueryRequest(
            request.getEncounterScope(),
            request.getDateFrom(),
            request.getDateTo());
        NurseWorkloadPageQuery query = new NurseWorkloadPageQuery();
        query.setEncounterScope(request.getEncounterScope());
        query.setDateFrom(request.getDateFrom());
        query.setDateTo(request.getDateTo());
        // 显式开立科室金额收窄（若前端仍传）；科室统计主路径用 organizationId→护士集合
        query.setRequestingOrgId(request.getRequestingOrgId());
        // Pre：工作科室 → 该科护士/医技人员集合（卡片仅该科人员）
        this.applyWorkDepartmentScope(query, request.getOrganizationId());
        // Pre：两侧 enrich 一次（卡片同时需要类目与 hvcm）
        this.prepareNurseWorkloadQueryScopesSafely(query, true, true);
        // Pre：全量事实行一次加载（禁止前端翻页重复扫候选集）
        List<NurseExecutionWorkloadDetailQueryModel> executionModels = this.executeQuerySafely(
            () -> this.procedureQueryService.listNurseExecutionWorkloadDetail(query));
        List<NurseConsumableWorkloadDetailQueryModel> consumableModels = this.executeQuerySafely(
            () -> this.deviceDispenseQueryService.listNurseConsumableWorkloadDetail(query));
        List<NurseExecutionDetailRowDto> executionRows = this.mapExecutionDetailRows(executionModels);
        List<NurseConsumableDetailRowDto> consumableRows = this.mapConsumableDetailRows(consumableModels);
        // Pre：类目展示名（术语）+ 金额；跳过 SR/患者/就诊名
        Map<String, String> categoryLabelMap = this.terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE);
        for (NurseExecutionDetailRowDto row : executionRows) {
            if (row == null) {
                continue;
            }
            String categoryEnum = row.getCategoryEnum();
            if (categoryEnum != null && categoryLabelMap.containsKey(categoryEnum)) {
                row.setCategoryDisplay(categoryLabelMap.get(categoryEnum));
            } else if (row.getCategoryDisplay() == null) {
                row.setCategoryDisplay("未分类");
            }
        }
        this.nurseWorkloadAmountEnricher.enrichExecutionDetailAmounts(executionRows, query.getRequestingOrgId());
        // Pre：耗材品名/规格/hvcm 展示（仅 DeviceDefinition 批量）
        this.fillConsumableCardDisplayFields(consumableRows);
        Map<Long, Long> deviceReqIdByDispenseId =
            this.buildDeviceReqIdByDispenseIdForAmountSum(consumableModels);
        this.nurseWorkloadAmountEnricher.enrichConsumableDetailAmounts(
            consumableRows, deviceReqIdByDispenseId, query.getRequestingOrgId());
        // Asm：页脚合计先于分桶（含无主执行人行，与 IF-02/04 同口径；禁止只加卡片和）
        ListNurseWorkloadNurseCardsResponse response = new ListNurseWorkloadNurseCardsResponse();
        response.setTotalExecutionCount((long) executionRows.size());
        response.setTotalExecutionSaleAmount(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
            this.collectSaleAmounts(executionRows)));
        response.setTotalExecutionDiscountAmount(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
            this.collectDiscountAmounts(executionRows)));
        response.setTotalExecutionActualReceivedAmount(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
            this.collectActualReceivedAmounts(executionRows)));
        response.setTotalConsumableQuantity(this.sumConsumableQuantities(consumableRows));
        response.setTotalConsumableSaleAmount(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
            this.collectSaleAmountsFromConsumable(consumableRows)));
        response.setTotalConsumableDiscountAmount(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
            this.collectDiscountAmountsFromConsumable(consumableRows)));
        response.setTotalConsumableActualReceivedAmount(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
            this.collectActualReceivedAmountsFromConsumable(consumableRows)));
        response.setTotalConsumablePurchaseAmount(this.sumNullableDetailAmounts(
            this.collectPurchaseAmountsFromConsumable(consumableRows)));
        // Asm：按护士分桶组装卡片（无 practitionerId 的行仅进页脚、不进卡片）
        Map<Long, List<NurseExecutionDetailRowDto>> executionByNurse =
            this.bucketExecutionRowsByNurse(executionRows);
        Map<Long, List<NurseConsumableDetailRowDto>> consumableByNurse =
            this.bucketConsumableRowsByNurse(consumableRows);
        LinkedHashSet<Long> nurseIds = new LinkedHashSet<>();
        nurseIds.addAll(executionByNurse.keySet());
        nurseIds.addAll(consumableByNurse.keySet());
        List<NurseWorkloadNurseCardDto> cards = new ArrayList<>();
        for (Long nurseId : nurseIds) {
            List<NurseExecutionDetailRowDto> nurseExecution =
                executionByNurse.getOrDefault(nurseId, List.of());
            List<NurseConsumableDetailRowDto> nurseConsumable =
                consumableByNurse.getOrDefault(nurseId, List.of());
            NurseWorkloadNurseCardDto card = new NurseWorkloadNurseCardDto();
            card.setPractitionerId(nurseId);
            card.setExecutionTotalCount((long) nurseExecution.size());
            card.setExecutionSaleAmountTotal(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
                this.collectSaleAmounts(nurseExecution)));
            card.setExecutionDiscountAmountTotal(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
                this.collectDiscountAmounts(nurseExecution)));
            card.setExecutionActualReceivedAmountTotal(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
                this.collectActualReceivedAmounts(nurseExecution)));
            card.setExecutionPurchaseAmountTotal(null);
            card.setExecutionSummaryRows(this.buildExecutionSummaryRowsFromDetails(nurseExecution));
            card.setConsumableTotalQuantity(this.sumConsumableQuantities(nurseConsumable));
            card.setConsumableSaleAmountTotal(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
                this.collectSaleAmountsFromConsumable(nurseConsumable)));
            card.setConsumableDiscountAmountTotal(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
                this.collectDiscountAmountsFromConsumable(nurseConsumable)));
            card.setConsumableActualReceivedAmountTotal(this.nurseWorkloadAmountEnricher.sumDetailAmounts(
                this.collectActualReceivedAmountsFromConsumable(nurseConsumable)));
            card.setConsumablePurchaseAmountTotal(this.sumNullableDetailAmounts(
                this.collectPurchaseAmountsFromConsumable(nurseConsumable)));
            card.setConsumableSummaryRows(this.buildConsumableSummaryRowsFromDetails(nurseConsumable));
            card.setConsumableItemChips(this.buildConsumableItemChips(nurseConsumable));
            cards.add(card);
        }
        response.setCards(cards);
        return response;
    }

    /**
     * IF-RPT-NWL-05：耗材使用明细分页查询。
     *
     * @param request 筛选与分页
     * @return 明细分页响应
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102} 筛选项非法；
     *         {@link ErrorCodeConstants#RPT_NWL_104} 查询失败
     */
    public PageNurseConsumableWorkloadDetailResponse pageNurseConsumableWorkloadDetail(
        @Valid PageNurseConsumableWorkloadDetailRequest request) {
        // Gate：筛选项与 IF-04 一致
        this.validateNurseWorkloadQueryRequest(
            request.getEncounterScope(),
            request.getDateFrom(),
            request.getDateTo());
        // Pre：Request → Query（§3.6 序号 2）
        NurseWorkloadPageQuery query = this.nurseWorkloadReportMapper.toNurseWorkloadPageQuery(request);
        // Pre：工作科室 → 该科护士/医技人员集合；单人明细优先
        this.applyWorkDepartmentScope(query, request.getOrganizationId());
        // Pre：就诊 scope + 耗材 hvcm；跳过 SR 类目
        this.prepareNurseWorkloadQueryScopesSafely(query, false, true);
        // Pre：有效发放行分页（§3.6 序号 4）
        Page<NurseConsumableWorkloadDetailQueryModel> page = this.executeQuerySafely(
            () -> this.deviceDispenseQueryService.pageNurseConsumableWorkloadDetail(query));
        List<NurseConsumableDetailRowDto> rows = this.mapConsumableDetailRows(page.getContent());
        // Pre：批量 DeviceRequest / Definition / 人患就诊 + 单位术语（§3.6 序号 5～11）
        NurseWorkloadAssembler.NurseWorkloadConsumableDetailEnrichmentContext enrichmentContext =
            this.buildConsumableDetailEnrichmentContext(page.getContent());
        // Asm：回填展示字段（§3.6 序号 12）
        this.nurseWorkloadAssembler.enrichConsumableDetailRows(rows, enrichmentContext);
        this.nurseWorkloadAmountEnricher.enrichConsumableDetailAmounts(
            rows,
            enrichmentContext.getDeviceReqIdByDispenseId(),
            query.getRequestingOrgId());
        // Asm：组装分页响应（§3.6 序号 13）
        PageNurseConsumableWorkloadDetailResponse response = new PageNurseConsumableWorkloadDetailResponse();
        response.setTotal(page.getTotalElements());
        int resolvedPageNo = 1;
        if (query.getPageNo() != null) {
            resolvedPageNo = query.getPageNo();
        }
        response.setPageNo(resolvedPageNo);
        int resolvedPageSize = NurseWorkloadConstants.DEFAULT_PAGE_SIZE;
        if (query.getPageSize() != null) {
            resolvedPageSize = query.getPageSize();
        }
        response.setPageSize(resolvedPageSize);
        response.setRows(rows);
        return response;
    }

    /**
     * IF-RPT-NWL-06：导出当前主 Tab 明细 Excel。
     *
     * @param request 主 Tab 与筛选
     * @return 导出响应
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102} 筛选项非法；
     *         {@link ErrorCodeConstants#RPT_NWL_201} 无明细；
     *         {@link ErrorCodeConstants#RPT_NWL_105} 导出失败
     */
    public ExportNurseWorkloadDetailResponse exportNurseWorkloadDetail(
        @Valid ExportNurseWorkloadDetailRequest request) {
        // Gate：筛选项与主 Tab 合法
        this.validateNurseWorkloadQueryRequest(
            request.getEncounterScope(),
            request.getDateFrom(),
            request.getDateTo());
        this.validateMainTabCode(request.getMainTabCode());
        // Gate：导出须已登录（§6.5 审计记 operatorId）
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.RPT_NWL_105,
            "导出失败，请缩小日期范围后重试");
        try {
            // Pre：导出 Query（无分页键）+ 按主 Tab 按需 enrich（§3.7 序号 2～3）
            NurseWorkloadPageQuery query = this.nurseWorkloadReportMapper.toNurseWorkloadPageQuery(request);
            // Pre：工作科室 → 该科护士/医技人员集合；单人优先
            this.applyWorkDepartmentScope(query, request.getOrganizationId());
            boolean needExecution = NurseWorkloadConstants.MAIN_TAB_EXECUTION.equals(request.getMainTabCode());
            this.prepareNurseWorkloadQueryScopesSafely(query, needExecution, !needExecution);
            boolean truncated;
            byte[] fileBytes;
            int exportedRowCount;
            if (NurseWorkloadConstants.MAIN_TAB_EXECUTION.equals(request.getMainTabCode())) {
                // Pre：复用 IF-03 编排序列循环拉取全量诊疗明细（§3.7 序号 4）
                NurseWorkloadExportLoopResult<NurseExecutionDetailRowDto> loopResult =
                    this.exportExecutionDetailsLoop(query);
                List<NurseExecutionDetailRowDto> allRows = loopResult.getRows();
                truncated = loopResult.isTruncated();
                // Gate：0 行拒绝导出（§3.7 序号 5 · NWL-SDD-R-004）
                this.assertExportNotEmpty(allRows.size());
                List<NurseExecutionDetailExportRowDto> exportRows = new ArrayList<>();
                for (NurseExecutionDetailRowDto row : allRows) {
                    exportRows.add(this.nurseWorkloadReportMapper.toExecutionExportRowDto(row));
                }
                // Asm：诊疗明细 Excel（§3.7 序号 6；前端 exportColumns 裁剪）
                fileBytes = this.nurseWorkloadReportMapper.buildExecutionExcelBytes(
                    exportRows, request.getExportColumns());
                exportedRowCount = exportRows.size();
            } else {
                // Pre：复用 IF-05 编排序列循环拉取全量耗材明细（§3.7 序号 4）
                NurseWorkloadExportLoopResult<NurseConsumableDetailRowDto> loopResult =
                    this.exportConsumableDetailsLoop(query);
                List<NurseConsumableDetailRowDto> allRows = loopResult.getRows();
                truncated = loopResult.isTruncated();
                // Gate：0 行拒绝导出（§3.7 序号 5）
                this.assertExportNotEmpty(allRows.size());
                List<NurseConsumableDetailExportRowDto> exportRows = new ArrayList<>();
                for (NurseConsumableDetailRowDto row : allRows) {
                    exportRows.add(this.nurseWorkloadReportMapper.toConsumableExportRowDto(row));
                }
                // Asm：耗材明细 Excel（§3.7 序号 6；前端 exportColumns 裁剪）
                fileBytes = this.nurseWorkloadReportMapper.buildConsumableExcelBytes(
                    exportRows, request.getExportColumns());
                exportedRowCount = exportRows.size();
            }
            log.info("护士工作量导出成功 operatorId={} mainTab={} rowCount={} truncated={}",
                operatorId, request.getMainTabCode(), exportedRowCount, truncated);
            // Asm：导出响应（§3.7 序号 7）
            ExportNurseWorkloadDetailResponse response = new ExportNurseWorkloadDetailResponse();
            response.setFileName(this.buildExportFileName(request.getMainTabCode()));
            response.setContentType(InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE);
            response.setFileBytes(fileBytes);
            response.setTruncated(truncated);
            response.setExportedRowCount(exportedRowCount);
            response.setMainTabCode(request.getMainTabCode());
            return response;
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("护士工作量导出失败 mainTab={}", request.getMainTabCode(), runtimeException);
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_105,
                "导出失败，请缩小日期范围后重试",
                runtimeException);
        }
    }

    /**
     * 汇总诊疗执行明细售价与优惠：一次拉全量事实行，按 IN 分块 enrich 后累加。
     *
     * @param query 已预解析读查询
     * @return 售价/优惠合计（结算 2 位）
     */
    private NurseExecutionAmountTotals sumExecutionAmountsForQuery(NurseWorkloadPageQuery query) {
        List<NurseExecutionWorkloadDetailQueryModel> allModels =
            this.procedureQueryService.listNurseExecutionWorkloadDetail(query);
        if (allModels == null || allModels.isEmpty()) {
            BigDecimal zero = this.nurseWorkloadAmountEnricher.sumDetailAmounts(List.of());
            return new NurseExecutionAmountTotals(zero, zero, zero);
        }
        List<BigDecimal> saleAmounts = new ArrayList<>();
        List<BigDecimal> discountAmounts = new ArrayList<>();
        List<BigDecimal> actualReceivedAmounts = new ArrayList<>();
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        int from = 0;
        while (from < allModels.size()) {
            int to = Math.min(from + chunkSize, allModels.size());
            List<NurseExecutionWorkloadDetailQueryModel> chunk = allModels.subList(from, to);
            List<NurseExecutionDetailRowDto> rows = this.mapExecutionDetailRows(chunk);
            this.nurseWorkloadAmountEnricher.enrichExecutionDetailAmounts(rows, query.getRequestingOrgId());
            saleAmounts.addAll(this.collectSaleAmounts(rows));
            discountAmounts.addAll(this.collectDiscountAmounts(rows));
            actualReceivedAmounts.addAll(this.collectActualReceivedAmounts(rows));
            from = to;
        }
        return new NurseExecutionAmountTotals(
            this.nurseWorkloadAmountEnricher.sumDetailAmounts(saleAmounts),
            this.nurseWorkloadAmountEnricher.sumDetailAmounts(discountAmounts),
            this.nurseWorkloadAmountEnricher.sumDetailAmounts(actualReceivedAmounts));
    }

    /**
     * 汇总耗材使用明细进售价：一次拉齐事实行，分块 enrich 后同时得到售价与进价页脚。
     *
     * <p>页脚合计禁止静默截断；禁止为售价/进价各扫一次候选发放集。</p>
     *
     * @param query 已预解析读查询
     * @return 售价合计 + 进价合计（全无进价时 purchase 为 null）
     */
    private NurseConsumableAmountTotals sumConsumableAmountsForQuery(NurseWorkloadPageQuery query) {
        // Pre：一次拉齐耗材净发放事实行
        List<NurseConsumableWorkloadDetailQueryModel> allModels =
            this.deviceDispenseQueryService.listNurseConsumableWorkloadDetail(query);
        if (allModels == null || allModels.isEmpty()) {
            BigDecimal zero = this.nurseWorkloadAmountEnricher.sumDetailAmounts(List.of());
            return new NurseConsumableAmountTotals(zero, null, zero, zero);
        }
        List<BigDecimal> saleAmounts = new ArrayList<>();
        List<BigDecimal> purchaseAmounts = new ArrayList<>();
        List<BigDecimal> discountAmounts = new ArrayList<>();
        List<BigDecimal> actualReceivedAmounts = new ArrayList<>();
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        int from = 0;
        // Loop：分块装锚点进售价，累加后释放本块明细
        while (from < allModels.size()) {
            int to = Math.min(from + chunkSize, allModels.size());
            List<NurseConsumableWorkloadDetailQueryModel> chunk = allModels.subList(from, to);
            List<NurseConsumableDetailRowDto> rows = this.mapConsumableDetailRows(chunk);
            Map<Long, Long> deviceReqIdByDispenseId =
                this.buildDeviceReqIdByDispenseIdForAmountSum(chunk);
            this.nurseWorkloadAmountEnricher.enrichConsumableDetailAmounts(
                rows, deviceReqIdByDispenseId, query.getRequestingOrgId());
            saleAmounts.addAll(this.collectSaleAmountsFromConsumable(rows));
            discountAmounts.addAll(this.collectDiscountAmountsFromConsumable(rows));
            actualReceivedAmounts.addAll(this.collectActualReceivedAmountsFromConsumable(rows));
            purchaseAmounts.addAll(this.collectPurchaseAmountsFromConsumable(rows));
            from = to;
        }
        return new NurseConsumableAmountTotals(
            this.nurseWorkloadAmountEnricher.sumDetailAmounts(saleAmounts),
            this.sumNullableDetailAmounts(purchaseAmounts),
            this.nurseWorkloadAmountEnricher.sumDetailAmounts(discountAmounts),
            this.nurseWorkloadAmountEnricher.sumDetailAmounts(actualReceivedAmounts));
    }

    /**
     * 耗材汇总页脚进售价/优惠。
     *
     * @param saleAmount            售价合计（结算刻度）
     * @param purchaseAmount        进价合计；全无进价时为 null
     * @param discountAmount        优惠合计（结算刻度）
     * @param actualReceivedAmount  实收合计（结算刻度）
     */
    private record NurseConsumableAmountTotals(
        BigDecimal saleAmount,
        BigDecimal purchaseAmount,
        BigDecimal discountAmount,
        BigDecimal actualReceivedAmount) {
    }

    /**
     * 诊疗执行页脚售价/优惠/实收。
     *
     * @param saleAmount           售价合计
     * @param discountAmount       优惠合计
     * @param actualReceivedAmount 实收合计
     */
    private record NurseExecutionAmountTotals(
        BigDecimal saleAmount,
        BigDecimal discountAmount,
        BigDecimal actualReceivedAmount) {
    }

    /**
     * Gate：就诊范围与日期窗必填且合法（BR-RPT-NWL-001/002）。
     *
     * @param encounterScope 门诊/住院
     * @param dateFrom       统计日起
     * @param dateTo         统计日止
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102}
     */
    private void validateNurseWorkloadQueryRequest(
        String encounterScope,
        LocalDate dateFrom,
        LocalDate dateTo) {
        // Gate：门诊/住院必选
        if (StringUtils.isEmpty(encounterScope)) {
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_102,
                "请选择门诊或住院及有效的查询日期");
        }
        boolean scopeSupported = NurseWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(encounterScope)
            || NurseWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT.equals(encounterScope);
        if (!scopeSupported) {
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_102,
                "请选择门诊或住院及有效的查询日期");
        }
        // Gate：日期窗必填
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_102,
                "请选择门诊或住院及有效的查询日期");
        }
        // Gate：止日不得早于起日
        if (dateFrom.isAfter(dateTo)) {
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_102,
                "请选择门诊或住院及有效的查询日期");
        }
    }

    /**
     * Gate：导出主 Tab 须为 EXECUTION 或 CONSUMABLE（BR-RPT-NWL-019）。
     *
     * @param mainTabCode 主 Tab 码
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102}
     */
    private void validateMainTabCode(String mainTabCode) {
        // Gate：mainTabCode 必填（BR-RPT-NWL-019）
        if (StringUtils.isEmpty(mainTabCode)) {
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_102,
                "请选择门诊或住院及有效的查询日期");
        }
        boolean supported = NurseWorkloadConstants.MAIN_TAB_EXECUTION.equals(mainTabCode)
            || NurseWorkloadConstants.MAIN_TAB_CONSUMABLE.equals(mainTabCode);
        // Gate：仅允许 EXECUTION / CONSUMABLE 两个主 Tab
        if (!supported) {
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_102,
                "请选择门诊或住院及有效的查询日期");
        }
    }

    /**
     * Pre：调用 Support 预解析 scope；IAE 映射为用户可读提示。
     *
     * @param query                 领域读查询
     * @param needExecutionCategory 是否 enrich SR 类目
     * @param needConsumableHvcm    是否 enrich 耗材 hvcm
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102}
     */
    private void prepareNurseWorkloadQueryScopesSafely(
        NurseWorkloadPageQuery query,
        boolean needExecutionCategory,
        boolean needConsumableHvcm) {
        try {
            this.nurseWorkloadLoader.prepareNurseWorkloadQueryScopes(
                query, needExecutionCategory, needConsumableHvcm);
        } catch (IllegalArgumentException illegalArgumentException) {
            // Support 层 IAE 映射为 EX-RPT-NWL-102 用户提示
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_102,
                "请选择门诊或住院及有效的查询日期",
                illegalArgumentException);
        }
    }

    /**
     * Pre：包装领域 QS 调用，运行时异常映射为查询失败。
     *
     * @param supplier 领域读调用
     * @param <T>      返回类型
     * @return 查询结果
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_104}
     */
    private <T> T executeQuerySafely(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // 领域 QS 未预期异常统一映射 EX-RPT-NWL-104
            log.error("护士工作量查询失败", runtimeException);
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_104,
                "查询失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * Gate：导出拒绝空明细（NWL-SDD-R-004）。
     *
     * @param rowCount 聚合行数
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_201}
     */
    private void assertExportNotEmpty(int rowCount) {
        if (rowCount <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_201,
                "当前条件下无明细可导出");
        }
    }

    /**
     * 从耗材明细领域行提取发放→请求映射（汇总金额用；不装载展示上下文）。
     *
     * @param models 当前页领域明细
     * @return dispenseId → deviceReqId
     */
    private Map<Long, Long> buildDeviceReqIdByDispenseIdForAmountSum(
        List<NurseConsumableWorkloadDetailQueryModel> models) {
        Map<Long, Long> deviceReqIdByDispenseId = new HashMap<>();
        if (models == null) {
            return deviceReqIdByDispenseId;
        }
        for (NurseConsumableWorkloadDetailQueryModel model : models) {
            if (model == null || model.getDispenseId() == null || model.getDeviceReqId() == null) {
                continue;
            }
            deviceReqIdByDispenseId.put(model.getDispenseId(), model.getDeviceReqId());
        }
        return deviceReqIdByDispenseId;
    }

    /**
     * 循环分页拉取诊疗执行明细直至空页或触顶 EXPORT_MAX_ROWS（§3.7 序号 4 · EXECUTION 路径）。
     *
     * @param baseQuery 已预解析的基础 Query
     * @return 全量 enrich 后的明细行及是否截断
     */
    private NurseWorkloadExportLoopResult<NurseExecutionDetailRowDto> exportExecutionDetailsLoop(
        NurseWorkloadPageQuery baseQuery) {
        List<NurseExecutionDetailRowDto> aggregated = new ArrayList<>();
        boolean truncated = false;
        int pageNo = 1;
        while (aggregated.size() < NurseWorkloadExportConstants.EXPORT_MAX_ROWS) {
            NurseWorkloadPageQuery pageQuery = this.copyQueryWithPaging(
                baseQuery, pageNo, NurseWorkloadExportConstants.EXPORT_PAGE_SIZE);
            Page<NurseExecutionWorkloadDetailQueryModel> page =
                this.procedureQueryService.pageNurseExecutionWorkloadDetail(pageQuery);
            // 空页表示已无更多命中行
            if (page.getContent().isEmpty()) {
                break;
            }
            List<NurseExecutionDetailRowDto> rows = this.mapExecutionDetailRows(page.getContent());
            NurseWorkloadAssembler.NurseWorkloadExecutionDetailEnrichmentContext context =
                this.buildExecutionDetailEnrichmentContext(rows);
            this.nurseWorkloadAssembler.enrichExecutionDetailRows(rows, context);
            this.nurseWorkloadAmountEnricher.enrichExecutionDetailAmounts(rows, baseQuery.getRequestingOrgId());
            for (NurseExecutionDetailRowDto row : rows) {
                if (aggregated.size() >= NurseWorkloadExportConstants.EXPORT_MAX_ROWS) {
                    // 触顶 EXPORT_MAX_ROWS，标记 truncated 并停止追加
                    truncated = true;
                    break;
                }
                aggregated.add(row);
            }
            if (truncated) {
                break;
            }
            if (page.getContent().size() < NurseWorkloadExportConstants.EXPORT_PAGE_SIZE) {
                break;
            }
            pageNo = pageNo + 1;
        }
        return new NurseWorkloadExportLoopResult<>(aggregated, truncated);
    }

    /**
     * 循环分页拉取耗材使用明细直至空页或触顶 EXPORT_MAX_ROWS（§3.7 序号 4 · CONSUMABLE 路径）。
     *
     * @param baseQuery 已预解析的基础 Query
     * @return 全量 enrich 后的明细行及是否截断
     */
    private NurseWorkloadExportLoopResult<NurseConsumableDetailRowDto> exportConsumableDetailsLoop(
        NurseWorkloadPageQuery baseQuery) {
        List<NurseConsumableDetailRowDto> aggregated = new ArrayList<>();
        boolean truncated = false;
        int pageNo = 1;
        while (aggregated.size() < NurseWorkloadExportConstants.EXPORT_MAX_ROWS) {
            NurseWorkloadPageQuery pageQuery = this.copyQueryWithPaging(
                baseQuery, pageNo, NurseWorkloadExportConstants.EXPORT_PAGE_SIZE);
            Page<NurseConsumableWorkloadDetailQueryModel> page =
                this.deviceDispenseQueryService.pageNurseConsumableWorkloadDetail(pageQuery);
            if (page.getContent().isEmpty()) {
                break;
            }
            List<NurseConsumableDetailRowDto> rows = this.mapConsumableDetailRows(page.getContent());
            NurseWorkloadAssembler.NurseWorkloadConsumableDetailEnrichmentContext context =
                this.buildConsumableDetailEnrichmentContext(page.getContent());
            this.nurseWorkloadAssembler.enrichConsumableDetailRows(rows, context);
            this.nurseWorkloadAmountEnricher.enrichConsumableDetailAmounts(
                rows,
                context.getDeviceReqIdByDispenseId(),
                baseQuery.getRequestingOrgId());
            for (NurseConsumableDetailRowDto row : rows) {
                if (aggregated.size() >= NurseWorkloadExportConstants.EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                aggregated.add(row);
            }
            if (truncated) {
                break;
            }
            if (page.getContent().size() < NurseWorkloadExportConstants.EXPORT_PAGE_SIZE) {
                break;
            }
            pageNo = pageNo + 1;
        }
        return new NurseWorkloadExportLoopResult<>(aggregated, truncated);
    }

    /**
     * 复制预解析 Query 并写入分页键（导出循环用）。
     *
     * @param source   源 Query
     * @param pageNo   页码
     * @param pageSize 页大小
     * @return 新 Query 实例
     */
    private NurseWorkloadPageQuery copyQueryWithPaging(NurseWorkloadPageQuery source, int pageNo, int pageSize) {
        NurseWorkloadPageQuery query = new NurseWorkloadPageQuery();
        query.setDateFrom(source.getDateFrom());
        query.setDateTo(source.getDateTo());
        query.setEncounterScope(source.getEncounterScope());
        query.setPractitionerId(source.getPractitionerId());
        query.setResolvedEncounterClassCode(source.getResolvedEncounterClassCode());
        query.setResolvedEncounterScope(source.getResolvedEncounterScope());
        query.setDeviceAutoInventoryDeductEnabled(source.getDeviceAutoInventoryDeductEnabled());
        query.setCategoryEnumByServiceRequestId(source.getCategoryEnumByServiceRequestId());
        query.setHvcmFlagByDeviceDefId(source.getHvcmFlagByDeviceDefId());
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return query;
    }

    /**
     * 领域诊疗明细 QueryModel 列表 → DTO 列表。
     *
     * @param models 领域明细行
     * @return DTO 列表
     */
    private List<NurseExecutionDetailRowDto> mapExecutionDetailRows(
        List<NurseExecutionWorkloadDetailQueryModel> models) {
        List<NurseExecutionDetailRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (NurseExecutionWorkloadDetailQueryModel model : models) {
            NurseExecutionDetailRowDto row = this.nurseWorkloadReportMapper.toDetailRowDto(model);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 领域耗材明细 QueryModel 列表 → DTO 列表。
     *
     * @param models 领域明细行
     * @return DTO 列表
     */
    private List<NurseConsumableDetailRowDto> mapConsumableDetailRows(
        List<NurseConsumableWorkloadDetailQueryModel> models) {
        List<NurseConsumableDetailRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (NurseConsumableWorkloadDetailQueryModel model : models) {
            NurseConsumableDetailRowDto row = this.nurseWorkloadReportMapper.toDetailRowDto(model);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 构建诊疗执行明细 enrich 上下文（批量 SR / Activity / 人患就诊 + 类目术语）。
     *
     * @param rows 当前页明细 DTO
     * @return enrich 上下文
     */
    private NurseWorkloadAssembler.NurseWorkloadExecutionDetailEnrichmentContext buildExecutionDetailEnrichmentContext(
        List<NurseExecutionDetailRowDto> rows) {
        Set<Long> serviceRequestIds = new LinkedHashSet<>();
        Set<Long> practitionerIds = new LinkedHashSet<>();
        Set<Long> patientIds = new LinkedHashSet<>();
        Set<Long> encounterIds = new LinkedHashSet<>();
        for (NurseExecutionDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getServiceRequestId() != null) {
                serviceRequestIds.add(row.getServiceRequestId());
            }
            if (row.getPractitionerId() != null) {
                practitionerIds.add(row.getPractitionerId());
            }
            if (row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        Map<Long, ServiceRequestListModel> serviceRequestById =
            this.serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        Set<Long> activityIds = new LinkedHashSet<>();
        for (ServiceRequestListModel serviceRequest : serviceRequestById.values()) {
            if (serviceRequest != null && serviceRequest.getActivityId() != null) {
                activityIds.add(serviceRequest.getActivityId());
            }
        }
        Map<Long, ActivityDefinition> activityDefinitionById = new HashMap<>();
        if (!activityIds.isEmpty()) {
            List<ActivityDefinition> activityDefinitions = this.activityDefinitionQueryService.findAllByIds(activityIds);
            for (ActivityDefinition activityDefinition : activityDefinitions) {
                if (activityDefinition != null && activityDefinition.getId() != null) {
                    activityDefinitionById.put(activityDefinition.getId(), activityDefinition);
                }
            }
        }
        NurseWorkloadAssembler.NurseWorkloadExecutionDetailEnrichmentContext context =
            new NurseWorkloadAssembler.NurseWorkloadExecutionDetailEnrichmentContext();
        context.setCategoryLabelMap(this.terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE));
        context.setServiceRequestById(serviceRequestById);
        context.setActivityDefinitionById(activityDefinitionById);
        context.setPractitionerNameById(this.loadPractitionerNameById(practitionerIds));
        context.setPatientNameById(this.patientQueryService.findPatientNamesByIds(patientIds));
        context.setEncounterBusNoById(this.encounterQueryService.mapEncounterBusNoByIds(encounterIds));
        return context;
    }

    /**
     * 构建耗材使用明细 enrich 上下文。
     *
     * @param models 当前页领域明细（含 deviceReqId）
     * @return enrich 上下文
     */
    private NurseWorkloadAssembler.NurseWorkloadConsumableDetailEnrichmentContext buildConsumableDetailEnrichmentContext(
        List<NurseConsumableWorkloadDetailQueryModel> models) {
        Set<Long> deviceReqIds = new LinkedHashSet<>();
        Set<Long> deviceDefIds = new LinkedHashSet<>();
        Set<Long> practitionerIds = new LinkedHashSet<>();
        Set<Long> patientIds = new LinkedHashSet<>();
        Set<Long> encounterIds = new LinkedHashSet<>();
        Map<Long, Long> deviceReqIdByDispenseId = new HashMap<>();
        if (models != null) {
            for (NurseConsumableWorkloadDetailQueryModel model : models) {
                if (model == null) {
                    continue;
                }
                if (model.getDeviceReqId() != null) {
                    deviceReqIds.add(model.getDeviceReqId());
                }
                if (model.getDispenseId() != null && model.getDeviceReqId() != null) {
                    deviceReqIdByDispenseId.put(model.getDispenseId(), model.getDeviceReqId());
                }
                if (model.getDeviceDefId() != null) {
                    deviceDefIds.add(model.getDeviceDefId());
                }
                if (model.getPractitionerId() != null) {
                    practitionerIds.add(model.getPractitionerId());
                }
                if (model.getPatientId() != null) {
                    patientIds.add(model.getPatientId());
                }
                if (model.getEncounterId() != null) {
                    encounterIds.add(model.getEncounterId());
                }
            }
        }
        Map<Long, DeviceDefinition> deviceDefinitionById = new HashMap<>();
        if (!deviceDefIds.isEmpty()) {
            List<DeviceDefinition> definitions = this.deviceDefinitionQueryService.getAllDeviceDefinitions(deviceDefIds);
            for (DeviceDefinition definition : definitions) {
                if (definition != null && definition.getId() != null) {
                    deviceDefinitionById.put(definition.getId(), definition);
                }
            }
        }
        NurseWorkloadAssembler.NurseWorkloadConsumableDetailEnrichmentContext context =
            new NurseWorkloadAssembler.NurseWorkloadConsumableDetailEnrichmentContext();
        context.setDeviceReqIdByDispenseId(deviceReqIdByDispenseId);
        context.setDeviceRequestById(this.deviceRequestQueryService.mapListModelsByIds(deviceReqIds));
        context.setDeviceDefinitionById(deviceDefinitionById);
        context.setPractitionerNameById(this.loadPractitionerNameById(practitionerIds));
        context.setPatientNameById(this.patientQueryService.findPatientNamesByIds(patientIds));
        context.setEncounterBusNoById(this.encounterQueryService.mapEncounterBusNoByIds(encounterIds));
        context.setUnitLabelMap(this.terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.UNIT_CODE));
        return context;
    }

    /**
     * 批量加载执业者 id → 姓名。
     *
     * @param practitionerIds 执业者主键集合
     * @return 姓名 map
     */
    private Map<Long, String> loadPractitionerNameById(Set<Long> practitionerIds) {
        Map<Long, String> practitionerNameById = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return practitionerNameById;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null && practitioner.getName() != null) {
                practitionerNameById.put(practitioner.getId(), practitioner.getName().trim());
            }
        }
        return practitionerNameById;
    }

    /**
     * 构建门诊/住院单选下拉。
     *
     * @return 就诊范围选项
     */
    private List<OptionDto> buildEncounterScopeOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto outpatient = new OptionDto();
        outpatient.setValue(NurseWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT);
        outpatient.setLabel(NurseWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT_LABEL);
        options.add(outpatient);
        OptionDto inpatient = new OptionDto();
        inpatient.setValue(NurseWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT);
        inpatient.setLabel(NurseWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT_LABEL);
        options.add(inpatient);
        return options;
    }

    /**
     * 构建工作科室下拉：启用的护士站与医技科室。
     *
     * @param encounterScope 门诊或住院 code（保留入参；工作科室不随就诊域收窄）
     * @return OptionDto 列表；无科室时返回空列表（非 null）
     */
    private List<OptionDto> buildOrganizationOptions(String encounterScope) {
        // Pre：护士站 + 医技，禁止用挂号科/入院科冒充工作科室
        List<Organization> organizations = this.organizationQueryService.listOrganizationsForWorkDepartment();
        List<OptionDto> options = new ArrayList<>();
        for (Organization organization : organizations) {
            // 无主键的科室无法作为筛选值，跳过
            if (organization == null || organization.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(organization.getId()));
            option.setLabel(organization.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * 构建护士下拉 OptionDto 列表。
     *
     * @param nurses 护士实体列表
     * @return 下拉选项
     */
    private List<OptionDto> buildNurseOptions(List<Practitioner> nurses) {
        List<OptionDto> options = new ArrayList<>();
        if (nurses == null) {
            return options;
        }
        for (Practitioner nurse : nurses) {
            if (nurse == null || nurse.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(nurse.getId()));
            if (nurse.getName() != null) {
                option.setLabel(nurse.getName().trim());
            } else {
                option.setLabel(String.valueOf(nurse.getId()));
            }
            options.add(option);
        }
        return options;
    }

    /**
     * 生成导出文件名（护士工作量_{主Tab}_{timestamp}.xlsx）。
     *
     * @param mainTabCode 主 Tab 码
     * @return 文件名
     */
    private String buildExportFileName(String mainTabCode) {
        String tabLabel = NurseWorkloadExportConstants.MAIN_TAB_CONSUMABLE_LABEL;
        if (NurseWorkloadConstants.MAIN_TAB_EXECUTION.equals(mainTabCode)) {
            tabLabel = NurseWorkloadExportConstants.MAIN_TAB_EXECUTION_LABEL;
        }
        String timestamp = LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER);
        return NurseWorkloadConstants.EXPORT_FILE_NAME_PREFIX + "_" + tabLabel + "_" + timestamp + ".xlsx";
    }

    /**
     * Gate：读取当前租户 id。
     *
     * @return 租户 id
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_101} 租户上下文缺失
     */
    private Long requireTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException(
                ErrorCodeConstants.RPT_NWL_101,
                "页面配置加载失败，请稍后重试");
        }
        return tenantId;
    }

    /**
     * 卡片耗材行回填品名/规格/hvcm 展示（仅 DeviceDefinition 批量，禁止患者就诊 enrich）。
     *
     * @param rows 耗材明细 DTO
     */
    private void fillConsumableCardDisplayFields(List<NurseConsumableDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> deviceDefIds = new LinkedHashSet<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row != null && row.getDeviceDefId() != null) {
                deviceDefIds.add(row.getDeviceDefId());
            }
        }
        Map<Long, DeviceDefinition> definitionById = new HashMap<>();
        if (!deviceDefIds.isEmpty()) {
            List<DeviceDefinition> definitions =
                this.deviceDefinitionQueryService.getAllDeviceDefinitions(deviceDefIds);
            for (DeviceDefinition definition : definitions) {
                if (definition != null && definition.getId() != null) {
                    definitionById.put(definition.getId(), definition);
                }
            }
        }
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            row.setHvcmDisplay(this.resolveCardHvcmDisplay(row.getHvcmFlag()));
            if (row.getDeviceDefId() == null) {
                continue;
            }
            DeviceDefinition definition = definitionById.get(row.getDeviceDefId());
            if (definition == null) {
                continue;
            }
            if (definition.getName() != null) {
                row.setDeviceName(definition.getName().trim());
            }
            if (definition.getTotalVolume() != null) {
                row.setSpecText(definition.getTotalVolume().trim());
            }
            if (row.getHvcmFlag() == null) {
                row.setHvcmFlag(definition.getHvcmFlag());
                row.setHvcmDisplay(this.resolveCardHvcmDisplay(definition.getHvcmFlag()));
            }
        }
    }

    /**
     * 高/低值展示文案（与 Assembler 同常量）。
     *
     * @param hvcmFlag 高值标志
     * @return 展示名
     */
    private String resolveCardHvcmDisplay(Boolean hvcmFlag) {
        if (hvcmFlag == null) {
            return NurseWorkloadConstants.HVCM_UNKNOWN_DISPLAY;
        }
        if (Boolean.TRUE.equals(hvcmFlag)) {
            return NurseWorkloadConstants.HVCM_HIGH_DISPLAY;
        }
        return NurseWorkloadConstants.HVCM_LOW_DISPLAY;
    }

    /**
     * 诊疗明细按护士分桶。
     *
     * @param rows 诊疗明细
     * @return nurseId → 行
     */
    private Map<Long, List<NurseExecutionDetailRowDto>> bucketExecutionRowsByNurse(
        List<NurseExecutionDetailRowDto> rows) {
        Map<Long, List<NurseExecutionDetailRowDto>> byNurse = new LinkedHashMap<>();
        if (rows == null) {
            return byNurse;
        }
        for (NurseExecutionDetailRowDto row : rows) {
            if (row == null || row.getPractitionerId() == null) {
                continue;
            }
            List<NurseExecutionDetailRowDto> bucket = byNurse.get(row.getPractitionerId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                byNurse.put(row.getPractitionerId(), bucket);
            }
            bucket.add(row);
        }
        return byNurse;
    }

    /**
     * 耗材明细按护士分桶。
     *
     * @param rows 耗材明细
     * @return nurseId → 行
     */
    private Map<Long, List<NurseConsumableDetailRowDto>> bucketConsumableRowsByNurse(
        List<NurseConsumableDetailRowDto> rows) {
        Map<Long, List<NurseConsumableDetailRowDto>> byNurse = new LinkedHashMap<>();
        if (rows == null) {
            return byNurse;
        }
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null || row.getPractitionerId() == null) {
                continue;
            }
            List<NurseConsumableDetailRowDto> bucket = byNurse.get(row.getPractitionerId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                byNurse.put(row.getPractitionerId(), bucket);
            }
            bucket.add(row);
        }
        return byNurse;
    }

    /**
     * 收集诊疗行售价列表。
     *
     * @param rows 诊疗行
     * @return 售价列表
     */
    private List<BigDecimal> collectSaleAmounts(List<NurseExecutionDetailRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseExecutionDetailRowDto row : rows) {
            if (row != null && row.getSaleAmount() != null) {
                amounts.add(row.getSaleAmount());
            }
        }
        return amounts;
    }

    /**
     * 收集诊疗行优惠列表。
     *
     * @param rows 诊疗行
     * @return 优惠列表
     */
    private List<BigDecimal> collectDiscountAmounts(List<NurseExecutionDetailRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseExecutionDetailRowDto row : rows) {
            if (row != null && row.getDiscountAmount() != null) {
                amounts.add(row.getDiscountAmount());
            }
        }
        return amounts;
    }

    /**
     * 收集诊疗行实收列表。
     *
     * @param rows 诊疗行
     * @return 实收列表
     */
    private List<BigDecimal> collectActualReceivedAmounts(List<NurseExecutionDetailRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseExecutionDetailRowDto row : rows) {
            if (row != null && row.getActualReceivedAmount() != null) {
                amounts.add(row.getActualReceivedAmount());
            }
        }
        return amounts;
    }

    /**
     * 收集耗材行售价列表。
     *
     * @param rows 耗材行
     * @return 售价列表
     */
    private List<BigDecimal> collectSaleAmountsFromConsumable(List<NurseConsumableDetailRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row != null && row.getSaleAmount() != null) {
                amounts.add(row.getSaleAmount());
            }
        }
        return amounts;
    }

    /**
     * 收集耗材行优惠列表。
     *
     * @param rows 耗材行
     * @return 优惠列表
     */
    private List<BigDecimal> collectDiscountAmountsFromConsumable(List<NurseConsumableDetailRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row != null && row.getDiscountAmount() != null) {
                amounts.add(row.getDiscountAmount());
            }
        }
        return amounts;
    }

    /**
     * 收集耗材行实收列表。
     *
     * @param rows 耗材行
     * @return 实收列表
     */
    private List<BigDecimal> collectActualReceivedAmountsFromConsumable(List<NurseConsumableDetailRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row != null && row.getActualReceivedAmount() != null) {
                amounts.add(row.getActualReceivedAmount());
            }
        }
        return amounts;
    }

    /**
     * 收集耗材行进价列表（跳过 null）。
     *
     * @param rows 耗材行
     * @return 进价列表
     */
    private List<BigDecimal> collectPurchaseAmountsFromConsumable(List<NurseConsumableDetailRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row != null && row.getPurchaseAmount() != null) {
                amounts.add(row.getPurchaseAmount());
            }
        }
        return amounts;
    }

    /**
     * 汇总可空明细金额；空列表返回 null。
     *
     * @param amounts 明细金额
     * @return 合计；无有效值时为 null
     */
    private BigDecimal sumNullableDetailAmounts(List<BigDecimal> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return null;
        }
        return this.nurseWorkloadAmountEnricher.sumDetailAmounts(amounts);
    }

    /**
     * 按工作科室（岗位 {@code adm_pr_assign.org_id}）收窄人员集合写入 {@code practitionerIds}。
     *
     * <p>已选单人时以 {@code practitionerId} 为准，不再叠加科室集合。
     * {@code organizationId} 为空则不收窄。该科没有护士/医技岗位时写入空列表（禁止回落全院）。
     * 科室统计路径清空 {@code requestingOrgId}，金额随执行人归属汇总。</p>
     *
     * @param query          领域读查询（就地写入）
     * @param organizationId 工作科室主键；可空
     */
    private void applyWorkDepartmentScope(NurseWorkloadPageQuery query, Long organizationId) {
        if (query == null) {
            return;
        }
        // 单人明细优先：不叠加科室 IN
        if (query.getPractitionerId() != null) {
            return;
        }
        if (organizationId == null) {
            return;
        }
        // Pre：护士岗与医技岗上挂了该工作科室的人员
        query.setPractitionerIds(this.resolvePractitionerIdsByWorkDepartment(organizationId));
        // 科室统计不再按开立科室二次过滤金额
        query.setRequestingOrgId(null);
    }

    /**
     * 解析工作科室下的护士与医技人员主键。
     *
     * <p>口径是岗位 {@code adm_pr_assign.org_id}，不是 {@code Practitioner.org_id}。</p>
     *
     * @param workDepartmentId 工作科室主键
     * @return 人员主键；无命中时为空列表（非 null）
     */
    private List<Long> resolvePractitionerIdsByWorkDepartment(Long workDepartmentId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.addAll(this.practitionerRoleScopeLoader.resolvePractitionerIdsForActiveRoleFilter(
            PlatformRoleCodeConstants.ROLE_NURSE, workDepartmentId));
        ids.addAll(this.practitionerRoleScopeLoader.resolvePractitionerIdsForActiveRoleFilter(
            PlatformRoleCodeConstants.ROLE_MEDICAL_TECH, workDepartmentId));
        return new ArrayList<>(ids);
    }

    /**
     * 加载工作科室下拉用的在职人员：未选科室为全部护士与医技，已选则只留挂了该科室的人。
     *
     * @param workDepartmentId 工作科室；空表示不按科室收窄
     * @return 去重后的人员列表
     */
    private List<Practitioner> listPractitionersByWorkDepartment(Long workDepartmentId) {
        List<Practitioner> merged = new ArrayList<>();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        this.appendRolePractitioners(merged, seen, PlatformRoleCodeConstants.ROLE_NURSE, workDepartmentId);
        this.appendRolePractitioners(merged, seen, PlatformRoleCodeConstants.ROLE_MEDICAL_TECH, workDepartmentId);
        return merged;
    }

    /**
     * 把某一平台角色的在职人员并入列表，已出现的主键跳过。
     *
     * @param merged           累积列表
     * @param seen             已写入的人员主键
     * @param roleCode         平台角色 code
     * @param workDepartmentId 工作科室；空则该角色全员
     */
    private void appendRolePractitioners(
            List<Practitioner> merged,
            Set<Long> seen,
            String roleCode,
            Long workDepartmentId) {
        List<Practitioner> rows = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRole(
            roleCode, workDepartmentId);
        if (rows == null) {
            return;
        }
        for (Practitioner row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // 双角色人员只出现一次
            if (seen.add(row.getId())) {
                merged.add(row);
            }
        }
    }

    /**
     * 耗材数量代数和。
     *
     * @param rows 耗材行
     * @return 数量合计
     */
    private BigDecimal sumConsumableQuantities(List<NurseConsumableDetailRowDto> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (NurseConsumableDetailRowDto row : rows) {
            if (row != null && row.getQuantity() != null) {
                total = total.add(row.getQuantity());
            }
        }
        return total;
    }

    /**
     * 由诊疗明细聚合成类目汇总行。
     *
     * @param rows 单护士诊疗明细
     * @return 类目汇总
     */
    private List<NurseExecutionSummaryRowDto> buildExecutionSummaryRowsFromDetails(
        List<NurseExecutionDetailRowDto> rows) {
        Map<String, NurseExecutionSummaryRowDto> bucket = new LinkedHashMap<>();
        for (NurseExecutionDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            String categoryKey = "";
            if (row.getCategoryEnum() != null) {
                categoryKey = row.getCategoryEnum();
            }
            NurseExecutionSummaryRowDto summary = bucket.get(categoryKey);
            if (summary == null) {
                summary = new NurseExecutionSummaryRowDto();
                summary.setCategoryEnum(row.getCategoryEnum());
                summary.setCategoryDisplay(row.getCategoryDisplay());
                summary.setExecutionCount(0L);
                bucket.put(categoryKey, summary);
            }
            summary.setExecutionCount(summary.getExecutionCount() + 1L);
        }
        return new ArrayList<>(bucket.values());
    }

    /**
     * 由耗材明细聚合成高/低值汇总行。
     *
     * @param rows 单护士耗材明细
     * @return hvcm 汇总
     */
    private List<NurseConsumableSummaryRowDto> buildConsumableSummaryRowsFromDetails(
        List<NurseConsumableDetailRowDto> rows) {
        Map<String, NurseConsumableSummaryRowDto> bucket = new LinkedHashMap<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            String bucketKey = "HVCM_NULL";
            if (Boolean.TRUE.equals(row.getHvcmFlag())) {
                bucketKey = "HVCM_TRUE";
            } else if (Boolean.FALSE.equals(row.getHvcmFlag())) {
                bucketKey = "HVCM_FALSE";
            }
            NurseConsumableSummaryRowDto summary = bucket.get(bucketKey);
            if (summary == null) {
                summary = new NurseConsumableSummaryRowDto();
                summary.setHvcmFlag(row.getHvcmFlag());
                summary.setHvcmDisplay(row.getHvcmDisplay());
                summary.setQuantityTotal(BigDecimal.ZERO);
                bucket.put(bucketKey, summary);
            }
            if (row.getQuantity() != null) {
                summary.setQuantityTotal(summary.getQuantityTotal().add(row.getQuantity()));
            }
        }
        return new ArrayList<>(bucket.values());
    }

    /**
     * 由耗材明细聚合成品名 chip。
     *
     * @param rows 单护士耗材明细
     * @return chip 列表
     */
    private List<NurseConsumableItemChipDto> buildConsumableItemChips(List<NurseConsumableDetailRowDto> rows) {
        Map<String, NurseConsumableItemChipDto> bucket = new LinkedHashMap<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            String name = "";
            if (row.getDeviceName() != null) {
                name = row.getDeviceName().trim();
            }
            String spec = "";
            if (row.getSpecText() != null) {
                spec = row.getSpecText().trim();
            }
            String label = name;
            if (!name.isEmpty() && !spec.isEmpty()) {
                label = name + "(" + spec + ")";
            } else if (name.isEmpty() && !spec.isEmpty()) {
                label = spec;
            } else if (name.isEmpty()) {
                label = "未知耗材";
            }
            String key = String.valueOf(row.getDeviceDefId()) + "::" + label;
            NurseConsumableItemChipDto chip = bucket.get(key);
            if (chip == null) {
                chip = new NurseConsumableItemChipDto();
                chip.setKey(key);
                chip.setLabel(label);
                chip.setQuantity(BigDecimal.ZERO);
                bucket.put(key, chip);
            }
            if (row.getQuantity() != null) {
                chip.setQuantity(chip.getQuantity().add(row.getQuantity()));
            }
        }
        return new ArrayList<>(bucket.values());
    }
}
