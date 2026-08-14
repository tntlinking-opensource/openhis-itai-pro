/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.service;

import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailRowDto;
import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailSummaryRowDto;
import com.openhis.health.application.report.registrationdetail.mapper.OutpatientRegistrationDetailReportMapper;
import com.openhis.health.application.report.registrationdetail.request.ExportOutpatientRegistrationDetailReportRequest;
import com.openhis.health.application.report.registrationdetail.request.LoadOutpatientRegistrationDetailReportPageInitRequest;
import com.openhis.health.application.report.registrationdetail.request.PageOutpatientRegistrationDetailReportDetailRequest;
import com.openhis.health.application.report.registrationdetail.request.SummarizeOutpatientRegistrationDetailReportRequest;
import com.openhis.health.application.report.registrationdetail.response.ExportOutpatientRegistrationDetailReportResponse;
import com.openhis.health.application.report.registrationdetail.response.LoadOutpatientRegistrationDetailReportPageInitResponse;
import com.openhis.health.application.report.registrationdetail.response.PageOutpatientRegistrationDetailReportDetailResponse;
import com.openhis.health.application.report.registrationdetail.response.SummarizeOutpatientRegistrationDetailReportResponse;
import com.openhis.health.application.report.registrationdetail.support.OutpatientRegistrationDetailReportEnricher;
import com.openhis.health.application.report.registrationdetail.support.OutpatientRegistrationDetailReportQuerySupport;
import com.openhis.health.domain.administration.encounter.queries.OutpatientRegistrationReportPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientRegistrationDetailStatsQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientRegistrationReportLineQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientRegistrationReportSummaryRow;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientRegistrationReportConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 门诊挂号明细报表只读编排（AS-RPT-007 IF-RPT-ORG-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link CurrentOperatorResolver}：登录 Gate</li>
 *   <li>{@link OutpatientRegistrationDetailReportQuerySupport}：日期窗与医生/关键字 → resolvedEncounterIds</li>
 *   <li>{@link EncounterQueryService}：净汇总与明细分页（白名单）</li>
 *   <li>{@link OutpatientRegistrationDetailReportEnricher}：号源/费用性质/挂号费 Asm</li>
 *   <li>{@link OutpatientRegistrationDetailReportMapper}：Request/Response 映射</li>
 * </ul>
 * <p>接入：AppServiceControllerRegistrar →
 * {@code POST /api/app/outpatientRegistrationDetailReport/{methodName}}；不另写 RestController。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutpatientRegistrationDetailReportAppService {

    /** 登录 Gate。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 挂号报表 scope 预解析。 */
    private final OutpatientRegistrationDetailReportQuerySupport querySupport;

    /** 挂号报表展示 enrich。 */
    private final OutpatientRegistrationDetailReportEnricher enricher;

    /** Encounter 报表读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 门诊挂号科室下拉。 */
    private final OrganizationQueryService organizationQueryService;

    /** 号源类型下拉。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /** 医生岗人员加载。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** Request/Response 映射。 */
    private final OutpatientRegistrationDetailReportMapper mapper;

    /**
     * IF-RPT-ORG-01：页面初始化（默认当日、科室/医生/号源/汇总视角下拉）。
     *
     * @param request 空体占位
     * @return 初始化响应
     * @throws BusinessException {@link OutpatientRegistrationReportConstants#EX_RPT_ORG_101} 初始化失败
     */
    public LoadOutpatientRegistrationDetailReportPageInitResponse loadOutpatientRegistrationDetailReportPageInit(
        LoadOutpatientRegistrationDetailReportPageInitRequest request) {
        // Gate：须已登录方可加载租户内主数据
        this.currentOperatorResolver.requireOperatorId(
            OutpatientRegistrationReportConstants.EX_RPT_ORG_101,
            "页面初始化失败，请稍后重试");
        try {
            LocalDate today = LocalDate.now();
            String todayText = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Pre：门诊挂号科室 / 医生岗 / 号源类型 / 汇总视角选项
            List<OptionDto> organizationOptions = this.buildOrganizationOptions();
            List<OptionDto> practitionerOptions = this.buildPractitionerOptions();
            List<OptionDto> serviceTypeOptions = this.buildServiceTypeOptions();
            List<OptionDto> summaryViewOptions = this.buildSummaryViewOptions();
            // Asm：默认当日 + 下拉装配（BR-RPT-ORG-001 默认当日）
            return this.mapper.toLoadPageInitResponse(
                todayText,
                todayText,
                OutpatientRegistrationReportConstants.DEFAULT_PAGE_SIZE,
                OutpatientRegistrationReportConstants.SUMMARY_VIEW_DEPARTMENT,
                organizationOptions,
                practitionerOptions,
                serviceTypeOptions,
                summaryViewOptions);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出，不包装为 101
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：主数据加载异常映射 EX-RPT-ORG-101
            log.error("门诊挂号明细报表页初始化失败", runtimeException);
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_101,
                "页面初始化失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-ORG-02：按科室或医生 × 号源净汇总。
     *
     * @param request 日期与汇总视角等筛选
     * @return 净汇总行与合计
     * @throws BusinessException {@link OutpatientRegistrationReportConstants#EX_RPT_ORG_102} 参数非法；
     *         {@link OutpatientRegistrationReportConstants#EX_RPT_ORG_103} 查询失败
     */
    public SummarizeOutpatientRegistrationDetailReportResponse summarizeOutpatientRegistrationDetailReport(
        SummarizeOutpatientRegistrationDetailReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            OutpatientRegistrationReportConstants.EX_RPT_ORG_101,
            "查询失败，请稍后重试");
        // Gate：日期必填且合法；汇总视角白名单
        LocalDate dateFrom = this.parseRequiredDate(request.getDateFrom());
        LocalDate dateTo = this.parseRequiredDate(request.getDateTo());
        this.assertDateRange(dateFrom, dateTo);
        String summaryDimension = this.querySupport.resolveSummaryDimension(request.getSummaryView());
        this.assertSummaryDimension(summaryDimension);
        try {
            // Pre：Request → Query + 医生/关键字 scope
            OutpatientRegistrationReportPageQuery query = this.mapper.toSummarizeQuery(request);
            query.setSummaryDimension(summaryDimension);
            this.querySupport.prepareQueryScopes(
                query,
                dateFrom,
                dateTo,
                request.getOrganizationId(),
                request.getPractitionerId(),
                request.getKeyword());
            // Cmd：领域净汇总（退号冲减）
            List<OutpatientRegistrationReportSummaryRow> domainRows =
                this.encounterQueryService.summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType(query);
            // Asm：展示名/挂号费 enrich + 净笔数与净金额合计（BR-RPT-ORG-003）
            List<OutpatientRegistrationDetailSummaryRowDto> rows = this.enricher.enrichSummaryRows(domainRows);
            long total = 0L;
            BigDecimal totalFee = BigDecimal.ZERO;
            // 遍历汇总行累加净挂号笔数与挂号费供页脚展示
            for (OutpatientRegistrationDetailSummaryRowDto row : rows) {
                // 跳过空行，避免 NPE 污染合计
                if (row == null) {
                    continue;
                }
                if (row.getNetRegistrationCount() != null) {
                    total = total + row.getNetRegistrationCount();
                }
                if (row.getRegistrationFeeAmount() != null) {
                    totalFee = totalFee.add(row.getRegistrationFeeAmount());
                }
            }
            return this.mapper.toSummarizeResponse(
                rows, total, MonetaryAmountSupport.scaleSettle(totalFee));
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：查询失败映射 EX-RPT-ORG-103
            log.error("门诊挂号明细报表汇总失败", runtimeException);
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_103,
                "查询失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-ORG-03：挂号明细分页（含费用性质/医保类型/挂号费）。
     *
     * @param request 筛选与分页
     * @return 明细分页
     * @throws BusinessException {@link OutpatientRegistrationReportConstants#EX_RPT_ORG_102} 参数非法；
     *         {@link OutpatientRegistrationReportConstants#EX_RPT_ORG_103} 查询失败
     */
    public PageOutpatientRegistrationDetailReportDetailResponse pageOutpatientRegistrationDetailReportDetail(
        PageOutpatientRegistrationDetailReportDetailRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            OutpatientRegistrationReportConstants.EX_RPT_ORG_101,
            "查询失败，请稍后重试");
        LocalDate dateFrom = this.parseRequiredDate(request.getDateFrom());
        LocalDate dateTo = this.parseRequiredDate(request.getDateTo());
        this.assertDateRange(dateFrom, dateTo);
        this.assertPageWindow(request.getPageNo(), request.getPageSize());
        try {
            // Pre：Request → Query + scope
            OutpatientRegistrationReportPageQuery query = this.mapper.toDetailPageQuery(request);
            this.querySupport.prepareQueryScopes(
                query,
                dateFrom,
                dateTo,
                request.getOrganizationId(),
                request.getPractitionerId(),
                request.getKeyword());
            // Cmd：领域明细分页
            Page<OutpatientRegistrationReportLineQueryModel> page =
                this.encounterQueryService.pageOutpatientRegistrationDetailReport(query);
            // Pre：同筛选挂/退页脚（对齐门诊挂号当日统计口径）
            OutpatientRegistrationDetailStatsQueryModel stats =
                this.encounterQueryService.countOutpatientRegistrationDetailReportStats(query);
            // Asm：费用性质 / 号源 / 挂号费 enrich → 分页响应 + 页脚
            List<OutpatientRegistrationDetailRowDto> rows = this.enricher.enrichDetailRows(page.getContent());
            return this.mapper.toDetailPageResponse(rows, page, stats);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：查询失败映射 EX-RPT-ORG-103
            log.error("门诊挂号明细报表明细分页失败", runtimeException);
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_103,
                "查询失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-ORG-04：导出当前 Tab（汇总或明细）。
     *
     * @param request 筛选 + presentationMode
     * @return xlsx 元数据与字节
     * @throws BusinessException {@link OutpatientRegistrationReportConstants#EX_RPT_ORG_102} 参数非法；
     *         {@link OutpatientRegistrationReportConstants#EX_RPT_ORG_104} 导出失败
     */
    public ExportOutpatientRegistrationDetailReportResponse exportOutpatientRegistrationDetailReport(
        ExportOutpatientRegistrationDetailReportRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientRegistrationReportConstants.EX_RPT_ORG_101,
            "导出失败，请稍后重试");
        LocalDate dateFrom = this.parseRequiredDate(request.getDateFrom());
        LocalDate dateTo = this.parseRequiredDate(request.getDateTo());
        this.assertDateRange(dateFrom, dateTo);
        String presentationMode = StringUtils.trimToNull(request.getPresentationMode());
        // Gate：导出仅当前 Tab（BR-RPT-ORG-005），须为 SUMMARY 或 DETAIL
        if (!OutpatientRegistrationReportConstants.PRESENTATION_MODE_SUMMARY.equals(presentationMode)
            && !OutpatientRegistrationReportConstants.PRESENTATION_MODE_DETAIL.equals(presentationMode)) {
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_102,
                "请选择有效的导出类型");
        }
        try {
            // SUMMARY：净汇总全量导出
            if (OutpatientRegistrationReportConstants.PRESENTATION_MODE_SUMMARY.equals(presentationMode)) {
                return this.exportSummary(request, dateFrom, dateTo, operatorId);
            }
            // DETAIL：明细分页循环直至空页或触顶 EXPORT_MAX_ROWS
            return this.exportDetail(request, dateFrom, dateTo, operatorId);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：导出失败映射 EX-RPT-ORG-104
            log.error("门诊挂号明细报表导出失败", runtimeException);
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_104,
                "导出失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * 导出汇总 Tab（与 IF-02 同口径净汇总）。
     *
     * @param request    导出筛选
     * @param dateFrom   已校验业务日起
     * @param dateTo     已校验业务日止
     * @param operatorId 操作人（审计日志）
     * @return xlsx 响应
     */
    private ExportOutpatientRegistrationDetailReportResponse exportSummary(
        ExportOutpatientRegistrationDetailReportRequest request,
        LocalDate dateFrom,
        LocalDate dateTo,
        Long operatorId) {
        // Pre：汇总视角映射 + scope
        String summaryDimension = this.querySupport.resolveSummaryDimension(request.getSummaryView());
        this.assertSummaryDimension(summaryDimension);
        OutpatientRegistrationReportPageQuery query = this.mapper.toExportQuery(request);
        query.setSummaryDimension(summaryDimension);
        this.querySupport.prepareQueryScopes(
            query,
            dateFrom,
            dateTo,
            request.getOrganizationId(),
            request.getPractitionerId(),
            request.getKeyword());
        // Cmd：领域净汇总
        List<OutpatientRegistrationReportSummaryRow> domainRows =
            this.encounterQueryService.summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType(query);
        // Asm：展示 enrich 后写 Excel
        List<OutpatientRegistrationDetailSummaryRowDto> rows = this.enricher.enrichSummaryRows(domainRows);
        boolean truncated = false;
        List<OutpatientRegistrationDetailSummaryRowDto> exportRows = rows;
        // 触顶导出行上限时截断，仍交付已收集部分
        if (rows.size() > OutpatientRegistrationReportConstants.EXPORT_MAX_ROWS) {
            truncated = true;
            exportRows = rows.subList(0, OutpatientRegistrationReportConstants.EXPORT_MAX_ROWS);
        }
        byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
            OutpatientRegistrationReportConstants.EXPORT_SHEET_SUMMARY,
            exportRows,
            OutpatientRegistrationDetailSummaryRowDto.class);
        String fileName = OutpatientRegistrationReportConstants.EXPORT_FILE_PREFIX_SUMMARY
            + dateFrom.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
            + GeneralSharedConstants.DASH
            + dateTo.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
            + ".xlsx";
        log.info("门诊挂号汇总导出成功 operatorId={} rowCount={} truncated={}",
            operatorId, exportRows.size(), truncated);
        return this.mapper.toExportResponse(
            fileName,
            body,
            exportRows.size(),
            truncated,
            OutpatientRegistrationReportConstants.EXPORT_CONTENT_TYPE);
    }

    /**
     * 导出明细 Tab（循环分页至空页或触顶 EXPORT_MAX_ROWS）。
     *
     * @param request    导出筛选
     * @param dateFrom   已校验业务日起
     * @param dateTo     已校验业务日止
     * @param operatorId 操作人（审计日志）
     * @return xlsx 响应
     */
    private ExportOutpatientRegistrationDetailReportResponse exportDetail(
        ExportOutpatientRegistrationDetailReportRequest request,
        LocalDate dateFrom,
        LocalDate dateTo,
        Long operatorId) {
        // Pre：与 IF-03 同筛选 scope
        OutpatientRegistrationReportPageQuery query = this.mapper.toExportQuery(request);
        this.querySupport.prepareQueryScopes(
            query,
            dateFrom,
            dateTo,
            request.getOrganizationId(),
            request.getPractitionerId(),
            request.getKeyword());
        List<OutpatientRegistrationDetailRowDto> exportRows = new ArrayList<>();
        boolean truncated = false;
        int pageNo = 1;
        int pageSize = OutpatientRegistrationReportConstants.DEFAULT_PAGE_SIZE;
        // 循环拉取明细分页直至空页或触顶 EXPORT_MAX_ROWS
        while (exportRows.size() < OutpatientRegistrationReportConstants.EXPORT_MAX_ROWS) {
            query.setPageNo(pageNo);
            query.setPageSize(pageSize);
            Page<OutpatientRegistrationReportLineQueryModel> page =
                this.encounterQueryService.pageOutpatientRegistrationDetailReport(query);
            List<OutpatientRegistrationReportLineQueryModel> content = page.getContent();
            // 空页：已无更多明细，结束循环
            if (content == null || content.isEmpty()) {
                break;
            }
            List<OutpatientRegistrationDetailRowDto> pageRows = this.enricher.enrichDetailRows(content);
            // 将本页行追加到导出缓冲，触顶则标记 truncated
            for (OutpatientRegistrationDetailRowDto row : pageRows) {
                if (exportRows.size() >= OutpatientRegistrationReportConstants.EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                exportRows.add(row);
            }
            // 已触顶：停止翻页
            if (truncated) {
                break;
            }
            // 末页不足一页：无下一页
            if (content.size() < pageSize) {
                break;
            }
            pageNo = pageNo + 1;
        }
        // Asm：明细行写 Excel
        byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
            OutpatientRegistrationReportConstants.EXPORT_SHEET_DETAIL,
            exportRows,
            OutpatientRegistrationDetailRowDto.class);
        String fileName = OutpatientRegistrationReportConstants.EXPORT_FILE_PREFIX_DETAIL
            + dateFrom.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
            + GeneralSharedConstants.DASH
            + dateTo.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
            + ".xlsx";
        log.info("门诊挂号明细导出成功 operatorId={} rowCount={} truncated={}",
            operatorId, exportRows.size(), truncated);
        return this.mapper.toExportResponse(
            fileName,
            body,
            exportRows.size(),
            truncated,
            OutpatientRegistrationReportConstants.EXPORT_CONTENT_TYPE);
    }

    /**
     * 解析必填业务日；空白或格式非法则 EX-RPT-ORG-102。
     *
     * @param dateText yyyy-MM-dd 文本
     * @return 业务日
     */
    private LocalDate parseRequiredDate(String dateText) {
        // Gate：日期必填（BR-RPT-ORG-001）
        if (StringUtils.isEmpty(dateText)) {
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_102,
                "请选择有效日期");
        }
        try {
            return LocalDate.parse(dateText.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        } catch (RuntimeException runtimeException) {
            // Gate：无法解析为业务日
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_102,
                "请选择有效日期",
                runtimeException);
        }
    }

    /**
     * 止日不得早于起日（闭区间合法）。
     *
     * @param dateFrom 起日
     * @param dateTo   止日
     */
    private void assertDateRange(LocalDate dateFrom, LocalDate dateTo) {
        // Gate：倒挂日期窗拒绝查询
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_102,
                "请选择有效日期");
        }
    }

    /**
     * 汇总维须为 ORGANIZATION 或 DOCTOR（与 summaryView 映射结果一致）。
     *
     * @param summaryDimension 领域汇总维码
     */
    private void assertSummaryDimension(String summaryDimension) {
        // Gate：白名单内直接放行
        if (OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_ORGANIZATION.equals(summaryDimension)
            || OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_DOCTOR.equals(summaryDimension)) {
            return;
        }
        throw new BusinessException(
            OutpatientRegistrationReportConstants.EX_RPT_ORG_102,
            "请选择有效的汇总视角");
    }

    /**
     * 分页窗口须为正整数（pageNo/pageSize ≥ 1）。
     *
     * @param pageNo   页码（1-based）
     * @param pageSize 页大小
     */
    private void assertPageWindow(Integer pageNo, Integer pageSize) {
        // Gate：页码非法
        if (pageNo == null || pageNo < 1) {
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_102,
                "请选择有效的分页参数");
        }
        // Gate：页大小非法
        if (pageSize == null || pageSize < 1) {
            throw new BusinessException(
                OutpatientRegistrationReportConstants.EX_RPT_ORG_102,
                "请选择有效的分页参数");
        }
    }

    /**
     * 门诊挂号科室 OptionDto（AS IF-01 科室下拉）。
     *
     * @return 科室选项；无主数据时为空列表
     */
    private List<OptionDto> buildOrganizationOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<Organization> organizations =
            this.organizationQueryService.listOrganizationsForOutpatientRegistration();
        // 无科室主数据：返回空下拉，前端仍可开页
        if (organizations == null) {
            return options;
        }
        // 逐条装配 value=id、label=名称
        for (Organization organization : organizations) {
            // 跳过无主键脏行
            if (organization == null || organization.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(organization.getId()));
            // 名称为空时仅回填 value，避免 NPE
            if (organization.getName() != null) {
                option.setLabel(organization.getName().trim());
            }
            options.add(option);
        }
        return options;
    }

    /**
     * 全院医生岗 OptionDto（orgId=null 不按科室收窄；前端可再按科级联）。
     *
     * @return 医生选项
     */
    private List<OptionDto> buildPractitionerOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<Practitioner> doctors = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_DOCTOR,
            null);
        // 无医生岗：空下拉
        if (doctors == null) {
            return options;
        }
        for (Practitioner doctor : doctors) {
            // 跳过无主键脏行
            if (doctor == null || doctor.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(doctor.getId()));
            if (doctor.getName() != null) {
                option.setLabel(doctor.getName().trim());
            }
            options.add(option);
        }
        return options;
    }

    /**
     * 可挂号号源类型 OptionDto（便民/普通/专家等）。
     *
     * @return 号源选项
     */
    private List<OptionDto> buildServiceTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<HealthcareService> services = this.healthcareServiceQueryService.listServicesForRegistration();
        // 无号源：空下拉
        if (services == null) {
            return options;
        }
        for (HealthcareService service : services) {
            // 跳过无主键脏行
            if (service == null || service.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(service.getId()));
            if (service.getName() != null) {
                option.setLabel(service.getName().trim());
            }
            options.add(option);
        }
        return options;
    }

    /**
     * 汇总视角 OptionDto（科室/医生；SDD summaryView）。
     *
     * @return 视角选项
     */
    private List<OptionDto> buildSummaryViewOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto department = new OptionDto();
        department.setValue(OutpatientRegistrationReportConstants.SUMMARY_VIEW_DEPARTMENT);
        department.setLabel("按科室");
        options.add(department);
        OptionDto doctor = new OptionDto();
        doctor.setValue(OutpatientRegistrationReportConstants.SUMMARY_VIEW_DOCTOR);
        doctor.setLabel("按接诊医生");
        options.add(doctor);
        return options;
    }
}

