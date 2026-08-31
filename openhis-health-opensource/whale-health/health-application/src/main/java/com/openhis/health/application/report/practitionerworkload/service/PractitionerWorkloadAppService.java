/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.service;

import com.openhis.health.application.report.practitionerworkload.assembler.PractitionerWorkloadAssembler;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadDetailLineDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadDoctorCardDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadExportRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadFinTypeRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadRegistrationRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadSummaryTotalsDto;
import com.openhis.health.application.report.practitionerworkload.mapper.PractitionerWorkloadMapper;
import com.openhis.health.application.report.practitionerworkload.request.ExportPractitionerWorkloadDetailRequest;
import com.openhis.health.application.report.practitionerworkload.request.ListPractitionerWorkloadDoctorCardsRequest;
import com.openhis.health.application.report.practitionerworkload.request.LoadPractitionerWorkloadPageInitRequest;
import com.openhis.health.application.report.practitionerworkload.request.PagePractitionerWorkloadDetailRequest;
import com.openhis.health.application.report.practitionerworkload.request.QueryPractitionerWorkloadSummaryRequest;
import com.openhis.health.application.report.practitionerworkload.response.ExportPractitionerWorkloadDetailResponse;
import com.openhis.health.application.report.practitionerworkload.response.ListPractitionerWorkloadDoctorCardsResponse;
import com.openhis.health.application.report.practitionerworkload.response.LoadPractitionerWorkloadPageInitResponse;
import com.openhis.health.application.report.practitionerworkload.response.PagePractitionerWorkloadDetailResponse;
import com.openhis.health.application.report.practitionerworkload.response.QueryPractitionerWorkloadSummaryResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadFinTypeOrgSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadOverviewSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadRegistrationOrgSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadRequestDetailQueryModel;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.OutpatientRegistrationReportPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientRegistrationReportSummaryRow;
import com.openhis.health.domain.administration.encounter.queries.model.PractitionerWorkloadEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientRegistrationReportConstants;
import com.openhis.health.domain.share.constants.PractitionerWorkloadConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.report.practitionerworkload.support.PractitionerWorkloadCostEnricher;
import com.openhis.health.application.report.practitionerworkload.support.PractitionerWorkloadFinTypeEnricher;
import com.openhis.health.application.report.practitionerworkload.support.PractitionerWorkloadLoader;
import com.openhis.health.application.report.practitionerworkload.support.PractitionerWorkloadPrescriptionEnricher;
import com.openhis.health.application.shared.report.ChargeItemOrderingDoctorFromRequestSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 科室/医生工作量统计应用服务（AS-RPT-001，IF-RPT-WKL-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、日期窗、encounterScope、分页与导出上限。</li>
 *   <li>Pre：{@link PractitionerWorkloadLoader} scope 预解析与领域 QueryService 读侧。</li>
 *   <li>Asm：{@link PractitionerWorkloadAssembler} + Mapper 装配响应/导出。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/practitionerWorkload/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PractitionerWorkloadAppService {

    /** 读路径登录门禁。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 科室主数据读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /** 医生主数据读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 号源类型读侧。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /** 就诊读侧（挂号医生批量分桶）。 */
    private final EncounterQueryService encounterQueryService;

    /** 收费明细事实读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 医生岗位 scope 预解析。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** fin_type 字典读侧。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 产品定义批量读侧。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** scope 预解析。 */
    private final PractitionerWorkloadLoader practitionerWorkloadLoader;

    /** fin_type 历史定价 enrich。 */
    private final PractitionerWorkloadFinTypeEnricher practitionerWorkloadFinTypeEnricher;

    /** 行成本 enrich。 */
    private final PractitionerWorkloadCostEnricher practitionerWorkloadCostEnricher;

    /** 药品收费行缺处方号时回落药品请求处方号。 */
    private final PractitionerWorkloadPrescriptionEnricher practitionerWorkloadPrescriptionEnricher;

    /** 开单医生/开立时间自 Request 回填。 */
    private final ChargeItemOrderingDoctorFromRequestSupport chargeItemOrderingDoctorFromRequestSupport;

    /** 金额列与跨 org 合并。 */
    private final PractitionerWorkloadAssembler practitionerWorkloadAssembler;

    /** Request/Response 映射。 */
    private final PractitionerWorkloadMapper practitionerWorkloadMapper;

    /**
     * IF-RPT-WKL-01：工作量统计页初始化。
     *
     * @param request 可选 encounterScope / organizationId
     * @return 门诊住院选项、科室/医生下拉与默认日期
     * @throws BusinessException 未登录或主数据失败
     */
    public LoadPractitionerWorkloadPageInitResponse loadPractitionerWorkloadPageInit(
        LoadPractitionerWorkloadPageInitRequest request) {
        // Gate：须已登录方可加载租户内主数据
        this.currentOperatorResolver.requireOperatorId(
            PractitionerWorkloadConstants.EX_RPT_WKL_101,
            "页面配置加载失败，请稍后重试");
        try {
            String encounterScope = PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT;
            if (request != null && StringUtils.isNotEmpty(request.getEncounterScope())) {
                encounterScope = request.getEncounterScope().trim();
            }
            // Gate：encounterScope 须为门诊/住院枚举
            this.validateEncounterScope(encounterScope);
            Long organizationId = null;
            if (request != null) {
                organizationId = request.getOrganizationId();
            }
            LocalDate today = LocalDate.now();
            String todayText = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Pre：按 encounterScope 加载科室下拉
            List<OptionDto> organizationOptions = this.buildOrganizationOptions(encounterScope);
            // Pre：医生下拉随科室级联收窄
            List<OptionDto> practitionerOptions = this.buildPractitionerOptions(organizationId);
            List<OptionDto> encounterScopeOptions = this.buildEncounterScopeOptions();
            // Asm：默认日期=当日，默认 Tab=汇总
            return this.practitionerWorkloadMapper.toLoadPractitionerWorkloadPageInitResponse(
                encounterScopeOptions,
                organizationOptions,
                practitionerOptions,
                todayText,
                todayText);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_102,
                "请选择门诊或住院及有效的查询日期");
        } catch (RuntimeException runtimeException) {
            log.error("科室医生工作量页初始化失败", runtimeException);
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_101,
                "页面配置加载失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-WKL-02：工作量汇总查询。
     *
     * @param request 筛选条件
     * @return fin_type 表、号源表与 summaryTotals
     * @throws BusinessException 参数非法或数据不完整
     */
    public QueryPractitionerWorkloadSummaryResponse queryPractitionerWorkloadSummary(
        QueryPractitionerWorkloadSummaryRequest request) {
        // Gate：登录
        this.currentOperatorResolver.requireOperatorId(
            PractitionerWorkloadConstants.EX_RPT_WKL_101,
            "汇总查询失败，请稍后重试");
        // Gate：encounterScope 与日期窗
        this.validateEncounterScope(request.getEncounterScope());
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        try {
            // Asm：Request → PractitionerWorkloadPageQuery
            PractitionerWorkloadPageQuery query =
                this.practitionerWorkloadMapper.toPractitionerWorkloadPageQuery(request);
            // Pre：汇总按账单 occurrence 窗解析（禁止扫 Payment）
            this.prepareSummarizeQueryScopes(query);
            // Pre：挂号净人次直读 Encounter（对齐 AS-RPT-007；BR-RPT-WKL-009），不依赖收费行
            List<PractitionerWorkloadRegistrationRowDto> registrationRows =
                this.buildRegistrationRowsFromEncounters(query);
            // Pre：账单 chargeItem 一次加载，费用过滤复用（禁止 IF-02 多次 loadChargeItemsByIds）
            Map<Long, ChargeItem> chargeItemById =
                this.chargeItemQueryService.loadPractitionerWorkloadChargeItemMap(query);
            List<PractitionerWorkloadChargeRowQueryModel> chargeRows =
                this.chargeItemQueryService.listPractitionerWorkloadScopeChargeRows(
                    query, false, chargeItemById);
            Set<Long> scopedChargeItemIds = this.extractChargeItemIdsFromRows(chargeRows);
            // 无费用行时仍返回挂号净人次，避免与挂号明细对不上被抹零
            if (scopedChargeItemIds.isEmpty()) {
                String organizationName = this.resolveOrganizationName(
                    this.resolveSelectedOrgId(request.getRequestingOrgId(), request.getOrganizationId()));
                PractitionerWorkloadSummaryTotalsDto emptyTotals =
                    this.practitionerWorkloadAssembler.assembleSummaryTotals(List.of(), registrationRows);
                this.practitionerWorkloadAssembler.applyOverviewSummary(
                    emptyTotals,
                    new PractitionerWorkloadOverviewSummaryQueryModel());
                // Asm：门诊人数与挂号明细「有效」对齐
                this.applyOutpatientRegistrationPeopleCount(
                    emptyTotals,
                    emptyTotals.getRegistrationNetCountTotal(),
                    PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(request.getEncounterScope()));
                return this.practitionerWorkloadMapper.toQueryPractitionerWorkloadSummaryResponse(
                    request.getEncounterScope(),
                    this.resolveSelectedOrgId(request.getRequestingOrgId(), request.getOrganizationId()),
                    organizationName,
                    List.of(),
                    registrationRows,
                    emptyTotals);
            }
            Set<Long> definitionIds = this.extractDefinitionIdsFromRows(chargeRows);
            // Pre：definition → fin_type_code（BR-RPT-WKL-007）
            Map<Long, String> finTypeCodeByDefinition =
                this.practitionerWorkloadFinTypeEnricher.resolveFinTypeCodeByDefinitionId(
                    query, definitionIds, scopedChargeItemIds);
            Map<String, String> finTypeDisplayByCode =
                this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            Map<Long, BigDecimal> purchaseByChargeItemId =
                this.practitionerWorkloadCostEnricher.computeCostByChargeItemScope(
                    query, scopedChargeItemIds);
            Map<String, BigDecimal> purchaseByOrgDefinitionKey =
                this.buildPurchaseByOrgDefinitionKey(chargeRows, purchaseByChargeItemId);
            // Pre：fin_type org 粒度汇总（复用已过滤费用行；售价=账单加和）
            List<PractitionerWorkloadFinTypeOrgSummaryQueryModel> finTypeOrgSummaries =
                this.chargeItemQueryService.summarizePractitionerWorkloadByFinType(chargeRows);
            List<PractitionerWorkloadFinTypeRowDto> finTypeRows =
                this.practitionerWorkloadAssembler.mergeFinTypeRowsAcrossOrgs(
                    finTypeOrgSummaries,
                    finTypeCodeByDefinition,
                    purchaseByOrgDefinitionKey,
                    finTypeDisplayByCode);
            // Asm：页脚进售价 = fin_type 行加和（与明细同口径，禁止扫 Payment 另算）
            PractitionerWorkloadSummaryTotalsDto summaryTotals =
                this.practitionerWorkloadAssembler.assembleSummaryTotals(finTypeRows, registrationRows);
            // Pre：收费行无处方号时回落药品请求 prescription_no（同方多味计 1；BR-RPT-WKL-019）
            this.practitionerWorkloadPrescriptionEnricher.enrichMissingPrescriptionNoFromMedicationRequest(chargeRows);
            PractitionerWorkloadOverviewSummaryQueryModel overviewSummary =
                this.chargeItemQueryService.summarizePractitionerWorkloadOverview(chargeRows);
            this.practitionerWorkloadAssembler.applyOverviewSummary(summaryTotals, overviewSummary);
            // Asm：门诊人数与挂号明细「有效」对齐（覆盖收费去重接诊人数）
            this.applyOutpatientRegistrationPeopleCount(
                summaryTotals,
                summaryTotals.getRegistrationNetCountTotal(),
                PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(request.getEncounterScope()));
            String organizationName = this.resolveOrganizationName(
                this.resolveSelectedOrgId(request.getRequestingOrgId(), request.getOrganizationId()));
            // Asm：映射汇总响应
            return this.practitionerWorkloadMapper.toQueryPractitionerWorkloadSummaryResponse(
                request.getEncounterScope(),
                this.resolveSelectedOrgId(request.getRequestingOrgId(), request.getOrganizationId()),
                organizationName,
                finTypeRows,
                registrationRows,
                summaryTotals);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (IllegalArgumentException illegalArgumentException) {
            throw this.mapScopeIllegalArgument(illegalArgumentException);
        } catch (RuntimeException runtimeException) {
            log.error("科室医生工作量汇总查询失败", runtimeException);
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_104,
                "汇总查询失败，请稍后重试");
        }
    }

    /**
     * 医生卡片墙批量查询：账单/chargeItem 一次加载后按医生分桶（卡片仅 summaryTotals）。
     *
     * <p>fin_type / 号源明细仍由弹窗懒加载 IF-02，避免卡片墙 N 次整单汇总。
     * 卡片医生取「入参下拉 ∪ 开单分桶 ∪ 接诊挂号」并集，且跳过全零卡，避免顶栏全科合计与卡片人数脱节。</p>
     *
     * @param request 筛选 + 医生 id 列表（可空；仍会按数据补医生）
     * @return 有工作量数据的卡片行
     * @throws BusinessException 参数非法或数据不完整
     */
    public ListPractitionerWorkloadDoctorCardsResponse listPractitionerWorkloadDoctorCards(
        ListPractitionerWorkloadDoctorCardsRequest request) {
        // Gate：登录
        this.currentOperatorResolver.requireOperatorId(
            PractitionerWorkloadConstants.EX_RPT_WKL_101,
            "汇总查询失败，请稍后重试");
        // Gate：encounterScope 与日期窗
        this.validateEncounterScope(request.getEncounterScope());
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        try {
            // Asm：卡片请求 → 与 IF-02 同构 PageQuery（不含单医生收窄）
            PractitionerWorkloadPageQuery query = new PractitionerWorkloadPageQuery();
            query.setEncounterScope(request.getEncounterScope());
            query.setOrganizationId(request.getOrganizationId());
            query.setRequestingOrgId(request.getRequestingOrgId());
            query.setBillDateFrom(LocalDate.parse(
                request.getDateFrom().trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
            query.setBillDateTo(LocalDate.parse(
                request.getDateTo().trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
            // Pre：卡片墙汇总同账单路径（禁止扫 Payment）
            this.prepareSummarizeQueryScopes(query);
            Map<Long, ChargeItem> chargeItemById =
                this.chargeItemQueryService.loadPractitionerWorkloadChargeItemMap(query);
            List<PractitionerWorkloadChargeRowQueryModel> allChargeRows =
                this.chargeItemQueryService.listPractitionerWorkloadScopeChargeRows(
                    query, false, chargeItemById);
            // Pre：缺处方号回落（BR-RPT-WKL-019），再按人分桶
            this.practitionerWorkloadPrescriptionEnricher.enrichMissingPrescriptionNoFromMedicationRequest(
                allChargeRows);
            Set<Long> definitionIds = this.extractDefinitionIdsFromRows(allChargeRows);
            Set<Long> scopedChargeItemIds = this.extractChargeItemIdsFromRows(allChargeRows);
            Map<Long, String> finTypeCodeByDefinition =
                this.practitionerWorkloadFinTypeEnricher.resolveFinTypeCodeByDefinitionId(
                    query, definitionIds, scopedChargeItemIds);
            Map<String, String> finTypeDisplayByCode =
                this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            // Pre：卡片不展示进价，进价 map 置空（弹窗 IF-02 再算）
            Map<String, BigDecimal> emptyPurchaseByOrgDefinition = Collections.emptyMap();
            // Pre：挂号医生一次批量解析，禁止按医生 N 次 filter REGISTRATION_DOCTOR
            LinkedHashSet<Long> encounterIdsForDoctorMap = new LinkedHashSet<>();
            if (query.getEncounterContextByEncounterId() != null) {
                encounterIdsForDoctorMap.addAll(query.getEncounterContextByEncounterId().keySet());
            }
            Map<Long, Long> registrationDoctorByEncounterId =
                this.encounterQueryService.mapActiveRegistrationDoctorByEncounterIds(encounterIdsForDoctorMap);
            // Pre：医嘱开单医生自 Request.requester_id（ChargeItem.enterer_* 已为收费人）
            List<ChargeItem> chargeItemsForOrderingDoctor = new ArrayList<>();
            for (PractitionerWorkloadChargeRowQueryModel row : allChargeRows) {
                if (row != null && row.getChargeItem() != null) {
                    chargeItemsForOrderingDoctor.add(row.getChargeItem());
                }
            }
            Map<Long, Long> orderingDoctorByChargeItemId =
                this.chargeItemOrderingDoctorFromRequestSupport.mapOrderingDoctorIdByChargeItemId(
                    chargeItemsForOrderingDoctor);
            Map<Long, List<PractitionerWorkloadChargeRowQueryModel>> rowsByDoctorId =
                this.bucketChargeRowsByDoctor(
                    allChargeRows, registrationDoctorByEncounterId, orderingDoctorByChargeItemId);
            // Pre：门诊挂号有效人次按接诊医生一次汇总（与 AS-RPT-007 同口径，禁止按卡 N 次扫）
            boolean outpatientCards = PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(
                request.getEncounterScope());
            Map<Long, Integer> registrationNetByAdmitterDoctorId = Collections.emptyMap();
            if (outpatientCards) {
                registrationNetByAdmitterDoctorId =
                    this.mapOutpatientRegistrationNetCountByAdmitterDoctor(query);
            }
            // Pre：卡片医生 = 入参下拉 ∪ 开单分桶 ∪ 接诊挂号（避免顶栏全科合计、卡片只剩科室岗 1 人）
            LinkedHashSet<Long> doctorIdsForCards = new LinkedHashSet<>();
            if (request.getPractitionerIds() != null) {
                for (Long practitionerId : request.getPractitionerIds()) {
                    if (practitionerId != null) {
                        doctorIdsForCards.add(practitionerId);
                    }
                }
            }
            doctorIdsForCards.addAll(rowsByDoctorId.keySet());
            doctorIdsForCards.addAll(registrationNetByAdmitterDoctorId.keySet());
            Map<Long, String> practitionerNameById = this.loadPractitionerNameMap(doctorIdsForCards);
            List<PractitionerWorkloadDoctorCardDto> cards = new ArrayList<>();
            for (Long practitionerId : doctorIdsForCards) {
                if (practitionerId == null) {
                    continue;
                }
                List<PractitionerWorkloadChargeRowQueryModel> doctorRows = rowsByDoctorId.get(practitionerId);
                if (doctorRows == null) {
                    doctorRows = List.of();
                }
                List<PractitionerWorkloadFinTypeOrgSummaryQueryModel> finTypeOrgSummaries =
                    this.chargeItemQueryService.summarizePractitionerWorkloadByFinType(doctorRows);
                List<PractitionerWorkloadFinTypeRowDto> finTypeRows =
                    this.practitionerWorkloadAssembler.mergeFinTypeRowsAcrossOrgs(
                        finTypeOrgSummaries,
                        finTypeCodeByDefinition,
                        emptyPurchaseByOrgDefinition,
                        finTypeDisplayByCode);
                // Asm：卡片开单金额/处方/诊疗仍来自收费行；人数改挂号有效
                PractitionerWorkloadSummaryTotalsDto summaryTotals =
                    this.practitionerWorkloadAssembler.assembleSummaryTotals(finTypeRows, List.of());
                PractitionerWorkloadOverviewSummaryQueryModel overviewSummary =
                    this.chargeItemQueryService.summarizePractitionerWorkloadOverview(doctorRows);
                this.practitionerWorkloadAssembler.applyOverviewSummary(summaryTotals, overviewSummary);
                Integer registrationNet = registrationNetByAdmitterDoctorId.get(practitionerId);
                this.applyOutpatientRegistrationPeopleCount(summaryTotals, registrationNet, outpatientCards);
                // Pre：无费用且无挂号的空卡不回传，避免医生人数被下拉撑大
                if (!this.hasDoctorCardWorkloadData(summaryTotals)) {
                    continue;
                }
                PractitionerWorkloadDoctorCardDto card = new PractitionerWorkloadDoctorCardDto();
                card.setPractitionerId(practitionerId);
                card.setPractitionerName(practitionerNameById.get(practitionerId));
                card.setSummaryTotals(summaryTotals);
                cards.add(card);
            }
            ListPractitionerWorkloadDoctorCardsResponse response = new ListPractitionerWorkloadDoctorCardsResponse();
            response.setCards(cards);
            return response;
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (IllegalArgumentException illegalArgumentException) {
            throw this.mapScopeIllegalArgument(illegalArgumentException);
        } catch (RuntimeException runtimeException) {
            log.error("科室医生工作量卡片批量查询失败", runtimeException);
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_104,
                "汇总查询失败，请稍后重试");
        }
    }

    /**
     * 将费用/挂号 charge 行按医生分桶：医嘱看 Request.requester_id；挂号看 REGISTRATION_DOCTOR（BR-RPT-WKL-005）。
     *
     * @param chargeRows                      已过滤 scope 行
     * @param registrationDoctorByEncounterId 就诊 → 挂号医生
     * @param orderingDoctorByChargeItemId    收费行 → 开单医生
     * @return doctorId → 行列表
     */
    private Map<Long, List<PractitionerWorkloadChargeRowQueryModel>> bucketChargeRowsByDoctor(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows,
        Map<Long, Long> registrationDoctorByEncounterId,
        Map<Long, Long> orderingDoctorByChargeItemId) {
        Map<Long, List<PractitionerWorkloadChargeRowQueryModel>> rowsByDoctorId = new LinkedHashMap<>();
        if (chargeRows == null) {
            return rowsByDoctorId;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            Long doctorId = null;
            if (this.isRegistrationChargeItem(chargeItem)) {
                if (chargeItem.getEncounterId() != null && registrationDoctorByEncounterId != null) {
                    doctorId = registrationDoctorByEncounterId.get(chargeItem.getEncounterId());
                }
            } else if (orderingDoctorByChargeItemId != null && chargeItem.getId() != null) {
                doctorId = orderingDoctorByChargeItemId.get(chargeItem.getId());
            }
            if (doctorId == null) {
                continue;
            }
            List<PractitionerWorkloadChargeRowQueryModel> bucket = rowsByDoctorId.get(doctorId);
            if (bucket == null) {
                bucket = new ArrayList<>();
                rowsByDoctorId.put(doctorId, bucket);
            }
            bucket.add(row);
        }
        return rowsByDoctorId;
    }

    /**
     * IF-RPT-WKL-03：工作量明细分页。
     *
     * <p>金标准：轻量展开 → 仅回填开立时间并排序 → 切片当前页 → 仅本页开单医生/展示名/进价 enrich。
     * 禁止为第 1 页对全部明细做完整展示 enrich 后再 subList（SKILL §2.1 #9）。</p>
     *
     * @param request 筛选与分页
     * @return 明细分页
     * @throws BusinessException 参数非法或数据不完整
     */
    public PagePractitionerWorkloadDetailResponse pagePractitionerWorkloadDetail(
        PagePractitionerWorkloadDetailRequest request) {
        // Gate：登录
        this.currentOperatorResolver.requireOperatorId(
            PractitionerWorkloadConstants.EX_RPT_WKL_101,
            "明细查询失败，请稍后重试");
        // Gate：encounterScope、日期与分页
        this.validateEncounterScope(request.getEncounterScope());
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        this.validatePageWindow(request.getPageNo(), request.getPageSize());
        try {
            // Asm：Request → PractitionerWorkloadPageQuery
            PractitionerWorkloadPageQuery query =
                this.practitionerWorkloadMapper.toPractitionerWorkloadPageQuery(request);
            // Pre：scope 预解析（同 IF-02）
            this.prepareQueryScopes(query);
            // Pre：一次加载 scope 内 charge 行（主键预解析 OK；允许全窗 charge）
            List<PractitionerWorkloadChargeRowQueryModel> adviceChargeRows =
                this.buildWorkloadChargeRows(query, false, true);
            // Pre：批量解析开单锚点（排序键；禁止先展开全量明细再 sort）
            List<ChargeItem> adviceChargeItems = this.extractChargeItemsFromRows(adviceChargeRows);
            Map<Long, ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor> orderingAnchors =
                this.chargeItemOrderingDoctorFromRequestSupport.mapOrderingDoctorAnchorByChargeItemId(
                    adviceChargeItems);
            List<PractitionerWorkloadChargeRowQueryModel> sortableChargeRows =
                this.chargeItemQueryService.filterPractitionerWorkloadAdviceChargeRows(adviceChargeRows);
            sortableChargeRows.sort(Comparator.comparing(
                row -> this.resolveWorkloadChargeRowOpenedTime(row, orderingAnchors),
                Comparator.nullsLast(Comparator.reverseOrder())));
            // Asm：先切片 charge 行，再仅展开当前页明细（SKILL §2.1 #9）
            Page<PractitionerWorkloadChargeRowQueryModel> chargeRowPage =
                this.chargeItemQueryService.slicePractitionerWorkloadChargeRows(sortableChargeRows, query);
            List<PractitionerWorkloadRequestDetailQueryModel> pageContent =
                this.chargeItemQueryService.buildPractitionerWorkloadDetailRowsFromChargeRows(
                    chargeRowPage.getContent());
            this.chargeItemOrderingDoctorFromRequestSupport.applyOpenedTimeToWorkloadDetails(
                pageContent, orderingAnchors);
            // Pre：仅当前页回填开单医生 id（复用已解析 anchor，禁止二次全窗 Request IN）
            this.chargeItemOrderingDoctorFromRequestSupport.applyOrderingDoctorToWorkloadDetails(
                pageContent, orderingAnchors);
            Map<String, PractitionerWorkloadChargeRowQueryModel> chargeRowByLinkKey =
                this.mapChargeRowsByLinkKey(adviceChargeRows);
            Set<Long> pageScopedChargeItemIds = this.extractChargeItemIdsFromDomainRows(pageContent);
            Map<Long, BigDecimal> pagePurchaseByChargeItemId =
                this.practitionerWorkloadCostEnricher.computeCostByChargeItemScope(
                    query, pageScopedChargeItemIds);
            // Asm：仅当前页展示 enrich（医生名等）+ 进售价
            List<PractitionerWorkloadDetailLineDto> lineDtos =
                this.assembleDetailLines(pageContent, chargeRowByLinkKey, pagePurchaseByChargeItemId);
            // Asm：映射分页响应
            return this.practitionerWorkloadMapper.toPagePractitionerWorkloadDetailResponse(
                lineDtos,
                chargeRowPage.getTotalElements(),
                chargeRowPage.getNumber() + 1,
                chargeRowPage.getSize(),
                null);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (IllegalArgumentException illegalArgumentException) {
            throw this.mapDetailIllegalArgument(illegalArgumentException);
        } catch (RuntimeException runtimeException) {
            log.error("科室医生工作量明细分页失败", runtimeException);
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_104,
                "明细查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-WKL-04：导出工作量明细 Excel。
     *
     * @param request 与明细同筛选
     * @return xlsx 字节
     * @throws BusinessException 无数据、超上限或组装失败
     */
    public ExportPractitionerWorkloadDetailResponse exportPractitionerWorkloadDetail(
        ExportPractitionerWorkloadDetailRequest request) {
        // Gate：登录
        this.currentOperatorResolver.requireOperatorId(
            PractitionerWorkloadConstants.EX_RPT_WKL_101,
            "导出失败，请稍后重试");
        // Gate：encounterScope 与日期窗
        this.validateEncounterScope(request.getEncounterScope());
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        try {
            // Asm：Request → PractitionerWorkloadPageQuery
            PractitionerWorkloadPageQuery query =
                this.practitionerWorkloadMapper.toPractitionerWorkloadPageQuery(request);
            // Pre：scope 预解析（同 IF-03）
            this.prepareQueryScopes(query);
            List<PractitionerWorkloadChargeRowQueryModel> adviceChargeRows =
                this.buildWorkloadChargeRows(query, false, true);
            List<PractitionerWorkloadRequestDetailQueryModel> allDetailRows =
                this.chargeItemQueryService.listPractitionerWorkloadDetailFromChargeRows(adviceChargeRows);
            this.enrichAndSortWorkloadDetailRows(allDetailRows, adviceChargeRows);
            // Gate：超 10 万行直接拒绝（EX-RPT-WKL-105；RPT-WKL-SDD-R-005）
            if (allDetailRows.size() > PractitionerWorkloadConstants.EXPORT_MAX_ROWS) {
                throw new BusinessException(
                    PractitionerWorkloadConstants.EX_RPT_WKL_105,
                    "导出失败，请缩小日期范围后重试");
            }
            Map<String, PractitionerWorkloadChargeRowQueryModel> chargeRowByLinkKey =
                this.mapChargeRowsByLinkKey(adviceChargeRows);
            Set<Long> scopedChargeItemIds = this.extractChargeItemIdsFromRows(adviceChargeRows);
            Map<Long, BigDecimal> costByChargeItemId =
                this.practitionerWorkloadCostEnricher.computeCostByChargeItemScope(
                    query, scopedChargeItemIds);
            List<PractitionerWorkloadDetailLineDto> allLines =
                this.assembleDetailLines(allDetailRows, chargeRowByLinkKey, costByChargeItemId);
            // Gate：拒绝空明细导出（RPT-WKL-SDD-R-006）
            if (allLines.isEmpty()) {
                throw new BusinessException(
                    PractitionerWorkloadConstants.EX_RPT_WKL_201,
                    "当前条件下无明细可导出");
            }
            List<PractitionerWorkloadExportRowDto> exportRows = new ArrayList<>(allLines.size());
            for (PractitionerWorkloadDetailLineDto line : allLines) {
                PractitionerWorkloadExportRowDto exportRow = this.practitionerWorkloadMapper.toExportRowDto(line);
                if (exportRow != null) {
                    exportRows.add(exportRow);
                }
            }
            // Asm：AnnotatedExcelExporter 生成 xlsx（前端 exportColumns 裁剪列）
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                PractitionerWorkloadConstants.EXPORT_SHEET_NAME,
                exportRows,
                PractitionerWorkloadExportRowDto.class,
                request.getExportColumns());
            String fileName = "科室医生工作量明细_"
                + GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER.format(java.time.LocalDateTime.now())
                + ".xlsx";
            return this.practitionerWorkloadMapper.toExportPractitionerWorkloadDetailResponse(
                fileName, body, exportRows.size(), false);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (IllegalArgumentException illegalArgumentException) {
            throw this.mapDetailIllegalArgument(illegalArgumentException);
        } catch (RuntimeException runtimeException) {
            log.error("科室医生工作量明细导出失败", runtimeException);
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_105,
                "导出失败，请缩小日期范围后重试");
        }
    }

    /**
     * 预解析查询 scope（科室/就诊/医生等），IllegalArgumentException 映射业务异常。
     *
     * @param query 分页查询（入参出参）
     * @throws BusinessException EX_RPT_WKL_102/106
     */
    private void prepareQueryScopes(PractitionerWorkloadPageQuery query) {
        try {
            this.practitionerWorkloadLoader.preparePractitionerWorkloadQueryScopes(query);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw this.mapScopeIllegalArgument(illegalArgumentException);
        }
    }

    /**
     * 汇总/卡片墙：按账单 occurrence 窗预解析 scope（禁止扫 Payment）。
     *
     * @param query 分页查询（入参出参）
     * @throws BusinessException EX_RPT_WKL_102/106
     */
    private void prepareSummarizeQueryScopes(PractitionerWorkloadPageQuery query) {
        try {
            this.practitionerWorkloadLoader.preparePractitionerWorkloadSummarizeScopesFromChargeBill(query);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw this.mapScopeIllegalArgument(illegalArgumentException);
        }
    }

    /**
     * 挂号号源行：直读 Encounter 净人次后跨 org 合并（对齐 AS-RPT-007；BR-RPT-WKL-009/016）。
     *
     * <p>时间轴为 coalesce(startTime, createdAt) 业务日闭区间；选医生时叠
     * <b>接诊医生 ADMITTER</b>（与门诊挂号明细筛「接诊医生」同口径）。禁止经挂号收费行计次。</p>
     *
     * @param query 已预解析科室/就诊类别与日期窗
     * @return 跨 org 合并后的号源行
     */
    private List<PractitionerWorkloadRegistrationRowDto> buildRegistrationRowsFromEncounters(
        PractitionerWorkloadPageQuery query) {
        List<PractitionerWorkloadRegistrationOrgSummaryQueryModel> registrationOrgSummaries =
            this.summarizeRegistrationOrgFromEncounters(query);
        Set<Long> serviceTypeIds = new LinkedHashSet<>();
        for (PractitionerWorkloadRegistrationOrgSummaryQueryModel orgSummary : registrationOrgSummaries) {
            if (orgSummary != null && orgSummary.getServiceTypeId() != null) {
                serviceTypeIds.add(orgSummary.getServiceTypeId());
            }
        }
        Map<Long, HealthcareService> serviceTypeById =
            this.healthcareServiceQueryService.loadHealthcareServiceMapByIds(serviceTypeIds);
        // Asm：跨 org 合并号源行
        return this.practitionerWorkloadAssembler.mergeRegistrationRowsAcrossOrgs(
            registrationOrgSummaries, serviceTypeById);
    }

    /**
     * 挂号科室 × 号源净人次：Encounter 直统（与门诊挂号明细同日窗/净态/接诊医生口径）。
     *
     * @param query 已预解析科室与日期窗；选医生时须有 practitionerId
     * @return org 粒度号源汇总；无命中为空列表
     */
    private List<PractitionerWorkloadRegistrationOrgSummaryQueryModel> summarizeRegistrationOrgFromEncounters(
        PractitionerWorkloadPageQuery query) {
        if (query == null
            || query.getBillDateFrom() == null
            || query.getBillDateTo() == null
            || StringUtils.isEmpty(query.getResolvedEncounterClassCode())) {
            return List.of();
        }
        List<Long> organizationIds = query.getResolvedOrganizationIds();
        if (organizationIds == null || organizationIds.isEmpty()) {
            return List.of();
        }
        // Pre：与 AS-RPT-007 同业务日闭区间（含当日 23:59:59.999999999）
        LocalDateTime businessTimeFrom = query.getBillDateFrom().atStartOfDay();
        LocalDateTime businessTimeTo = query.getBillDateTo().atTime(LocalTime.MAX);
        Collection<Long> resolvedEncounterIds = null;
        // Pre：选医生时候选须同业务日窗，再滤接诊医生 ADMITTER（与挂号明细一致，禁止 REGISTRATION_DOCTOR）
        if (query.getPractitionerId() != null) {
            List<Long> candidates = this.encounterQueryService.listEncounterIdsForRegistrationBusinessWindow(
                query.getResolvedEncounterClassCode(),
                organizationIds,
                businessTimeFrom,
                businessTimeTo);
            Set<Long> doctorMatched = this.encounterQueryService.filterEncounterIdsByActiveAdmitterDoctor(
                candidates, query.getPractitionerId());
            resolvedEncounterIds = doctorMatched;
        }
        // Pre：Encounter 直统科室 × 号源净人次（B.6.4 经 Encounter QS）
        List<OutpatientRegistrationReportSummaryRow> encounterSummaries =
            this.encounterQueryService.summarizePractitionerWorkloadRegistrationByOrgAndServiceType(
                query.getResolvedEncounterClassCode(),
                organizationIds,
                businessTimeFrom,
                businessTimeTo,
                resolvedEncounterIds);
        // Asm：领域汇总行 → 工作量 org 粒度号源模型
        List<PractitionerWorkloadRegistrationOrgSummaryQueryModel> result = new ArrayList<>();
        if (encounterSummaries == null || encounterSummaries.isEmpty()) {
            return result;
        }
        for (OutpatientRegistrationReportSummaryRow encounterSummary : encounterSummaries) {
            if (encounterSummary == null || encounterSummary.getOrganizationId() == null) {
                continue;
            }
            PractitionerWorkloadRegistrationOrgSummaryQueryModel orgSummary =
                new PractitionerWorkloadRegistrationOrgSummaryQueryModel();
            orgSummary.setOrganizationId(encounterSummary.getOrganizationId());
            orgSummary.setServiceTypeId(encounterSummary.getServiceTypeId());
            long netCount = encounterSummary.getNetRegistrationCount();
            if (netCount > Integer.MAX_VALUE) {
                orgSummary.setNetChargeCount(Integer.MAX_VALUE);
            } else {
                orgSummary.setNetChargeCount((int) netCount);
            }
            result.add(orgSummary);
        }
        return result;
    }

    /**
     * 门诊：按接诊医生（ADMITTER）一次汇总挂号有效人次（复用 AS-RPT-007 医生维汇总，禁止自写第二套口径）。
     *
     * <p>住院域返回空 Map（人次仍走收费去重）。</p>
     *
     * @param query 已预解析门诊类别、科室与日期窗
     * @return admitterDoctorId → 有效挂号人次
     */
    private Map<Long, Integer> mapOutpatientRegistrationNetCountByAdmitterDoctor(
        PractitionerWorkloadPageQuery query) {
        Map<Long, Integer> netByDoctorId = new LinkedHashMap<>();
        if (query == null
            || query.getBillDateFrom() == null
            || query.getBillDateTo() == null
            || !PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(query.getEncounterScope())) {
            return netByDoctorId;
        }
        List<Long> organizationIds = query.getResolvedOrganizationIds();
        if (organizationIds == null || organizationIds.isEmpty()) {
            return netByDoctorId;
        }
        // Pre：与挂号明细汇总同 Query（业务日窗 + 科室 + 医生维）
        OutpatientRegistrationReportPageQuery registrationQuery = new OutpatientRegistrationReportPageQuery();
        registrationQuery.setBusinessTimeFrom(query.getBillDateFrom().atStartOfDay());
        registrationQuery.setBusinessTimeTo(query.getBillDateTo().atTime(LocalTime.MAX));
        if (query.getOrganizationId() != null) {
            registrationQuery.setOrganizationId(query.getOrganizationId());
        } else {
            registrationQuery.setResolvedOrganizationIds(organizationIds);
        }
        registrationQuery.setSummaryDimension(
            OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_DOCTOR);
        // Pre：AS-RPT-007 医生维汇总（ADMITTER × 号源净人次）
        List<OutpatientRegistrationReportSummaryRow> doctorSummaries =
            this.encounterQueryService.summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType(
                registrationQuery);
        if (doctorSummaries == null || doctorSummaries.isEmpty()) {
            return netByDoctorId;
        }
        // Asm：同一医生跨号源加总 → 卡片「挂号人次」
        for (OutpatientRegistrationReportSummaryRow doctorSummary : doctorSummaries) {
            if (doctorSummary == null || doctorSummary.getPractitionerId() == null) {
                continue;
            }
            long netCount = doctorSummary.getNetRegistrationCount();
            int addend;
            if (netCount > Integer.MAX_VALUE) {
                addend = Integer.MAX_VALUE;
            } else {
                addend = (int) netCount;
            }
            Integer existing = netByDoctorId.get(doctorSummary.getPractitionerId());
            if (existing == null) {
                netByDoctorId.put(doctorSummary.getPractitionerId(), addend);
            } else {
                long merged = (long) existing + (long) addend;
                if (merged > Integer.MAX_VALUE) {
                    netByDoctorId.put(doctorSummary.getPractitionerId(), Integer.MAX_VALUE);
                } else {
                    netByDoctorId.put(doctorSummary.getPractitionerId(), (int) merged);
                }
            }
        }
        return netByDoctorId;
    }

    /**
     * 门诊人次外显：用挂号有效人次覆盖收费去重接诊人数，避免与挂号明细两套数。
     *
     * @param summaryTotals             合计 DTO
     * @param registrationNetCountTotal 挂号有效人次
     * @param outpatient                是否门诊域
     */
    private void applyOutpatientRegistrationPeopleCount(
        PractitionerWorkloadSummaryTotalsDto summaryTotals,
        Integer registrationNetCountTotal,
        boolean outpatient) {
        if (summaryTotals == null || !outpatient) {
            return;
        }
        int net = 0;
        if (registrationNetCountTotal != null) {
            net = registrationNetCountTotal;
        }
        summaryTotals.setRegistrationNetCountTotal(net);
        // 卡片/底栏「人数」与挂号明细「有效」同值，禁止再展示收费去重口径
        summaryTotals.setEncounterCountTotal(net);
    }

    /**
     * 卡片是否有可展示工作量（费用或挂号任一非零）；全零卡不回传以免撑大医生人数。
     *
     * @param summaryTotals 卡片合计
     * @return 有数据时 true
     */
    private boolean hasDoctorCardWorkloadData(PractitionerWorkloadSummaryTotalsDto summaryTotals) {
        if (summaryTotals == null) {
            return false;
        }
        if (summaryTotals.getSaleAmountTotal() != null
            && summaryTotals.getSaleAmountTotal().compareTo(BigDecimal.ZERO) != 0) {
            return true;
        }
        if (summaryTotals.getPurchaseAmountTotal() != null
            && summaryTotals.getPurchaseAmountTotal().compareTo(BigDecimal.ZERO) != 0) {
            return true;
        }
        if (summaryTotals.getRegistrationNetCountTotal() != null
            && summaryTotals.getRegistrationNetCountTotal() != 0) {
            return true;
        }
        if (summaryTotals.getEncounterCountTotal() != null
            && summaryTotals.getEncounterCountTotal() != 0) {
            return true;
        }
        if (summaryTotals.getPrescriptionCountTotal() != null
            && summaryTotals.getPrescriptionCountTotal() != 0) {
            return true;
        }
        if (summaryTotals.getActivityItemCountTotal() != null
            && summaryTotals.getActivityItemCountTotal() != 0) {
            return true;
        }
        return false;
    }

    /**
     * 从工作量候选行提取 chargeItem 主键 scope。
     *
     * @param chargeRows 候选行
     * @return chargeItem id 集合
     */
    private Set<Long> extractChargeItemIdsFromRows(List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        if (chargeRows == null) {
            return chargeItemIds;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null || row.getChargeItem().getId() == null) {
                continue;
            }
            chargeItemIds.add(row.getChargeItem().getId());
        }
        return chargeItemIds;
    }

    /**
     * 从领域明细行收集 chargeItem 主键（分页进价 enrich 用）。
     *
     * @param domainRows 领域明细
     * @return 去重主键集
     */
    private Set<Long> extractChargeItemIdsFromDomainRows(
        List<PractitionerWorkloadRequestDetailQueryModel> domainRows) {
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        if (domainRows == null) {
            return chargeItemIds;
        }
        for (PractitionerWorkloadRequestDetailQueryModel row : domainRows) {
            if (row != null && row.getChargeItemId() != null) {
                chargeItemIds.add(row.getChargeItemId());
            }
        }
        return chargeItemIds;
    }

    /**
     * 从工作量候选行提取 definition 主键集合。
     *
     * @param chargeRows 候选行
     * @return definition id 集合
     */
    private Set<Long> extractDefinitionIdsFromRows(List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        if (chargeRows == null) {
            return definitionIds;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null || row.getChargeItem().getDefinitionId() == null) {
                continue;
            }
            definitionIds.add(row.getChargeItem().getDefinitionId());
        }
        return definitionIds;
    }

    /**
     * 已选科室时解析展示名。
     *
     * @param organizationId 科室 id
     * @return 科室名称；缺省 null
     */
    private String resolveOrganizationName(Long organizationId) {
        if (organizationId == null) {
            return null;
        }
        Organization organization = this.organizationQueryService.loadOrganizationByIdForDisplay(organizationId);
        if (organization == null) {
            return null;
        }
        return organization.getName();
    }

    /**
     * 领域明细行 + 单条 payment–chargeItem 链路 → 明细 DTO（金额带正负，对齐门诊费用明细）。
     *
     * @param domainRows           领域链路明细
     * @param chargeRowByLinkKey   chargeItemId:paymentId → charge 行
     * @param costByChargeItemId   chargeItem → 行成本（未带退费符号）
     * @return 明细 DTO
     * @throws BusinessException EX_RPT_WKL_103 产品缺失
     */
    private List<PractitionerWorkloadDetailLineDto> assembleDetailLines(
        List<PractitionerWorkloadRequestDetailQueryModel> domainRows,
        Map<String, PractitionerWorkloadChargeRowQueryModel> chargeRowByLinkKey,
        Map<Long, BigDecimal> costByChargeItemId) {
        if (domainRows == null || domainRows.isEmpty()) {
            return List.of();
        }
        Set<Long> orgIds = new LinkedHashSet<>();
        Set<Long> practitionerIds = new LinkedHashSet<>();
        Set<ProductDefinitionBatchLookup> productLookups = new LinkedHashSet<>();
        for (PractitionerWorkloadRequestDetailQueryModel domainRow : domainRows) {
            if (domainRow == null) {
                continue;
            }
            if (domainRow.getRequestingOrgId() != null) {
                orgIds.add(domainRow.getRequestingOrgId());
            }
            if (domainRow.getEntererId() != null) {
                practitionerIds.add(domainRow.getEntererId());
            }
            PractitionerWorkloadChargeRowQueryModel chargeRow =
                this.resolveChargeRowByLinkKey(chargeRowByLinkKey, domainRow);
            if (chargeRow != null && chargeRow.getChargeItem() != null) {
                ProductDefinitionBatchLookup lookup =
                    ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeRow.getChargeItem());
                if (lookup != null) {
                    productLookups.add(lookup);
                }
            }
        }
        Map<Long, String> orgNameById = this.organizationQueryService.mapNamesByIds(orgIds);
        Map<Long, String> practitionerNameById = this.loadPractitionerNameMap(practitionerIds);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(productLookups));
        List<PractitionerWorkloadDetailLineDto> result = new ArrayList<>(domainRows.size());
        for (PractitionerWorkloadRequestDetailQueryModel domainRow : domainRows) {
            if (domainRow == null) {
                continue;
            }
            String catalogTypeDisplay =
                this.practitionerWorkloadAssembler.resolveCatalogTypeDisplay(domainRow.getServiceTable());
            PractitionerWorkloadChargeRowQueryModel chargeRow =
                this.resolveChargeRowByLinkKey(chargeRowByLinkKey, domainRow);
            // Asm：诊疗无进价则为 null（不编 0）
            BigDecimal purchaseAmount = this.resolveSignedLinePurchase(chargeRow, costByChargeItemId);
            ProductDefinitionDisplayDto product = this.resolveProductForChargeRow(chargeRow, productByKey);
            if (product == null) {
                throw new BusinessException(
                    PractitionerWorkloadConstants.EX_RPT_WKL_103,
                    "统计数据不完整，请联系管理员核对收费项目配置");
            }
            String orgName = null;
            if (domainRow.getRequestingOrgId() != null) {
                orgName = orgNameById.get(domainRow.getRequestingOrgId());
            }
            String practitionerName = null;
            if (domainRow.getEntererId() != null) {
                practitionerName = practitionerNameById.get(domainRow.getEntererId());
            }
            PractitionerWorkloadDetailLineDto lineDto = this.practitionerWorkloadAssembler.assembleDetailLineAmounts(
                domainRow,
                purchaseAmount,
                product,
                catalogTypeDisplay,
                orgName,
                practitionerName);
            if (lineDto != null) {
                result.add(lineDto);
            }
        }
        return result;
    }

    /**
     * 按 chargeItemId:paymentId 解析单条链路行。
     *
     * @param chargeRowByLinkKey 链路键 → charge 行
     * @param domainRow          领域明细行
     * @return 匹配的 charge 行；无则 null
     */
    private PractitionerWorkloadChargeRowQueryModel resolveChargeRowByLinkKey(
        Map<String, PractitionerWorkloadChargeRowQueryModel> chargeRowByLinkKey,
        PractitionerWorkloadRequestDetailQueryModel domainRow) {
        if (chargeRowByLinkKey == null || domainRow == null) {
            return null;
        }
        return chargeRowByLinkKey.get(this.buildChargePaymentLinkKey(domainRow.getChargeItemId(), domainRow.getPaymentId()));
    }

    /**
     * 单条链路的带符号行进价；诊疗无进价返回 null（不编 0）。
     *
     * @param chargeRow              链路 charge 行
     * @param purchaseByChargeItemId chargeItem → 进价
     * @return 带退费符号的进价；无则 null
     */
    private BigDecimal resolveSignedLinePurchase(
        PractitionerWorkloadChargeRowQueryModel chargeRow,
        Map<Long, BigDecimal> purchaseByChargeItemId) {
        if (chargeRow == null || chargeRow.getChargeItem() == null || chargeRow.getChargeItem().getId() == null) {
            return null;
        }
        if (purchaseByChargeItemId == null) {
            return null;
        }
        BigDecimal linePurchase = purchaseByChargeItemId.get(chargeRow.getChargeItem().getId());
        if (linePurchase == null) {
            return null;
        }
        return this.applyPractitionerWorkloadPaymentSign(linePurchase, chargeRow.getPaymentContext());
    }

    /**
     * 单条 charge 行 → 产品读模型。
     *
     * @param chargeRow    链路 charge 行
     * @param productByKey compositeKey → 产品
     * @return 产品读模型；无则 null
     */
    private ProductDefinitionDisplayDto resolveProductForChargeRow(
        PractitionerWorkloadChargeRowQueryModel chargeRow,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (chargeRow == null || chargeRow.getChargeItem() == null || productByKey == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup =
            ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeRow.getChargeItem());
        if (lookup == null) {
            return null;
        }
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(), lookup.getProductDefinitionId());
        if (compositeKey == null) {
            return null;
        }
        return productByKey.get(compositeKey);
    }

    /**
     * charge 行按账单键索引（ChargeItem-only：无 paymentId 时键=chargeItemId）。
     *
     * @param chargeRows 候选 charge 行
     * @return linkKey → 行
     */
    private Map<String, PractitionerWorkloadChargeRowQueryModel> mapChargeRowsByLinkKey(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        Map<String, PractitionerWorkloadChargeRowQueryModel> mapped = new LinkedHashMap<>();
        if (chargeRows == null) {
            return mapped;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            Long chargeItemId = row.getChargeItem().getId();
            Long paymentId = null;
            if (row.getPaymentContext() != null) {
                paymentId = row.getPaymentContext().getPaymentId();
            }
            String key = this.buildChargePaymentLinkKey(chargeItemId, paymentId);
            if (key != null) {
                mapped.put(key, row);
            }
        }
        return mapped;
    }

    /**
     * 账单行分组键：有 paymentId 时 {@code id:paymentId}；账单路径仅 {@code id}。
     *
     * @param chargeItemId chargeItem 主键
     * @param paymentId    payment 主键；账单合成路径可为 null
     * @return 链路键；chargeItemId 空则 null
     */
    private String buildChargePaymentLinkKey(Long chargeItemId, Long paymentId) {
        if (chargeItemId == null) {
            return null;
        }
        if (paymentId == null) {
            return String.valueOf(chargeItemId);
        }
        return chargeItemId + ":" + paymentId;
    }

    /**
     * 按 orgId:definitionId 聚合 chargeItem 进价（汇总页 fin_type 用）。
     *
     * <p>诊疗无进价（map 无键）不加；禁止用 0 冒充。</p>
     *
     * @param chargeRows               候选行
     * @param purchaseByChargeItemId   chargeItem → 进价
     * @return orgDefinitionKey → 进价合计
     */
    private Map<String, BigDecimal> buildPurchaseByOrgDefinitionKey(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows,
        Map<Long, BigDecimal> purchaseByChargeItemId) {
        Map<String, BigDecimal> purchaseByKey = new LinkedHashMap<>();
        if (chargeRows == null || purchaseByChargeItemId == null) {
            return purchaseByKey;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (chargeItem.getRequestingOrgId() == null || chargeItem.getDefinitionId() == null) {
                continue;
            }
            BigDecimal linePurchase = purchaseByChargeItemId.get(chargeItem.getId());
            // 诊疗/挂号无进价：跳过，不累加 0
            if (linePurchase == null) {
                continue;
            }
            String key = chargeItem.getRequestingOrgId() + ":" + chargeItem.getDefinitionId();
            // 退费合成上下文带负号（BR-RPT-WKL-011）
            linePurchase = this.applyPractitionerWorkloadPaymentSign(linePurchase, row.getPaymentContext());
            BigDecimal existing = purchaseByKey.get(key);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            purchaseByKey.put(key, existing.add(linePurchase));
        }
        return purchaseByKey;
    }

    /**
     * 开立科室优先，遗留 organizationId 回落。
     *
     * @param requestingOrgId 开立科室
     * @param organizationId  遗留科室
     * @return 已选科室 id；均空为 null
     */
    private Long resolveSelectedOrgId(Long requestingOrgId, Long organizationId) {
        if (requestingOrgId != null) {
            return requestingOrgId;
        }
        return organizationId;
    }

    /**
     * BigDecimal null 安全转 ZERO。
     *
     * @param value 原值
     * @return 非 null 数值
     */
    private BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    /**
     * payment 链接 + chargeItem 过滤 → 工作量候选行（含 scope/挂号/医嘱模式）。
     *
     * @param query                 分页查询（含 resolved scope）
     * @param registrationOnlyMode  仅挂号 chargeItem
     * @param adviceRequestRowsOnly 明细模式仅三类 request 表
     * @return 候选行
     */
    private List<PractitionerWorkloadChargeRowQueryModel> buildWorkloadChargeRows(
        PractitionerWorkloadPageQuery query,
        boolean registrationOnlyMode,
        boolean adviceRequestRowsOnly) {
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link != null && link.getChargeItemId() != null) {
                chargeItemIds.add(link.getChargeItemId());
            }
        }
        if (chargeItemIds.isEmpty()) {
            return List.of();
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        Map<Long, ChargeItem> chargeItemById = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                chargeItemById.put(chargeItem.getId(), chargeItem);
            }
        }
        List<String> allowedStatuses = List.of(
            ChargeItemStatus.SETTLED.getCode(),
            ChargeItemStatus.REFUNDED.getCode());
        List<PractitionerWorkloadChargeRowQueryModel> rows = new ArrayList<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            if (!allowedStatuses.contains(chargeItem.getStatusEnum())) {
                continue;
            }
            if (!this.matchesEncounterScope(chargeItem, query.getResolvedEncounterScope())) {
                continue;
            }
            boolean registration = this.isRegistrationChargeItem(chargeItem);
            if (registrationOnlyMode && !registration) {
                continue;
            }
            if (registrationOnlyMode) {
                if (!this.matchesRegistrationEncounterOrg(chargeItem, query)) {
                    continue;
                }
            } else if (!this.matchesRequestingOrg(chargeItem, query.getResolvedOrganizationIds())) {
                continue;
            }
            if (!this.matchesPractitionerScope(chargeItem, query)) {
                continue;
            }
            if (adviceRequestRowsOnly && !registration && !this.isAdviceServiceTable(chargeItem.getServiceTable())) {
                // 明细分支仅三类 request 表；汇总 fin_type 含挂号等非医嘱行（BR-RPT-WKL-006）
                continue;
            }
            rows.add(new PractitionerWorkloadChargeRowQueryModel(chargeItem, link.getPaymentContext()));
        }
        return rows;
    }

    /**
     * chargeItem 是否在 resolved encounter scope 内。
     *
     * @param chargeItem             chargeItem
     * @param resolvedEncounterScope 就诊 id 列表
     * @return 是否匹配
     */
    private boolean matchesEncounterScope(ChargeItem chargeItem, List<Long> resolvedEncounterScope) {
        if (resolvedEncounterScope == null || resolvedEncounterScope.isEmpty()) {
            return false;
        }
        if (chargeItem.getEncounterId() == null) {
            return false;
        }
        for (Long encounterId : resolvedEncounterScope) {
            if (encounterId != null && encounterId.equals(chargeItem.getEncounterId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * chargeItem requestingOrg 是否在 resolved 科室列表内。
     *
     * @param chargeItem              chargeItem
     * @param resolvedOrganizationIds 科室 id 列表
     * @return 是否匹配
     */
    private boolean matchesRequestingOrg(ChargeItem chargeItem, List<Long> resolvedOrganizationIds) {
        if (resolvedOrganizationIds == null || chargeItem.getRequestingOrgId() == null) {
            return false;
        }
        for (Long orgId : resolvedOrganizationIds) {
            if (orgId != null && orgId.equals(chargeItem.getRequestingOrgId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 挂号 chargeItem：就诊所属科室须在 resolved 科室内（非 requestingOrg）。
     *
     * @param chargeItem chargeItem
     * @param query      含 encounter 上下文与 resolved 科室
     * @return 是否匹配
     */
    private boolean matchesRegistrationEncounterOrg(ChargeItem chargeItem, PractitionerWorkloadPageQuery query) {
        if (query.getEncounterContextByEncounterId() == null || chargeItem.getEncounterId() == null) {
            return false;
        }
        PractitionerWorkloadEncounterContextQueryModel context =
            query.getEncounterContextByEncounterId().get(chargeItem.getEncounterId());
        if (context == null || context.getOrganizationId() == null) {
            return false;
        }
        if (query.getResolvedOrganizationIds() == null) {
            return false;
        }
        Long encounterOrgId = context.getOrganizationId();
        for (Long orgId : query.getResolvedOrganizationIds()) {
            if (orgId != null && orgId.equals(encounterOrgId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 医生 scope：未选医生则全过；挂号看 registrationDoctorEncounterScope；其它看 OrderingDoctorChargeItemScope。
     *
     * @param chargeItem chargeItem
     * @param query      分页查询
     * @return 是否匹配
     */
    private boolean matchesPractitionerScope(ChargeItem chargeItem, PractitionerWorkloadPageQuery query) {
        if (query.getPractitionerId() == null) {
            return true;
        }
        if (this.isRegistrationChargeItem(chargeItem)) {
            Set<Long> registrationScope = query.getRegistrationDoctorEncounterScope();
            if (registrationScope == null || chargeItem.getEncounterId() == null) {
                return false;
            }
            return registrationScope.contains(chargeItem.getEncounterId());
        }
        Set<Long> orderingScope = query.getOrderingDoctorChargeItemScope();
        if (orderingScope == null || orderingScope.isEmpty() || chargeItem.getId() == null) {
            return false;
        }
        return orderingScope.contains(chargeItem.getId());
    }

    /**
     * 明细行回填开单医生/开立时间并按开立时间倒序（导出全量路径；BR-RPT-WKL-005/012）。
     *
     * <p>明细分页禁止调用本方法：须拆「仅开立时间 → 排序切片 → 仅当前页开单医生」。</p>
     *
     * @param detailRows       明细读模型
     * @param adviceChargeRows 同源收费行
     */
    private void enrichAndSortWorkloadDetailRows(
        List<PractitionerWorkloadRequestDetailQueryModel> detailRows,
        List<PractitionerWorkloadChargeRowQueryModel> adviceChargeRows) {
        if (detailRows == null || detailRows.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeItems = this.extractChargeItemsFromRows(adviceChargeRows);
        this.chargeItemOrderingDoctorFromRequestSupport.enrichWorkloadDetailOrderingDoctorAndOpenedTime(
            detailRows, chargeItems);
        detailRows.sort(Comparator.comparing(
            PractitionerWorkloadRequestDetailQueryModel::getEnteredDate,
            Comparator.nullsLast(Comparator.reverseOrder())));
    }

    /**
     * 从工作量候选行提取 ChargeItem 实体列表（Request 开单锚点批量解析用）。
     *
     * @param chargeRows 候选行
     * @return ChargeItem 列表（保序；跳过空行）
     */
    private List<ChargeItem> extractChargeItemsFromRows(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        List<ChargeItem> chargeItems = new ArrayList<>();
        if (chargeRows == null) {
            return chargeItems;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row != null && row.getChargeItem() != null) {
                chargeItems.add(row.getChargeItem());
            }
        }
        return chargeItems;
    }

    /**
     * 判定 chargeItem 是否为挂号费（generateSource 或 context）。
     *
     * @param chargeItem chargeItem
     * @return 是否挂号 chargeItem
     */
    private boolean isRegistrationChargeItem(ChargeItem chargeItem) {
        if (GenerateSource.REGISTRATION_REGISTER.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            return true;
        }
        return PractitionerWorkloadConstants.CHARGE_ITEM_CONTEXT_REGISTER.equals(chargeItem.getContextEnum());
    }

    /**
     * 解析 charge 行开立时间排序键（Request.req_opened_time；无锚点时 null 排末尾）。
     *
     * @param row                 charge 行
     * @param anchorByChargeItemId chargeItemId → 开单锚点
     * @return 开立时间；无锚点返回 null
     */
    private LocalDateTime resolveWorkloadChargeRowOpenedTime(
        PractitionerWorkloadChargeRowQueryModel row,
        Map<Long, ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor> anchorByChargeItemId) {
        if (row == null || row.getChargeItem() == null || row.getChargeItem().getId() == null) {
            return null;
        }
        if (anchorByChargeItemId == null) {
            return null;
        }
        ChargeItemOrderingDoctorFromRequestSupport.OrderingDoctorAnchor anchor =
            anchorByChargeItemId.get(row.getChargeItem().getId());
        if (anchor == null) {
            return null;
        }
        return anchor.openedTime();
    }

    /**
     * service_table 是否为三类医嘱 request 表之一。
     *
     * @param serviceTable service_table
     * @return 是否医嘱 request 表
     */
    private boolean isAdviceServiceTable(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            return false;
        }
        String normalized = serviceTable.trim();
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
            return true;
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)) {
            return true;
        }
        return TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized);
    }

    /**
     * 退费 payment 链路金额取负（BR-RPT-WKL-011；对齐门诊费用明细 BR-OP-FDT-012）。
     *
     * @param amount          原始金额
     * @param paymentContext  payment 读上下文
     * @return 带退费符号的金额
     */
    private BigDecimal applyPractitionerWorkloadPaymentSign(
        BigDecimal amount,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext) {
        BigDecimal normalized = this.nullToZero(amount);
        if (paymentContext != null && Boolean.TRUE.equals(paymentContext.getRefundPayment())) {
            return normalized.abs().negate();
        }
        return normalized;
    }

    /**
     * 构建门诊/住院 encounterScope 下拉选项。
     *
     * @return OptionDto 列表
     */
    private List<OptionDto> buildEncounterScopeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.buildOption(
            PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT,
            PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT_LABEL));
        options.add(this.buildOption(
            PractitionerWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT,
            PractitionerWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT_LABEL));
        return options;
    }

    /**
     * 按 encounterScope 构建可选科室下拉（门诊挂号科 / 住院入院科）。
     *
     * @param encounterScope 门诊或住院 code
     * @return OptionDto 列表
     */
    private List<OptionDto> buildOrganizationOptions(String encounterScope) {
        List<Organization> organizations;
        if (PractitionerWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT.equals(encounterScope)) {
            organizations = this.organizationQueryService.listOrganizationsForInpatientAdmission();
        } else {
            organizations = this.organizationQueryService.listOrganizationsForOutpatientRegistration();
        }
        List<OptionDto> options = new ArrayList<>();
        for (Organization organization : organizations) {
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
     * 按科室构建可选医生下拉（平台 ROLE_DOCTOR scope）。
     *
     * @param organizationId 科室 id；null 表示不限科室
     * @return OptionDto 列表
     */
    private List<OptionDto> buildPractitionerOptions(Long organizationId) {
        Set<Long> doctorIds = this.practitionerRoleScopeLoader.resolvePractitionerIdsForActivePlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_DOCTOR,
            organizationId);
        Collection<Long> scopeIds = null;
        if (!doctorIds.isEmpty()) {
            scopeIds = doctorIds;
        }
        Page<Practitioner> page = this.practitionerQueryService.pageActivePractitionersForPosition(
            scopeIds,
            null,
            PageRequest.of(0, PractitionerWorkloadConstants.PRACTITIONER_OPTION_PAGE_SIZE));
        List<OptionDto> options = new ArrayList<>();
        for (Practitioner practitioner : page.getContent()) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(practitioner.getId()));
            option.setLabel(practitioner.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * 批量加载医务人员 id → 姓名。
     *
     * @param practitionerIds id 集合
     * @return id → 姓名
     */
    private Map<Long, String> loadPractitionerNameMap(Set<Long> practitionerIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return nameById;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null) {
                nameById.put(practitioner.getId(), practitioner.getName());
            }
        }
        return nameById;
    }

    /**
     * 构建 value/label OptionDto。
     *
     * @param value 选项值
     * @param label 展示文案
     * @return OptionDto
     */
    private OptionDto buildOption(String value, String label) {
        OptionDto option = new OptionDto();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }

    /**
     * 校验 encounterScope 为门诊或住院合法值。
     *
     * @param encounterScope 就诊范围 code
     * @throws BusinessException EX_RPT_WKL_102
     */
    private void validateEncounterScope(String encounterScope) {
        if (StringUtils.isEmpty(encounterScope)) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_102,
                "请选择门诊或住院及有效的查询日期");
        }
        String normalized = encounterScope.trim();
        if (!PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(normalized)
            && !PractitionerWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT.equals(normalized)) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_102,
                "请选择门诊或住院及有效的查询日期");
        }
    }

    /**
     * 校验日期窗口非空且 from ≤ to。
     *
     * @param dateFrom 起始日期 yyyy-MM-dd
     * @param dateTo   结束日期 yyyy-MM-dd
     * @throws BusinessException EX_RPT_WKL_102
     */
    private void validateDateWindow(String dateFrom, String dateTo) {
        if (StringUtils.isEmpty(dateFrom) || StringUtils.isEmpty(dateTo)) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_102,
                "请选择门诊或住院及有效的查询日期");
        }
        LocalDate from = LocalDate.parse(dateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        if (from.isAfter(to)) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_102,
                "请选择门诊或住院及有效的查询日期");
        }
    }

    /**
     * 校验分页参数 pageNo/pageSize 合法。
     *
     * @param pageNo   页码
     * @param pageSize 页大小；null 跳过
     * @throws BusinessException EX_RPT_WKL_102
     */
    private void validatePageWindow(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_102,
                "请选择门诊或住院及有效的查询日期");
        }
        if (pageSize != null && pageSize < 1) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_102,
                "请选择门诊或住院及有效的查询日期");
        }
    }

    /**
     * scope 预解析 IllegalArgumentException → EX_RPT_WKL_106/102。
     *
     * @param illegalArgumentException 原异常
     * @return 业务异常
     */
    private BusinessException mapScopeIllegalArgument(IllegalArgumentException illegalArgumentException) {
        String message = illegalArgumentException.getMessage();
        if (message != null && message.contains("organization class")) {
            return new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_106,
                "所选科室不属于当前门诊/住院范围，请重新选择");
        }
        return new BusinessException(
            PractitionerWorkloadConstants.EX_RPT_WKL_102,
            "请选择门诊或住院及有效的查询日期");
    }

    /**
     * 明细查询 IllegalArgumentException → EX_RPT_WKL_103 或回落 scope 映射。
     *
     * @param illegalArgumentException 原异常
     * @return 业务异常
     */
    private BusinessException mapDetailIllegalArgument(IllegalArgumentException illegalArgumentException) {
        String message = illegalArgumentException.getMessage();
        if (message != null && message.contains("service_table")) {
            return new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_103,
                "统计数据不完整，请联系管理员核对收费项目配置");
        }
        return this.mapScopeIllegalArgument(illegalArgumentException);
    }
}
