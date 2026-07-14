/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.service;

import com.openhis.health.application.report.practitionerworkload.assembler.PractitionerWorkloadAssembler;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadDetailLineDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadExportRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadFinTypeRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadRegistrationRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadSummaryTotalsDto;
import com.openhis.health.application.report.practitionerworkload.mapper.PractitionerWorkloadMapper;
import com.openhis.health.application.report.practitionerworkload.request.ExportPractitionerWorkloadDetailRequest;
import com.openhis.health.application.report.practitionerworkload.request.LoadPractitionerWorkloadPageInitRequest;
import com.openhis.health.application.report.practitionerworkload.request.PagePractitionerWorkloadDetailRequest;
import com.openhis.health.application.report.practitionerworkload.request.QueryPractitionerWorkloadSummaryRequest;
import com.openhis.health.application.report.practitionerworkload.response.ExportPractitionerWorkloadDetailResponse;
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
import com.openhis.health.domain.administration.encounter.queries.model.PractitionerWorkloadEncounterContextQueryModel;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
            // Pre：scope 预解析（科室/就诊/payment）
            this.prepareQueryScopes(query);
            // Pre：与领域汇总同源 chargeItem scope，避免 App 重复过滤口径漂移
            List<PractitionerWorkloadChargeRowQueryModel> chargeRows =
                this.chargeItemQueryService.listPractitionerWorkloadScopeChargeRows(query, false);
            Set<Long> scopedChargeItemIds = this.extractChargeItemIdsFromRows(chargeRows);
            if (scopedChargeItemIds.isEmpty()) {
                String organizationName = this.resolveOrganizationName(request.getOrganizationId());
                PractitionerWorkloadSummaryTotalsDto emptyTotals =
                    this.practitionerWorkloadAssembler.assembleSummaryTotals(List.of(), List.of());
                this.practitionerWorkloadAssembler.applyOverviewSummary(
                    emptyTotals,
                    new PractitionerWorkloadOverviewSummaryQueryModel());
                return this.practitionerWorkloadMapper.toQueryPractitionerWorkloadSummaryResponse(
                    request.getEncounterScope(),
                    request.getOrganizationId(),
                    organizationName,
                    List.of(),
                    List.of(),
                    emptyTotals);
            }
            Set<Long> definitionIds = this.extractDefinitionIdsFromRows(chargeRows);
            // Pre：definition → fin_type_code（BR-RPT-WKL-007）
            Map<Long, String> finTypeCodeByDefinition =
                this.practitionerWorkloadFinTypeEnricher.resolveFinTypeCodeByDefinitionId(
                    query, definitionIds, scopedChargeItemIds);
            Map<String, String> finTypeDisplayByCode =
                this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            Map<Long, BigDecimal> costByChargeItemId =
                this.practitionerWorkloadCostEnricher.computeCostByChargeItemScope(
                    query, scopedChargeItemIds);
            Map<String, BigDecimal> costByOrgDefinitionKey =
                this.buildCostByOrgDefinitionKey(chargeRows, costByChargeItemId);
            // Pre：fin_type org 粒度汇总
            List<PractitionerWorkloadFinTypeOrgSummaryQueryModel> finTypeOrgSummaries =
                this.chargeItemQueryService.summarizePractitionerWorkloadByFinType(query);
            // Pre：挂号号源 org 粒度汇总
            List<PractitionerWorkloadRegistrationOrgSummaryQueryModel> registrationOrgSummaries =
                this.chargeItemQueryService.summarizeRegistrationChargesByServiceType(query);
            List<PractitionerWorkloadFinTypeRowDto> finTypeRows =
                this.practitionerWorkloadAssembler.mergeFinTypeRowsAcrossOrgs(
                    finTypeOrgSummaries,
                    finTypeCodeByDefinition,
                    costByOrgDefinitionKey,
                    finTypeDisplayByCode);
            // Asm：实收分摊回填（分母=payment 快照内全部 chargeItem |total_price|）
            Map<Long, BigDecimal> paymentSnapshotAbsTotalById = this.buildPaymentSnapshotAbsTotalById(query);
            this.practitionerWorkloadAssembler.allocateActualReceivedAmount(
                query, chargeRows, finTypeRows, finTypeCodeByDefinition, paymentSnapshotAbsTotalById);
            Set<Long> serviceTypeIds = new LinkedHashSet<>();
            for (PractitionerWorkloadRegistrationOrgSummaryQueryModel orgSummary : registrationOrgSummaries) {
                if (orgSummary != null && orgSummary.getServiceTypeId() != null) {
                    serviceTypeIds.add(orgSummary.getServiceTypeId());
                }
            }
            Map<Long, HealthcareService> serviceTypeById =
                this.healthcareServiceQueryService.loadHealthcareServiceMapByIds(serviceTypeIds);
            // Asm：跨 org 合并号源行
            List<PractitionerWorkloadRegistrationRowDto> registrationRows =
                this.practitionerWorkloadAssembler.mergeRegistrationRowsAcrossOrgs(
                    registrationOrgSummaries, serviceTypeById);
            PractitionerWorkloadSummaryTotalsDto summaryTotals =
                this.practitionerWorkloadAssembler.assembleSummaryTotals(finTypeRows, registrationRows);
            PractitionerWorkloadOverviewSummaryQueryModel overviewSummary =
                this.chargeItemQueryService.summarizePractitionerWorkloadOverview(chargeRows);
            this.practitionerWorkloadAssembler.applyOverviewSummary(summaryTotals, overviewSummary);
            String organizationName = this.resolveOrganizationName(request.getOrganizationId());
            // Asm：映射汇总响应
            return this.practitionerWorkloadMapper.toQueryPractitionerWorkloadSummaryResponse(
                request.getEncounterScope(),
                request.getOrganizationId(),
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
     * IF-RPT-WKL-03：工作量明细分页。
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
            // Pre：一次加载 scope 内 charge 行，复用于分页与 enrich（避免重复 loadChargeItemsByIds）
            List<PractitionerWorkloadChargeRowQueryModel> adviceChargeRows =
                this.buildWorkloadChargeRows(query, false, true);
            Page<PractitionerWorkloadRequestDetailQueryModel> domainPage =
                this.chargeItemQueryService.pagePractitionerWorkloadDetailFromChargeRows(adviceChargeRows, query);
            Map<String, PractitionerWorkloadChargeRowQueryModel> chargeRowByLinkKey =
                this.mapChargeRowsByLinkKey(adviceChargeRows);
            // Asm：分页明细不计算成本（UI 不展示；导出 IF-04 单独 enrich）
            List<PractitionerWorkloadDetailLineDto> lineDtos =
                this.assembleDetailLines(domainPage.getContent(), chargeRowByLinkKey, Collections.emptyMap());
            int pageNo = 1;
            if (request.getPageNo() != null && request.getPageNo() >= 1) {
                pageNo = request.getPageNo();
            }
            int pageSize = PractitionerWorkloadConstants.DEFAULT_PAGE_SIZE;
            if (request.getPageSize() != null && request.getPageSize() >= 1) {
                pageSize = request.getPageSize();
            }
            // Asm：映射分页响应
            return this.practitionerWorkloadMapper.toPagePractitionerWorkloadDetailResponse(
                lineDtos,
                domainPage.getTotalElements(),
                pageNo,
                pageSize,
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
            // Asm：AnnotatedExcelExporter 生成 xlsx
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                PractitionerWorkloadConstants.EXPORT_SHEET_NAME,
                exportRows,
                PractitionerWorkloadExportRowDto.class);
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
            BigDecimal costAmount = this.resolveSignedLineCost(chargeRow, costByChargeItemId);
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
                costAmount,
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
     * 单条链路的带符号行成本。
     *
     * @param chargeRow          链路 charge 行
     * @param costByChargeItemId chargeItem → 成本
     * @return 带退费符号的成本；无则 ZERO
     */
    private BigDecimal resolveSignedLineCost(
        PractitionerWorkloadChargeRowQueryModel chargeRow,
        Map<Long, BigDecimal> costByChargeItemId) {
        if (chargeRow == null || chargeRow.getChargeItem() == null || chargeRow.getChargeItem().getId() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal lineCost = BigDecimal.ZERO;
        if (costByChargeItemId != null) {
            BigDecimal bucket = costByChargeItemId.get(chargeRow.getChargeItem().getId());
            if (bucket != null) {
                lineCost = bucket;
            }
        }
        return this.applyPractitionerWorkloadPaymentSign(lineCost, chargeRow.getPaymentContext());
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
     * charge 行按 chargeItemId:paymentId 索引（明细每行一条收/退链路）。
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
            if (row == null || row.getChargeItem() == null || row.getPaymentContext() == null) {
                continue;
            }
            Long chargeItemId = row.getChargeItem().getId();
            Long paymentId = row.getPaymentContext().getPaymentId();
            String key = this.buildChargePaymentLinkKey(chargeItemId, paymentId);
            if (key != null) {
                mapped.put(key, row);
            }
        }
        return mapped;
    }

    /**
     * payment–chargeItem 链路分组键。
     *
     * @param chargeItemId chargeItem 主键
     * @param paymentId    payment 主键
     * @return 链路键；任一为空则 null
     */
    private String buildChargePaymentLinkKey(Long chargeItemId, Long paymentId) {
        if (chargeItemId == null || paymentId == null) {
            return null;
        }
        return chargeItemId + ":" + paymentId;
    }

    /**
     * 按 orgId:definitionId 聚合 chargeItem 成本（汇总页 fin_type 用）。
     *
     * @param chargeRows           候选行
     * @param costByChargeItemId   chargeItem → 成本
     * @return orgDefinitionKey → 成本合计
     */
    private Map<String, BigDecimal> buildCostByOrgDefinitionKey(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows,
        Map<Long, BigDecimal> costByChargeItemId) {
        Map<String, BigDecimal> costByKey = new LinkedHashMap<>();
        if (chargeRows == null || costByChargeItemId == null) {
            return costByKey;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (chargeItem.getRequestingOrgId() == null || chargeItem.getDefinitionId() == null) {
                continue;
            }
            String key = chargeItem.getRequestingOrgId() + ":" + chargeItem.getDefinitionId();
            BigDecimal lineCost = costByChargeItemId.get(chargeItem.getId());
            if (lineCost == null) {
                lineCost = BigDecimal.ZERO;
            }
            // 收退各一行时按 payment 符号代数累加，避免成本只加不减（BR-RPT-WKL-011）
            lineCost = this.applyPractitionerWorkloadPaymentSign(lineCost, row.getPaymentContext());
            BigDecimal existing = costByKey.get(key);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            costByKey.put(key, existing.add(lineCost));
        }
        return costByKey;
    }

    /**
     * 按 payment 快照汇总 |total_price| 作为实收分摊分母（AS-OP-FIN-005 §1.3.1）。
     *
     * @param query 含 resolved payment-charge 链接
     * @return paymentId → |total_price| 累加
     */
    private Map<Long, BigDecimal> buildPaymentSnapshotAbsTotalById(PractitionerWorkloadPageQuery query) {
        Map<Long, BigDecimal> absByPaymentId = new LinkedHashMap<>();
        if (query == null) {
            return absByPaymentId;
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return absByPaymentId;
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link != null && link.getChargeItemId() != null) {
                chargeItemIds.add(link.getChargeItemId());
            }
        }
        if (chargeItemIds.isEmpty()) {
            return absByPaymentId;
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
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            Long paymentId = link.getPaymentContext().getPaymentId();
            if (paymentId == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            BigDecimal lineAbs = this.nullToZero(chargeItem.getTotalPrice()).abs();
            BigDecimal existing = absByPaymentId.get(paymentId);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            absByPaymentId.put(paymentId, existing.add(lineAbs));
        }
        return absByPaymentId;
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
     * 医生 scope：未选医生则全过；挂号看 registrationDoctorEncounterScope；其它看 entererId。
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
        return query.getPractitionerId().equals(chargeItem.getEntererId());
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
