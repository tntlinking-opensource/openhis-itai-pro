/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.insurancefeedetail.inpatient.service;

import com.openhis.health.application.report.insurancefeedetail.dto.InsuranceFeeDetailRowDto;
import com.openhis.health.application.report.insurancefeedetail.inpatient.mapper.InpatientInsuranceFeeDetailReportMapper;
import com.openhis.health.application.report.insurancefeedetail.inpatient.request.ExportInpatientInsuranceFeeDetailReportRequest;
import com.openhis.health.application.report.insurancefeedetail.inpatient.request.LoadInpatientInsuranceFeeDetailReportPageInitRequest;
import com.openhis.health.application.report.insurancefeedetail.inpatient.request.PageInpatientInsuranceFeeDetailReportRequest;
import com.openhis.health.application.report.insurancefeedetail.inpatient.response.ExportInpatientInsuranceFeeDetailReportResponse;
import com.openhis.health.application.report.insurancefeedetail.inpatient.response.LoadInpatientInsuranceFeeDetailReportPageInitResponse;
import com.openhis.health.application.report.insurancefeedetail.inpatient.response.PageInpatientInsuranceFeeDetailReportResponse;
import com.openhis.health.application.report.insurancefeedetail.support.InsuranceFeeDetailReportQuerySupport;
import com.openhis.health.application.shared.report.ReportDateTimeWindowSupport;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InsuranceFeeDetailReportConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院医保费用明细报表只读编排（AS-RPT-013 IF-RPT-IIF-01～03）。
 *
 * <p>接入：AppServiceControllerRegistrar →
 * {@code POST /api/app/inpatientInsuranceFeeDetailReport/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientInsuranceFeeDetailReportAppService {

    /** 登录 Gate。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 筛选预解析与 SPI 编排。 */
    private final InsuranceFeeDetailReportQuerySupport querySupport;

    /** Request/Response 映射。 */
    private final InpatientInsuranceFeeDetailReportMapper mapper;

    /**
     * IF-RPT-IIF-01：页面初始化（默认近一个月结算窗）。
     *
     * @param request 空体占位
     * @return 默认时间窗与筛选项
     * @throws BusinessException {@link InsuranceFeeDetailReportConstants#EX_RPT_IIF_101}
     */
    public LoadInpatientInsuranceFeeDetailReportPageInitResponse loadInpatientInsuranceFeeDetailReportPageInit(
        LoadInpatientInsuranceFeeDetailReportPageInitRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            InsuranceFeeDetailReportConstants.EX_RPT_IIF_101,
            "页面初始化失败，请稍后重试");
        try {
            LocalDate today = LocalDate.now();
            // Pre：默认业务窗 = 今日往前 N 个自然月～今日（近一个月）
            LocalDate fromDay = today.minusMonths(
                InsuranceFeeDetailReportConstants.DEFAULT_LOOKBACK_MONTHS);
            // Pre：科室/费用类别/工伤门控/职工居民主数据
            // Asm：默认近一个月窗 + 下拉装配
            return this.mapper.toLoadPageInitResponse(
                ReportDateTimeWindowSupport.formatDayStart(fromDay),
                ReportDateTimeWindowSupport.formatDayEnd(today),
                InsuranceFeeDetailReportConstants.DEFAULT_PAGE_SIZE,
                this.querySupport.buildInpatientOrganizationOptions(),
                this.querySupport.buildContractCategoryOptions(),
                this.querySupport.isWorkInjuryEnabled(),
                this.querySupport.buildInsutypeGroupOptions());
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出，不包装为 101
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：主数据加载异常映射 EX-RPT-IIF-101
            log.error("住院医保费用明细报表页初始化失败", runtimeException);
            throw new BusinessException(
                InsuranceFeeDetailReportConstants.EX_RPT_IIF_101,
                "页面初始化失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-IIF-02：明细分页 + 页脚四列合计。
     *
     * @param request 筛选与分页
     * @return 分页响应
     * @throws BusinessException {@link InsuranceFeeDetailReportConstants#EX_RPT_IIF_102}/103
     */
    public PageInpatientInsuranceFeeDetailReportResponse pageInpatientInsuranceFeeDetailReport(
        PageInpatientInsuranceFeeDetailReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            InsuranceFeeDetailReportConstants.EX_RPT_IIF_101,
            "查询失败，请稍后重试");
        LocalDateTime[] window = this.querySupport.parseRequiredDateTimeWindow(
            request.getDateFrom(),
            request.getDateTo(),
            InsuranceFeeDetailReportConstants.EX_RPT_IIF_102);
        try {
            // Pre：Support 预解析合同/就诊 scope 并组装 SPI 入参
            // Cmd：SPI 明细分页 + SQL 合计
            InsuranceFeeDetailReportQuerySupport.InsuranceFeeDetailPageBundle bundle =
                this.querySupport.pageWithFooter(
                    EncounterClass.IMP.getCode(),
                    window[0],
                    window[1],
                    request.getWorkInjuryOnly(),
                    request.getContractCategoryCodes(),
                    request.getInsutypeGroup(),
                    request.getOrganizationId(),
                    request.getSetlIdKeyword(),
                    request.getMdtrtIdKeyword(),
                    request.getBusNoKeyword(),
                    request.getPatientNameKeyword(),
                    request.getPageNo(),
                    request.getPageSize(),
                    InsuranceFeeDetailReportConstants.EX_RPT_IIF_102);
            // Asm：分页壳 + 页脚
            return this.mapper.toPageResponse(
                bundle.page().getContent(),
                bundle.page(),
                bundle.footer());
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：查询失败映射 EX-RPT-IIF-103
            log.error("住院医保费用明细报表明细分页失败", runtimeException);
            throw new BusinessException(
                InsuranceFeeDetailReportConstants.EX_RPT_IIF_103,
                "查询失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-IIF-03：导出当前筛选明细 Excel。
     *
     * @param request 筛选 + exportColumns
     * @return xlsx
     * @throws BusinessException {@link InsuranceFeeDetailReportConstants#EX_RPT_IIF_102}/104
     */
    public ExportInpatientInsuranceFeeDetailReportResponse exportInpatientInsuranceFeeDetailReport(
        ExportInpatientInsuranceFeeDetailReportRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            InsuranceFeeDetailReportConstants.EX_RPT_IIF_101,
            "导出失败，请稍后重试");
        LocalDateTime[] window = this.querySupport.parseRequiredDateTimeWindow(
            request.getDateFrom(),
            request.getDateTo(),
            InsuranceFeeDetailReportConstants.EX_RPT_IIF_102);
        try {
            // Pre：同筛选循环分页收集至导出行上限
            // Cmd：Support 调 SPI 分页直至空页或触顶
            InsuranceFeeDetailReportQuerySupport.InsuranceFeeDetailExportBundle bundle =
                this.querySupport.collectExportRows(
                    EncounterClass.IMP.getCode(),
                    window[0],
                    window[1],
                    request.getWorkInjuryOnly(),
                    request.getContractCategoryCodes(),
                    request.getInsutypeGroup(),
                    request.getOrganizationId(),
                    request.getSetlIdKeyword(),
                    request.getMdtrtIdKeyword(),
                    request.getBusNoKeyword(),
                    request.getPatientNameKeyword(),
                    InsuranceFeeDetailReportConstants.EX_RPT_IIF_102);
            List<InsuranceFeeDetailRowDto> exportRows = bundle.rows();
            // Asm：写 Excel（列裁剪走 exportColumns）
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                InsuranceFeeDetailReportConstants.EXPORT_SHEET_INPATIENT,
                exportRows,
                InsuranceFeeDetailRowDto.class,
                request.getExportColumns());
            String fileName = InsuranceFeeDetailReportConstants.EXPORT_FILE_PREFIX_INPATIENT
                + window[0].toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                + GeneralSharedConstants.DASH
                + window[1].toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                + ".xlsx";
            log.info("住院医保费用明细导出成功 operatorId={} rowCount={} truncated={}",
                operatorId, exportRows.size(), bundle.truncated());
            return this.mapper.toExportResponse(
                fileName,
                body,
                exportRows.size(),
                bundle.truncated(),
                InsuranceFeeDetailReportConstants.EXPORT_CONTENT_TYPE);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：导出失败映射 EX-RPT-IIF-104
            log.error("住院医保费用明细报表导出失败", runtimeException);
            throw new BusinessException(
                InsuranceFeeDetailReportConstants.EX_RPT_IIF_104,
                "导出失败，请稍后重试",
                runtimeException);
        }
    }
}
