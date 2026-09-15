/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageDetailRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageExportRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageSummaryRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.mapper.OutpatientConsumableUsageReportMapper;
import com.openhis.health.application.report.consumableusage.outpatient.request.ExportOutpatientConsumableUsageReportRequest;
import com.openhis.health.application.report.consumableusage.outpatient.request.LoadOutpatientConsumableUsageReportPageInitRequest;
import com.openhis.health.application.report.consumableusage.outpatient.request.PageOutpatientConsumableUsageReportDetailRequest;
import com.openhis.health.application.report.consumableusage.outpatient.request.SummarizeOutpatientConsumableUsageReportRequest;
import com.openhis.health.application.report.consumableusage.outpatient.response.ExportOutpatientConsumableUsageReportResponse;
import com.openhis.health.application.report.consumableusage.outpatient.response.LoadOutpatientConsumableUsageReportPageInitResponse;
import com.openhis.health.application.report.consumableusage.outpatient.response.PageOutpatientConsumableUsageReportDetailResponse;
import com.openhis.health.application.report.consumableusage.outpatient.response.SummarizeOutpatientConsumableUsageReportResponse;
import com.openhis.health.application.report.consumableusage.outpatient.support.OutpatientConsumableUsageDetailPageResult;
import com.openhis.health.application.report.consumableusage.outpatient.support.OutpatientConsumableUsageReportQuerySupport;
import com.openhis.health.application.report.consumableusage.outpatient.support.OutpatientConsumableUsageSummaryResult;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportQueryFilter;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.report.ReportDateTimeWindowSupport;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 门诊耗材使用报表只读编排（AS-RPT-005 IF-RPT-OCU-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link CurrentOperatorResolver} / {@link PageWindowGate}：登录与分页门禁</li>
 *   <li>{@link OutpatientConsumableUsageReportQuerySupport}：两路并集预解析、合并、金额 enrich</li>
 *   <li>{@link OrganizationQueryService} / {@link LocationQueryService}：初始化下拉</li>
 *   <li>{@link OutpatientConsumableUsageReportMapper}：Request/Response 映射</li>
 * </ul>
 * <p>接入：AppServiceControllerRegistrar →
 * POST /api/app/outpatientConsumableUsageReport/{methodName}；不另写 RestController。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutpatientConsumableUsageReportAppService {

    /** 登录 Gate。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 分页窗口 Gate。 */
    private final PageWindowGate pageWindowGate;

    /** 两路并集查询编排。 */
    private final OutpatientConsumableUsageReportQuerySupport outpatientConsumableUsageReportQuerySupport;

    /** 科室下拉。 */
    private final OrganizationQueryService organizationQueryService;

    /** 库房下拉。 */
    private final LocationQueryService locationQueryService;

    /** 映射。 */
    private final OutpatientConsumableUsageReportMapper outpatientConsumableUsageReportMapper;

    /** 耗材类型术语下拉。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 执行人（发耗材人）下拉主档。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 费用性质 / medType 上下文装配。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * IF-RPT-OCU-01：页面初始化（默认当日、汇总维/来源/科室/库房下拉）。
     *
     * @param request 空壳请求
     * @return 初始化响应
     * @throws BusinessException {@link ConsumableUsageReportConstants#EX_RPT_OCU_101} 初始化失败
     */
    public LoadOutpatientConsumableUsageReportPageInitResponse loadOutpatientConsumableUsageReportPageInit(
        LoadOutpatientConsumableUsageReportPageInitRequest request) {
        // Gate：须已登录方可加载报表页元数据
        this.currentOperatorResolver.requireOperatorId(
            ConsumableUsageReportConstants.EX_RPT_OCU_101,
            "页面初始化失败，请稍后重试");
        try {
            // Pre：门诊相关科室 + 启用中药房/科室药房并集（报表不按管辖库房收窄）
            List<Organization> organizations =
                this.organizationQueryService.listOrganizationsForOutpatientRegistration();
            List<Location> locations = this.locationQueryService.listAllActivePharmacyPartyLocations();
            LocalDate today = LocalDate.now();
            String defaultFrom = ReportDateTimeWindowSupport.formatDayStart(today);
            String defaultTo = ReportDateTimeWindowSupport.formatDayEnd(today);
            List<OptionDto> feeNatureOptions = this.reportPatientSettlementContextAssembler.loadFeeNatureOptions();
            List<OptionDto> categoryOptions =
                this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE);
            Page<Practitioner> dispenserPage = this.practitionerQueryService.pageActivePractitionersForPosition(
                null,
                null,
                PageRequest.of(0, PharmacyDispenseRecordConstants.DISPENSER_OPTION_PAGE_SIZE),
                false);
            List<OptionDto> practitionerOptions = new ArrayList<>();
            // Pre：执行人下拉跳过无主键脏行
            for (Practitioner practitioner : dispenserPage.getContent()) {
                if (practitioner == null || practitioner.getId() == null) {
                    continue;
                }
                OptionDto option = new OptionDto();
                option.setValue(String.valueOf(practitioner.getId()));
                option.setLabel(practitioner.getName());
                practitionerOptions.add(option);
            }
            // Asm：装配默认日期与筛选项
            return this.outpatientConsumableUsageReportMapper.toLoadPageInitResponse(
                organizations,
                locations,
                this.buildSummaryDimensionOptions(),
                this.buildSourceCodeOptions(),
                feeNatureOptions,
                categoryOptions,
                practitionerOptions,
                this.outpatientConsumableUsageReportMapper.buildRecordTypeOptions(),
                defaultFrom,
                defaultTo);
        } catch (RuntimeException ex) {
            log.warn("门诊耗材使用报表页初始化失败", ex);
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_OCU_101,
                "页面初始化失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OCU-02：按患者或科室净汇总。
     *
     * @param request 筛选条件
     * @return 汇总响应
     * @throws BusinessException {@link ConsumableUsageReportConstants#EX_RPT_OCU_102} 日期非法；
     *         {@link ConsumableUsageReportConstants#EX_RPT_OCU_103} 查询失败
     */
    public SummarizeOutpatientConsumableUsageReportResponse summarizeOutpatientConsumableUsageReport(
        SummarizeOutpatientConsumableUsageReportRequest request) {
        // Gate：登录与日期窗
        this.currentOperatorResolver.requireOperatorId(
            ConsumableUsageReportConstants.EX_RPT_OCU_103,
            "查询失败，请稍后重试");
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        try {
            // Pre：Support 预解析 AMB scope + 两路净汇总合并
            OutpatientConsumableUsageSummaryResult result =
                this.outpatientConsumableUsageReportQuerySupport.summarize(
                    this.buildQueryFilter(request));
            // Asm：Support 结果映射 HTTP 响应（页脚净量净额）
            SummarizeOutpatientConsumableUsageReportResponse response =
                new SummarizeOutpatientConsumableUsageReportResponse();
            response.setRows(result.getRows());
            response.setTotalNetQuantity(result.getTotalNetQuantity());
            response.setPurchaseNetTotalAmount(result.getPurchaseNetTotalAmount());
            response.setSaleNetTotalAmount(result.getSaleNetTotalAmount());
            return response;
        } catch (BusinessException ex) {
            // Gate：业务异常原样抛出，不包装为 103
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊耗材使用报表汇总失败", ex);
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_OCU_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OCU-03：两路并集明细分页。
     *
     * @param request 筛选与分页
     * @return 明细分页
     * @throws BusinessException {@link ConsumableUsageReportConstants#EX_RPT_OCU_102} 日期非法；
     *         {@link ConsumableUsageReportConstants#EX_RPT_OCU_103} 查询失败
     */
    public PageOutpatientConsumableUsageReportDetailResponse pageOutpatientConsumableUsageReportDetail(
        PageOutpatientConsumableUsageReportDetailRequest request) {
        // Gate：登录、分页窗口、日期窗
        this.currentOperatorResolver.requireOperatorId(
            ConsumableUsageReportConstants.EX_RPT_OCU_103,
            "查询失败，请稍后重试");
        this.pageWindowGate.requirePositivePageWindow(
            request,
            ConsumableUsageReportConstants.EX_RPT_OCU_103,
            "分页参数无效");
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        try {
            // Pre：Support 两路并集或单路分页（来源筛收窄）
            OutpatientConsumableUsageDetailPageResult result =
                this.outpatientConsumableUsageReportQuerySupport.pageDetail(
                    this.buildQueryFilter(request),
                    request.getPageNo(),
                    request.getPageSize());
            // Asm：分页元数据与明细行写入响应
            PageOutpatientConsumableUsageReportDetailResponse response =
                new PageOutpatientConsumableUsageReportDetailResponse();
            response.setRecords(result.getRecords());
            response.setTotal(result.getTotal());
            response.setPageNo(result.getPageNo());
            response.setPageSize(result.getPageSize());
            return response;
        } catch (BusinessException ex) {
            // Gate：业务异常原样抛出
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊耗材使用报表明细查询失败", ex);
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_OCU_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OCU-04：导出当前 Tab（汇总或明细）。
     *
     * @param request 导出条件
     * @return xlsx 响应
     * @throws BusinessException {@link ConsumableUsageReportConstants#EX_RPT_OCU_102} 日期非法；
     *         {@link ConsumableUsageReportConstants#EX_RPT_OCU_104} 导出失败
     */
    public ExportOutpatientConsumableUsageReportResponse exportOutpatientConsumableUsageReport(
        ExportOutpatientConsumableUsageReportRequest request) {
        // Gate：登录与日期窗
        this.currentOperatorResolver.requireOperatorId(
            ConsumableUsageReportConstants.EX_RPT_OCU_104,
            "导出失败，请稍后重试");
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        String exportTab = StringUtils.trimToNull(request.getExportTabCode());
        if (exportTab == null) {
            // Gate：必须指定当前 Tab，避免误导导出
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_OCU_104,
                "导出失败，请稍后重试");
        }
        try {
            boolean truncated = false;
            List<OutpatientConsumableUsageExportRowDto> exportRows = new ArrayList<>();
            // Pre：仅当前 Tab 全量（BR-RPT-OCU-006；受导出行上限）
            if (ConsumableUsageReportConstants.EXPORT_TAB_SUMMARY.equals(exportTab)) {
                // Pre：汇总 Tab → 同 IF-02 净汇总后截断导出行
                OutpatientConsumableUsageSummaryResult summary =
                    this.outpatientConsumableUsageReportQuerySupport.summarize(
                        this.buildQueryFilter(request));
                List<OutpatientConsumableUsageSummaryRowDto> rows = summary.getRows();
                if (rows == null) {
                    // Asm：空汇总仍导出列头
                    rows = List.of();
                }
                if (rows.size() > ConsumableUsageReportConstants.EXPORT_MAX_ROWS) {
                    // Gate：触达上限则截断并打 truncated，避免超大 Excel
                    truncated = true;
                    rows = rows.subList(0, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
                }
                for (OutpatientConsumableUsageSummaryRowDto row : rows) {
                    exportRows.add(
                        this.outpatientConsumableUsageReportMapper.toExportRowFromSummary(row));
                }
            } else {
                // Pre：明细 Tab（含非 SUMMARY 的兜底）→ 有界并集列表
                List<OutpatientConsumableUsageDetailRowDto> detailRows =
                    this.outpatientConsumableUsageReportQuerySupport.listDetailForExport(
                        this.buildQueryFilter(request));
                if (detailRows.size() >= ConsumableUsageReportConstants.EXPORT_MAX_ROWS) {
                    // Gate：list 已按上限截取，触顶即视为可能截断
                    truncated = true;
                }
                for (OutpatientConsumableUsageDetailRowDto row : detailRows) {
                    exportRows.add(
                        this.outpatientConsumableUsageReportMapper.toExportRowFromDetail(row));
                }
            }
            // Asm：并集导出行 + 前端 exportColumns 裁剪列头
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                ConsumableUsageReportConstants.OUTPATIENT_SHEET_NAME,
                exportRows,
                OutpatientConsumableUsageExportRowDto.class,
                request.getExportColumns());
            return this.outpatientConsumableUsageReportMapper.toExportResponse(
                body, exportRows.size(), truncated);
        } catch (BusinessException ex) {
            // Gate：业务异常原样抛出
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊耗材使用报表导出失败", ex);
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_OCU_104,
                "导出失败，请稍后重试");
        }
    }

    /**
     * 从汇总请求装配报表筛选对象。
     *
     * @param request IF-RPT-OCU-02 请求
     * @return 传入 QuerySupport 的筛选
     */
    private ConsumableUsageReportQueryFilter buildQueryFilter(
        SummarizeOutpatientConsumableUsageReportRequest request) {
        return new ConsumableUsageReportQueryFilter()
            .setDateFrom(request.getDateFrom())
            .setDateTo(request.getDateTo())
            .setSummaryDimension(request.getSummaryDimension())
            .setSourceCode(request.getSourceCode())
            .setLocationId(request.getLocationId())
            .setFeeNatureContractNo(request.getFeeNatureContractNo())
            .setRecordTypeCode(request.getRecordTypeCode())
            .setDeviceCategoryCodes(request.getDeviceCategoryCodes())
            .setPerformerPractitionerId(request.getPerformerPractitionerId())
            .setDeviceKeyword(request.getDeviceKeyword())
            .setPatientKeyword(request.getPatientKeyword())
            .setOrganizationId(request.getOrganizationId());
    }

    /**
     * 从明细分页请求装配报表筛选对象。
     *
     * @param request IF-RPT-OCU-03 请求
     * @return 传入 QuerySupport 的筛选
     */
    private ConsumableUsageReportQueryFilter buildQueryFilter(
        PageOutpatientConsumableUsageReportDetailRequest request) {
        return new ConsumableUsageReportQueryFilter()
            .setDateFrom(request.getDateFrom())
            .setDateTo(request.getDateTo())
            .setSourceCode(request.getSourceCode())
            .setLocationId(request.getLocationId())
            .setFeeNatureContractNo(request.getFeeNatureContractNo())
            .setRecordTypeCode(request.getRecordTypeCode())
            .setDeviceCategoryCodes(request.getDeviceCategoryCodes())
            .setPerformerPractitionerId(request.getPerformerPractitionerId())
            .setDeviceKeyword(request.getDeviceKeyword())
            .setPatientKeyword(request.getPatientKeyword())
            .setOrganizationId(request.getOrganizationId());
    }

    /**
     * 从导出请求装配报表筛选对象。
     *
     * @param request IF-RPT-OCU-04 请求
     * @return 传入 QuerySupport 的筛选
     */
    private ConsumableUsageReportQueryFilter buildQueryFilter(
        ExportOutpatientConsumableUsageReportRequest request) {
        return new ConsumableUsageReportQueryFilter()
            .setDateFrom(request.getDateFrom())
            .setDateTo(request.getDateTo())
            .setSummaryDimension(request.getSummaryDimension())
            .setSourceCode(request.getSourceCode())
            .setLocationId(request.getLocationId())
            .setFeeNatureContractNo(request.getFeeNatureContractNo())
            .setRecordTypeCode(request.getRecordTypeCode())
            .setDeviceCategoryCodes(request.getDeviceCategoryCodes())
            .setPerformerPractitionerId(request.getPerformerPractitionerId())
            .setDeviceKeyword(request.getDeviceKeyword())
            .setPatientKeyword(request.getPatientKeyword())
            .setOrganizationId(request.getOrganizationId());
    }

    /**
     * Gate：业务日/时刻闭区间合法且起止不颠倒。
     *
     * <p>允许 {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss}；仅日期时起补 00:00:00、止补 23:59:59。</p>
     *
     * @param dateFrom 日起或日起时刻
     * @param dateTo   日止或日止时刻
     */
    private void validateDateWindow(String dateFrom, String dateTo) {
        // Gate：日期必填（BR-RPT-OCU-001）
        if (StringUtils.isEmpty(dateFrom) || StringUtils.isEmpty(dateTo)) {
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_OCU_102,
                "请选择有效日期范围");
        }
        LocalDateTime from;
        LocalDateTime to;
        try {
            from = ReportDateTimeWindowSupport.parseInclusiveStart(dateFrom);
            to = ReportDateTimeWindowSupport.parseInclusiveEnd(dateTo);
        } catch (DateTimeParseException ex) {
            // 格式非法按日期范围错误拒绝，避免半解析窗口
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_OCU_102,
                "请选择有效日期范围");
        }
        // Gate：结束时刻早于起始时刻时拒绝，避免空结果误导
        if (to.isBefore(from)) {
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_OCU_102,
                "请选择有效日期范围");
        }
    }

    /**
     * 汇总维下拉。
     *
     * @return 选项
     */
    private List<OptionDto> buildSummaryDimensionOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.option(
            ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION,
            ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION_LABEL));
        options.add(this.option(
            ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM,
            ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM_LABEL));
        options.add(this.option(
            ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY,
            ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY_LABEL));
        options.add(this.option(
            ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT,
            ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT_LABEL));
        return options;
    }

    /**
     * 来源筛下拉（患者 / 领用二选一；禁止 ALL 并集）。
     *
     * @return 选项
     */
    private List<OptionDto> buildSourceCodeOptions() {
        List<OptionDto> options = new ArrayList<>();
        // 默认项在前：患者使用（DD：禁止来源=全部混查）
        options.add(this.option(ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE, "患者使用"));
        options.add(this.option(ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE, "科室领用"));
        return options;
    }

    /**
     * 构造 OptionDto。
     *
     * @param value 值
     * @param label 展示
     * @return 选项
     */
    private OptionDto option(String value, String label) {
        OptionDto option = new OptionDto();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }
}
