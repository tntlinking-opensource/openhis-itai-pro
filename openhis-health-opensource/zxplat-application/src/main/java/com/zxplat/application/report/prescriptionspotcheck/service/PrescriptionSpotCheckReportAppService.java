/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.prescriptionspotcheck.service;

import com.zxplat.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckHerbalRowDto;
import com.zxplat.application.report.prescriptionspotcheck.dto.PrescriptionSpotCheckWesternRowDto;
import com.zxplat.application.report.prescriptionspotcheck.mapper.PrescriptionSpotCheckReportMapper;
import com.zxplat.application.report.prescriptionspotcheck.request.ExportPrescriptionSpotCheckReportRequest;
import com.zxplat.application.report.prescriptionspotcheck.request.LoadPrescriptionSpotCheckReportPageInitRequest;
import com.zxplat.application.report.prescriptionspotcheck.request.PagePrescriptionSpotCheckReportRequest;
import com.zxplat.application.report.prescriptionspotcheck.response.ExportPrescriptionSpotCheckReportResponse;
import com.zxplat.application.report.prescriptionspotcheck.response.LoadPrescriptionSpotCheckReportPageInitResponse;
import com.zxplat.application.report.prescriptionspotcheck.response.PagePrescriptionSpotCheckReportResponse;
import com.zxplat.application.report.prescriptionspotcheck.support.PrescriptionSpotCheckReportQuerySupport;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.PrescriptionSpotCheckReportConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import com.zxplat.framework.core.application.util.AnnotatedExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊处方抽查报表只读编排（AS-RPT-014 IF-RPT-PSC-01～03）。
 *
 * <p>接入：AppServiceControllerRegistrar →
 * {@code POST /api/app/prescriptionSpotCheckReport/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionSpotCheckReportAppService {

    /** 登录 Gate。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 筛选预解析与分页/导出收集。 */
    private final PrescriptionSpotCheckReportQuerySupport querySupport;

    /** Request/Response 映射。 */
    private final PrescriptionSpotCheckReportMapper mapper;

    /**
     * IF-RPT-PSC-01：页面初始化（默认西药及中成药 + 当月开方窗）。
     *
     * @param request 空体占位
     * @return 默认类型、开方月窗、类型下拉
     * @throws BusinessException {@link PrescriptionSpotCheckReportConstants#EX_RPT_PSC_101}
     */
    public LoadPrescriptionSpotCheckReportPageInitResponse loadPrescriptionSpotCheckReportPageInit(
        LoadPrescriptionSpotCheckReportPageInitRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            PrescriptionSpotCheckReportConstants.EX_RPT_PSC_101,
            "页面初始化失败，请稍后重试");
        try {
            LocalDate today = LocalDate.now();
            // Pre：当月首尾日作为默认开方窗
            String[] monthBounds = this.querySupport.resolveDefaultOpenedMonthBounds(today);
            // Asm：默认类型西药及中成药 + 类型下拉
            return this.mapper.toLoadPageInitResponse(
                PrescriptionSpotCheckReportConstants.REPORT_TYPE_WESTERN_PATENT,
                monthBounds[0],
                monthBounds[1],
                PrescriptionSpotCheckReportConstants.DEFAULT_PAGE_SIZE,
                this.querySupport.buildReportTypeOptions());
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出，不包装为 101
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：初始化异常映射 EX-RPT-PSC-101
            log.error("门诊处方抽查报表页初始化失败", runtimeException);
            throw new BusinessException(
                PrescriptionSpotCheckReportConstants.EX_RPT_PSC_101,
                "页面初始化失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-PSC-02：按类型明细分页。
     *
     * @param request 类型、开方窗与分页
     * @return 分页响应（westernContent / herbalContent 互斥）
     * @throws BusinessException {@link PrescriptionSpotCheckReportConstants#EX_RPT_PSC_102}/103
     */
    public PagePrescriptionSpotCheckReportResponse pagePrescriptionSpotCheckReport(
        PagePrescriptionSpotCheckReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            PrescriptionSpotCheckReportConstants.EX_RPT_PSC_101,
            "查询失败，请稍后重试");
        // Gate：类型必选且仅允许西药/饮片
        String reportType = this.querySupport.requireReportType(
            request.getReportType(),
            PrescriptionSpotCheckReportConstants.EX_RPT_PSC_102);
        // Gate：开方日窗必填且起≤止
        LocalDateTime[] window = this.querySupport.parseRequiredOpenedDateWindow(
            request.getOpenedDateFrom(),
            request.getOpenedDateTo(),
            PrescriptionSpotCheckReportConstants.EX_RPT_PSC_102);
        // Gate：分页窗口
        int[] pageWindow = this.querySupport.resolvePageWindow(
            request.getPageNo(),
            request.getPageSize(),
            PrescriptionSpotCheckReportConstants.EX_RPT_PSC_102);
        try {
            // 饮片类型：装配饮片列
            if (PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL.equals(reportType)) {
                // Pre/Cmd：AMB→COMPLETED→分页→Asm 饮片行
                PrescriptionSpotCheckReportQuerySupport.HerbalPageBundle bundle =
                    this.querySupport.pageHerbal(
                        window[0],
                        window[1],
                        pageWindow[0],
                        pageWindow[1]);
                // Asm：分页壳 + herbalContent
                return this.mapper.toHerbalPageResponse(reportType, bundle.rows(), bundle.page());
            }
            // Pre/Cmd：西药及中成药分页装配
            PrescriptionSpotCheckReportQuerySupport.WesternPageBundle bundle =
                this.querySupport.pageWestern(
                    window[0],
                    window[1],
                    pageWindow[0],
                    pageWindow[1]);
            // Asm：分页壳 + westernContent
            return this.mapper.toWesternPageResponse(reportType, bundle.rows(), bundle.page());
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：查询失败映射 EX-RPT-PSC-103
            log.error("门诊处方抽查报表明细分页失败", runtimeException);
            throw new BusinessException(
                PrescriptionSpotCheckReportConstants.EX_RPT_PSC_103,
                "查询失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-PSC-03：导出当前筛选明细 Excel。
     *
     * @param request 同筛选 + exportColumns
     * @return xlsx
     * @throws BusinessException {@link PrescriptionSpotCheckReportConstants#EX_RPT_PSC_102}/104
     */
    public ExportPrescriptionSpotCheckReportResponse exportPrescriptionSpotCheckReport(
        ExportPrescriptionSpotCheckReportRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            PrescriptionSpotCheckReportConstants.EX_RPT_PSC_101,
            "导出失败，请稍后重试");
        // Gate：类型必选
        String reportType = this.querySupport.requireReportType(
            request.getReportType(),
            PrescriptionSpotCheckReportConstants.EX_RPT_PSC_102);
        // Gate：开方日窗
        LocalDateTime[] window = this.querySupport.parseRequiredOpenedDateWindow(
            request.getOpenedDateFrom(),
            request.getOpenedDateTo(),
            PrescriptionSpotCheckReportConstants.EX_RPT_PSC_102);
        try {
            // 饮片导出
            if (PrescriptionSpotCheckReportConstants.REPORT_TYPE_HERBAL.equals(reportType)) {
                // Pre/Cmd：循环分页收集至上限
                PrescriptionSpotCheckReportQuerySupport.HerbalExportBundle bundle =
                    this.querySupport.collectHerbalExportRows(
                        window[0],
                        window[1]);
                List<PrescriptionSpotCheckHerbalRowDto> exportRows = bundle.rows();
                // Asm：写 Excel（列裁剪走 exportColumns）
                byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                    PrescriptionSpotCheckReportConstants.EXPORT_SHEET_HERBAL,
                    exportRows,
                    PrescriptionSpotCheckHerbalRowDto.class,
                    request.getExportColumns());
                String fileName = PrescriptionSpotCheckReportConstants.EXPORT_FILE_PREFIX_HERBAL
                    + window[0].toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                    + GeneralSharedConstants.DASH
                    + window[1].toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                    + ".xlsx";
                log.info("门诊处方抽查饮片导出成功 operatorId={} rowCount={} truncated={}",
                    operatorId, exportRows.size(), bundle.truncated());
                return this.mapper.toExportResponse(
                    fileName,
                    body,
                    exportRows.size(),
                    bundle.truncated(),
                    PrescriptionSpotCheckReportConstants.EXPORT_CONTENT_TYPE);
            }
            // Pre/Cmd：西药循环收集
            PrescriptionSpotCheckReportQuerySupport.WesternExportBundle bundle =
                this.querySupport.collectWesternExportRows(
                    window[0],
                    window[1]);
            List<PrescriptionSpotCheckWesternRowDto> exportRows = bundle.rows();
            // Asm：写 Excel
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                PrescriptionSpotCheckReportConstants.EXPORT_SHEET_WESTERN_PATENT,
                exportRows,
                PrescriptionSpotCheckWesternRowDto.class,
                request.getExportColumns());
            String fileName = PrescriptionSpotCheckReportConstants.EXPORT_FILE_PREFIX_WESTERN_PATENT
                + window[0].toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                + GeneralSharedConstants.DASH
                + window[1].toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                + ".xlsx";
            log.info("门诊处方抽查西药导出成功 operatorId={} rowCount={} truncated={}",
                operatorId, exportRows.size(), bundle.truncated());
            return this.mapper.toExportResponse(
                fileName,
                body,
                exportRows.size(),
                bundle.truncated(),
                PrescriptionSpotCheckReportConstants.EXPORT_CONTENT_TYPE);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：导出失败映射 EX-RPT-PSC-104
            log.error("门诊处方抽查报表导出失败", runtimeException);
            throw new BusinessException(
                PrescriptionSpotCheckReportConstants.EX_RPT_PSC_104,
                "导出失败，请稍后重试",
                runtimeException);
        }
    }
}
