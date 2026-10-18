/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.surplusretention.service;

import com.zxplat.application.report.surplusretention.dto.SurplusRetentionReportRowDto;
import com.zxplat.application.report.surplusretention.mapper.SurplusRetentionReportMapper;
import com.zxplat.application.report.surplusretention.request.ExportSurplusRetentionReportRequest;
import com.zxplat.application.report.surplusretention.request.LoadSurplusRetentionReportPageInitRequest;
import com.zxplat.application.report.surplusretention.request.PageSurplusRetentionReportRequest;
import com.zxplat.application.report.surplusretention.response.ExportSurplusRetentionReportResponse;
import com.zxplat.application.report.surplusretention.response.LoadSurplusRetentionReportPageInitResponse;
import com.zxplat.application.report.surplusretention.response.PageSurplusRetentionReportResponse;
import com.zxplat.application.report.surplusretention.support.SurplusRetentionReportQuerySupport;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.SurplusRetentionReportConstants;
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
 * 药品结余留用报表只读编排（AS-RPT-015 IF-RPT-SRB-01～03）。
 *
 * <p>接入：AppServiceControllerRegistrar →
 * {@code POST /api/app/surplusRetentionReport/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurplusRetentionReportAppService {

    /** 登录 Gate。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 筛选预解析与分页/导出收集。 */
    private final SurplusRetentionReportQuerySupport querySupport;

    /** Request/Response 映射。 */
    private final SurplusRetentionReportMapper mapper;

    /**
     * IF-RPT-SRB-01：页面初始化（默认当月签发窗 + 批次术语下拉）。
     *
     * @param request 空体占位
     * @return 默认签发月窗、批次下拉、默认页大小
     * @throws BusinessException {@link SurplusRetentionReportConstants#EX_RPT_SRB_101}
     */
    public LoadSurplusRetentionReportPageInitResponse loadSurplusRetentionReportPageInit(
        LoadSurplusRetentionReportPageInitRequest request) {
        // Gate：须已登录（request 为空体占位，不参与筛选）
        this.currentOperatorResolver.requireOperatorId(
            SurplusRetentionReportConstants.EX_RPT_SRB_101,
            "页面初始化失败，请稍后重试");
        try {
            LocalDate today = LocalDate.now();
            // Pre：当月首尾日作为默认签发窗
            String[] monthBounds = this.querySupport.resolveDefaultAuthoredMonthBounds(today);
            // Asm：默认窗 + 批次下拉
            return this.mapper.toLoadPageInitResponse(
                monthBounds[0],
                monthBounds[1],
                SurplusRetentionReportConstants.DEFAULT_PAGE_SIZE,
                this.querySupport.buildBatchOptions());
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出，不包装为 101
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：初始化异常映射 EX-RPT-SRB-101
            log.error("药品结余留用报表页初始化失败", runtimeException);
            throw new BusinessException(
                SurplusRetentionReportConstants.EX_RPT_SRB_101,
                "页面初始化失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-SRB-02：明细分页。
     *
     * @param request 批次、签发窗与分页
     * @return 分页响应（content 为 19 列行）
     * @throws BusinessException {@link SurplusRetentionReportConstants#EX_RPT_SRB_102}/103
     */
    public PageSurplusRetentionReportResponse pageSurplusRetentionReport(
        PageSurplusRetentionReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            SurplusRetentionReportConstants.EX_RPT_SRB_101,
            "查询失败，请稍后重试");
        // Gate：批次必选
        String batchCode = this.querySupport.requireBatchCode(
            request.getSurplusRetentionBatchCode(),
            SurplusRetentionReportConstants.EX_RPT_SRB_102);
        // Gate：签发日窗必填且起≤止
        LocalDateTime[] window = this.querySupport.parseRequiredAuthoredDateWindow(
            request.getAuthoredDateFrom(),
            request.getAuthoredDateTo(),
            SurplusRetentionReportConstants.EX_RPT_SRB_102);
        // Gate：分页窗口
        int[] pageWindow = this.querySupport.resolvePageWindow(
            request.getPageNo(),
            request.getPageSize(),
            SurplusRetentionReportConstants.EX_RPT_SRB_102);
        try {
            // Pre：目录 → 申请范围
            SurplusRetentionReportQuerySupport.FilterScope scope =
                this.querySupport.resolveFilterScope(batchCode, window[0], window[1]);
            // Cmd：患者发退药 COMPLETED+REFUNDED 分页并装配 19 列
            SurplusRetentionReportQuerySupport.PageBundle bundle =
                this.querySupport.pageDetail(scope, pageWindow[0], pageWindow[1]);
            // Asm：分页壳 + content
            return this.mapper.toPageResponse(bundle.rows(), bundle.page());
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：查询失败映射 EX-RPT-SRB-103
            log.error("药品结余留用报表明细分页失败", runtimeException);
            throw new BusinessException(
                SurplusRetentionReportConstants.EX_RPT_SRB_103,
                "查询失败，请稍后重试",
                runtimeException);
        }
    }

    /**
     * IF-RPT-SRB-03：导出当前筛选明细 Excel。
     *
     * @param request 同筛选 + exportColumns
     * @return xlsx
     * @throws BusinessException {@link SurplusRetentionReportConstants#EX_RPT_SRB_102}/104
     */
    public ExportSurplusRetentionReportResponse exportSurplusRetentionReport(
        ExportSurplusRetentionReportRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            SurplusRetentionReportConstants.EX_RPT_SRB_101,
            "导出失败，请稍后重试");
        // Gate：批次必选
        String batchCode = this.querySupport.requireBatchCode(
            request.getSurplusRetentionBatchCode(),
            SurplusRetentionReportConstants.EX_RPT_SRB_102);
        // Gate：签发日窗
        LocalDateTime[] window = this.querySupport.parseRequiredAuthoredDateWindow(
            request.getAuthoredDateFrom(),
            request.getAuthoredDateTo(),
            SurplusRetentionReportConstants.EX_RPT_SRB_102);
        try {
            // Pre：范围仅解析一次
            SurplusRetentionReportQuerySupport.FilterScope scope =
                this.querySupport.resolveFilterScope(batchCode, window[0], window[1]);
            // Cmd：循环分页收集至上限
            SurplusRetentionReportQuerySupport.ExportBundle bundle =
                this.querySupport.collectExportRows(scope);
            List<SurplusRetentionReportRowDto> exportRows = bundle.rows();
            // Asm：写 Excel（列裁剪走 exportColumns）
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                SurplusRetentionReportConstants.EXPORT_SHEET,
                exportRows,
                SurplusRetentionReportRowDto.class,
                request.getExportColumns());
            String fileName = SurplusRetentionReportConstants.EXPORT_FILE_PREFIX
                + window[0].toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                + GeneralSharedConstants.DASH
                + window[1].toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
                + ".xlsx";
            log.info("药品结余留用报表导出成功 operatorId={} rowCount={} truncated={}",
                operatorId, exportRows.size(), bundle.truncated());
            return this.mapper.toExportResponse(
                fileName,
                body,
                exportRows.size(),
                bundle.truncated(),
                SurplusRetentionReportConstants.EXPORT_CONTENT_TYPE);
        } catch (BusinessException businessException) {
            // Gate：业务异常原样抛出
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：导出失败映射 EX-RPT-SRB-104
            log.error("药品结余留用报表导出失败", runtimeException);
            throw new BusinessException(
                SurplusRetentionReportConstants.EX_RPT_SRB_104,
                "导出失败，请稍后重试",
                runtimeException);
        }
    }
}
