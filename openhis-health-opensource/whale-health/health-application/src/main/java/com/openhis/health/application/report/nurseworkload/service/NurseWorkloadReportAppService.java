/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.service;

import com.openhis.health.application.report.nurseworkload.dto.NurseConsumableDetailExportRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseConsumableDetailRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionDetailExportRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionDetailRowDto;
import com.openhis.health.application.report.nurseworkload.mapper.NurseWorkloadReportMapper;
import com.openhis.health.application.report.nurseworkload.request.ExportNurseWorkloadDetailRequest;
import com.openhis.health.application.report.nurseworkload.request.LoadNurseWorkloadFormInitRequest;
import com.openhis.health.application.report.nurseworkload.request.NurseWorkloadQueryRequest;
import com.openhis.health.application.report.nurseworkload.request.PageNurseConsumableWorkloadDetailRequest;
import com.openhis.health.application.report.nurseworkload.request.PageNurseExecutionWorkloadDetailRequest;
import com.openhis.health.application.report.nurseworkload.response.ExportNurseWorkloadDetailResponse;
import com.openhis.health.application.report.nurseworkload.response.LoadNurseWorkloadFormInitResponse;
import com.openhis.health.application.report.nurseworkload.response.PageNurseConsumableWorkloadDetailResponse;
import com.openhis.health.application.report.nurseworkload.response.PageNurseExecutionWorkloadDetailResponse;
import com.openhis.health.application.report.nurseworkload.response.SummarizeNurseConsumableWorkloadResponse;
import com.openhis.health.application.report.nurseworkload.response.SummarizeNurseExecutionWorkloadResponse;
import com.openhis.health.application.report.nurseworkload.support.NurseWorkloadAmountEnricher;
import com.openhis.health.application.report.nurseworkload.support.NurseWorkloadAssembler;
import com.openhis.health.application.report.nurseworkload.support.NurseWorkloadExportLoopResult;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.openhis.health.domain.clinical.procedure.queries.model.NurseExecutionCategorySummaryQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.NurseExecutionWorkloadDetailQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.openhis.health.domain.share.constants.NurseWorkloadConstants;
import com.openhis.health.domain.share.constants.NurseWorkloadExportConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.devicedispense.queries.model.NurseConsumableHvcmSummaryQueryModel;
import com.openhis.health.domain.workflow.devicedispense.queries.model.NurseConsumableWorkloadDetailQueryModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.report.nurseworkload.support.NurseWorkloadLoader;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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

    /** 护士岗人员加载。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** 术语展示名加载。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 汇总/明细 enrich。 */
    private final NurseWorkloadAssembler nurseWorkloadAssembler;

    /** 明细金额 enrich。 */
    private final NurseWorkloadAmountEnricher nurseWorkloadAmountEnricher;

    /** Request/Response 映射。 */
    private final NurseWorkloadReportMapper nurseWorkloadReportMapper;

    /**
     * IF-RPT-NWL-01：页面初始化（默认门诊+当日+护士下拉）。
     *
     * @param request 空壳请求
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
            // Pre：全院激活护士岗人员，orgId=null 表示不按科室收窄（§3.2 序号 3）
            List<Practitioner> nurses = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRole(
                WhalePlatformRoleCodeConstants.ROLE_NURSE,
                null);
            // Asm：默认门诊+当日、就诊范围与护士下拉（§3.2 序号 4）
            LoadNurseWorkloadFormInitResponse response = new LoadNurseWorkloadFormInitResponse();
            LocalDate today = LocalDate.now();
            response.setDefaultEncounterScope(NurseWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT);
            response.setDefaultDateFrom(today);
            response.setDefaultDateTo(today);
            response.setEncounterScopeOptions(this.buildEncounterScopeOptions());
            response.setNurseOptions(this.buildNurseOptions(nurses));
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
        // Pre：就诊 scope / SR·category / 耗材 hvcm 预解析（§3.3 序号 3）
        this.prepareNurseWorkloadQueryScopesSafely(query);
        // Pre：按 category_enum 汇总净 COMPLETED 次数（§3.3 序号 4）
        List<NurseExecutionCategorySummaryQueryModel> summaries = this.executeQuerySafely(
            () -> this.procedureQueryService.summarizeNurseExecutionWorkloadByCategory(query));
        // Pre：activity_category_code 术语展示名（§3.3 序号 5）
        Map<String, String> categoryLabelMap = this.terminologyOptionLoader.buildLabelMap(
            TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE);
        // Asm：汇总行 + totalExecutionCount 页脚合计（§3.3 序号 6～7）
        SummarizeNurseExecutionWorkloadResponse response =
            this.nurseWorkloadAssembler.buildExecutionSummaryResponse(summaries, categoryLabelMap);
        response.setTotalAmount(this.sumExecutionAmountForQuery(query));
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
        // Pre：scope 预解析（§3.4 序号 3）
        this.prepareNurseWorkloadQueryScopesSafely(query);
        // Pre：净 COMPLETED 明细分页，recorded_time DESC（§3.4 序号 4）
        Page<NurseExecutionWorkloadDetailQueryModel> page = this.executeQuerySafely(
            () -> this.procedureQueryService.pageNurseExecutionWorkloadDetail(query));
        List<NurseExecutionDetailRowDto> rows = this.mapExecutionDetailRows(page.getContent());
        // Pre：批量 SR / Activity / 人患就诊 + 类目术语（§3.4 序号 5～11）
        NurseWorkloadAssembler.NurseWorkloadExecutionDetailEnrichmentContext enrichmentContext =
            this.buildExecutionDetailEnrichmentContext(rows);
        // Asm：回填 GenerateSource、项目名等展示字段（§3.4 序号 12）
        this.nurseWorkloadAssembler.enrichExecutionDetailRows(rows, enrichmentContext);
        this.nurseWorkloadAmountEnricher.enrichExecutionDetailAmounts(rows);
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
        // Pre：scope 预解析含 hvcm enrich（§3.5 序号 3）
        this.prepareNurseWorkloadQueryScopesSafely(query);
        // Pre：按 hvcm_flag 汇总发放数量（§3.5 序号 4）
        List<NurseConsumableHvcmSummaryQueryModel> summaries = this.executeQuerySafely(
            () -> this.deviceDispenseQueryService.summarizeNurseConsumableWorkloadByHvcm(query));
        // Asm：汇总行 + totalQuantity 页脚合计（§3.5 序号 5～6）
        SummarizeNurseConsumableWorkloadResponse response =
            this.nurseWorkloadAssembler.buildConsumableSummaryResponse(summaries);
        response.setTotalAmount(this.sumConsumableAmountForQuery(query));
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
        // Pre：scope 预解析（§3.6 序号 3）
        this.prepareNurseWorkloadQueryScopesSafely(query);
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
            enrichmentContext.getDeviceReqIdByDispenseId());
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
            // Pre：导出 Query（无分页键）+ scope 预解析（§3.7 序号 2～3）
            NurseWorkloadPageQuery query = this.nurseWorkloadReportMapper.toNurseWorkloadPageQuery(request);
            this.prepareNurseWorkloadQueryScopesSafely(query);
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
                // Asm：诊疗明细 Excel（§3.7 序号 6）
                fileBytes = this.nurseWorkloadReportMapper.buildExecutionExcelBytes(exportRows);
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
                // Asm：耗材明细 Excel（§3.7 序号 6）
                fileBytes = this.nurseWorkloadReportMapper.buildConsumableExcelBytes(exportRows);
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
     * 汇总诊疗执行明细金额（复用导出循环，避免重复 enrich 规则）。
     *
     * @param query 已预解析读查询
     * @return 金额合计
     */
    private BigDecimal sumExecutionAmountForQuery(NurseWorkloadPageQuery query) {
        NurseWorkloadExportLoopResult<NurseExecutionDetailRowDto> loopResult =
            this.exportExecutionDetailsLoop(query);
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseExecutionDetailRowDto row : loopResult.getRows()) {
            if (row != null && row.getAmount() != null) {
                amounts.add(row.getAmount());
            }
        }
        return this.nurseWorkloadAmountEnricher.sumDetailAmounts(amounts);
    }

    /**
     * 汇总耗材使用明细金额（复用导出循环，避免重复 enrich 规则）。
     *
     * @param query 已预解析读查询
     * @return 金额合计
     */
    private BigDecimal sumConsumableAmountForQuery(NurseWorkloadPageQuery query) {
        NurseWorkloadExportLoopResult<NurseConsumableDetailRowDto> loopResult =
            this.exportConsumableDetailsLoop(query);
        List<BigDecimal> amounts = new ArrayList<>();
        for (NurseConsumableDetailRowDto row : loopResult.getRows()) {
            if (row != null && row.getAmount() != null) {
                amounts.add(row.getAmount());
            }
        }
        return this.nurseWorkloadAmountEnricher.sumDetailAmounts(amounts);
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
     * @param query 领域读查询
     * @throws BusinessException {@link ErrorCodeConstants#RPT_NWL_102}
     */
    private void prepareNurseWorkloadQueryScopesSafely(NurseWorkloadPageQuery query) {
        try {
            this.nurseWorkloadLoader.prepareNurseWorkloadQueryScopes(query);
        } catch (IllegalArgumentException illegalArgumentException) {
            // Supcontracts 层 IAE 映射为 EX-RPT-NWL-102 用户提示
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
            this.nurseWorkloadAmountEnricher.enrichExecutionDetailAmounts(rows);
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
                context.getDeviceReqIdByDispenseId());
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
}
