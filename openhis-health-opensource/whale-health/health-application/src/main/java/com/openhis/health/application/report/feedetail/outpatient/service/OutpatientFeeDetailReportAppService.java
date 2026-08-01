/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.outpatient.service;

import com.openhis.health.application.outpatient.financial.feedetail.request.ExportOutpatientFeeDetailRequest;
import com.openhis.health.application.outpatient.financial.feedetail.request.LoadOutpatientFeeDetailPageInitRequest;
import com.openhis.health.application.outpatient.financial.feedetail.request.PageOutpatientFeeDetailRequest;
import com.openhis.health.application.outpatient.financial.feedetail.response.ExportOutpatientFeeDetailResponse;
import com.openhis.health.application.outpatient.financial.feedetail.response.LoadOutpatientFeeDetailPageInitResponse;
import com.openhis.health.application.outpatient.financial.feedetail.response.PageOutpatientFeeDetailResponse;
import com.openhis.health.application.outpatient.financial.feedetail.service.OutpatientFeeDetailAppService;
import com.openhis.health.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryFooterDto;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryRowDto;
import com.openhis.health.application.report.feedetail.outpatient.mapper.OutpatientFeeDetailReportMapper;
import com.openhis.health.application.report.feedetail.outpatient.request.ExportOutpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.outpatient.request.LoadOutpatientFeeDetailReportPageInitRequest;
import com.openhis.health.application.report.feedetail.outpatient.request.PageOutpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.outpatient.request.SummarizeOutpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.outpatient.response.ExportOutpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.outpatient.response.LoadOutpatientFeeDetailReportPageInitResponse;
import com.openhis.health.application.report.feedetail.outpatient.response.PageOutpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.outpatient.response.SummarizeOutpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.support.FeeDetailReportFinTypeSummaryAssembler;
import com.openhis.health.application.shared.financial.PaymentEntererMatchResolver;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.administration.chargeitem.query.FeeDetailFinTypeSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailReportConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 门诊费用明细报表应用服务（AS-RPT-008，IF-RPT-OFD-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、日期窗、呈现模式（SUMMARY/DETAIL）。</li>
 *   <li>Pre：明细 IF 门面委托 {@link OutpatientFeeDetailAppService}；汇总走
 *       {@link OutpatientFeeDetailLoader} + {@link ChargeItemQueryService#summarizeOutpatientFeeDetailByFinType}。</li>
 *   <li>Cmd：无写库（只读报表）。</li>
 *   <li>Asm：{@link FeeDetailReportFinTypeSummaryAssembler} 按 fin_type 合并与页脚结算刻度。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/outpatientFeeDetailReport/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientFeeDetailReportAppService {

    /** 读路径登录门禁。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 前身门诊费用明细 IF（明细分页/导出/初始化门面）。 */
    private final OutpatientFeeDetailAppService outpatientFeeDetailAppService;

    /** 收费明细事实读侧（fin_type 汇总）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 跨聚合 scope 预解析（payment → chargeItem）。 */
    private final OutpatientFeeDetailLoader outpatientFeeDetailLoader;

    /** payment enterer_id 匹配（Practitioner + 用户主键存量兼容）。 */
    private final PaymentEntererMatchResolver paymentEntererMatchResolver;

    /** 术语下拉与 fin_type 译文。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** definition → fin_type 合并装配。 */
    private final FeeDetailReportFinTypeSummaryAssembler feeDetailReportFinTypeSummaryAssembler;

    /** Request/Response 映射。 */
    private final OutpatientFeeDetailReportMapper outpatientFeeDetailReportMapper;

    /**
     * IF-RPT-OFD-01：门诊费用明细报表页初始化。
     *
     * @param request 空体占位
     * @return 默认日期、筛选项、汇总/明细 Tab
     * @throws BusinessException 未登录或主数据失败时抛出 {@code EX-RPT-OFD-101}
     */
    public LoadOutpatientFeeDetailReportPageInitResponse loadOutpatientFeeDetailReportPageInit(
        LoadOutpatientFeeDetailReportPageInitRequest request) {
        // Gate：须已登录方可加载租户内主数据，拒绝匿名拉下拉
        this.currentOperatorResolver.requireOperatorId(
            OutpatientFeeDetailReportConstants.EX_RPT_OFD_101,
            "页面配置加载失败，请稍后重试");
        try {
            // Pre：门面委托前身 init，复用科室/收费员/大类细分类口径（AS-OP-FIN-005）
            LoadOutpatientFeeDetailPageInitResponse source =
                this.outpatientFeeDetailAppService.loadOutpatientFeeDetailPageInit(
                    new LoadOutpatientFeeDetailPageInitRequest());
            // Asm：在前身选项上补呈现模式与默认汇总 Tab（BR-RPT-OFD-004/005）
            return this.outpatientFeeDetailReportMapper.toLoadPageInitResponse(
                source,
                this.buildPresentationModeOptions(),
                OutpatientFeeDetailReportConstants.DEFAULT_ACTIVE_TAB);
        } catch (BusinessException businessException) {
            // 前身业务码原样上抛，避免二次包装丢失语义
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：主数据/门面异常映射报表初始化失败码
            log.error("门诊费用明细报表页初始化失败", runtimeException);
            throw new BusinessException(
                OutpatientFeeDetailReportConstants.EX_RPT_OFD_101,
                "页面配置加载失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OFD-02：费用类型净汇总。
     *
     * <p>收退 Tab 强制 ALL（净视角，BR-RPT-OFD-003）；与明细同日期/科室等筛选。</p>
     *
     * @param request 筛选条件（收退方向忽略，强制 ALL）
     * @return fin_type 汇总行 + 页脚
     * @throws BusinessException 日期非法 {@code EX-RPT-OFD-102}；查询/数据不完整 {@code EX-RPT-OFD-103}
     */
    public SummarizeOutpatientFeeDetailReportResponse summarizeOutpatientFeeDetailReport(
        SummarizeOutpatientFeeDetailReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            OutpatientFeeDetailReportConstants.EX_RPT_OFD_101,
            "费用明细汇总失败，请稍后重试");
        // Gate：日期闭区间必填且止日不早于起日（BR-RPT-OFD-005）
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        try {
            // Asm：Request → Query，Mapper 内强制 tradeDirection=ALL
            OutpatientFeeDetailPageQuery query = this.outpatientFeeDetailReportMapper.toSummarizeQuery(request);
            this.applyEntererMatchToFeeDetailQuery(query);
            // Pre：payment bill_date scope + 科室/患者/项目收窄
            this.outpatientFeeDetailLoader.prepareOutpatientFeeDetailQueryScopes(query);
            // Pre：域层按 definition_id 代数净汇总（含退负号）
            List<FeeDetailFinTypeSummaryQueryModel> domainRows =
                this.chargeItemQueryService.summarizeOutpatientFeeDetailByFinType(query);
            Map<String, String> finTypeLabelByCode =
                this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            // Asm：历史定价 type_code enrich + 同 fin_type 合并（禁止当前 ACTIVE 目录价）
            List<FeeDetailReportSummaryRowDto> rows =
                this.feeDetailReportFinTypeSummaryAssembler.assembleMergedFinTypeRows(
                    query,
                    domainRows,
                    finTypeLabelByCode,
                    OutpatientFeeDetailReportConstants.EX_RPT_OFD_103,
                    "统计数据不完整，请联系管理员核对收费项目配置");
            // Asm：页脚先加总再 2 位结算刻度
            FeeDetailReportSummaryFooterDto footer =
                this.feeDetailReportFinTypeSummaryAssembler.assembleFooter(rows);
            return this.outpatientFeeDetailReportMapper.toSummarizeResponse(rows, footer);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("门诊费用明细报表汇总失败", runtimeException);
            throw new BusinessException(
                OutpatientFeeDetailReportConstants.EX_RPT_OFD_103,
                "费用明细汇总失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OFD-03：就诊块明细分页（门面委托前身 AS-OP-FIN-005）。
     *
     * @param request 筛选与分页
     * @return 就诊块分页 + 全量 summary
     * @throws BusinessException 前身校验失败原样上抛；其它运行时映射 {@code EX-RPT-OFD-103}
     */
    public PageOutpatientFeeDetailReportResponse pageOutpatientFeeDetailReport(
        PageOutpatientFeeDetailReportRequest request) {
        // Gate：登录入口统一报表码；日期/Tab/分页由前身二次校验
        this.currentOperatorResolver.requireOperatorId(
            OutpatientFeeDetailReportConstants.EX_RPT_OFD_101,
            "费用明细查询失败，请稍后重试");
        try {
            // Asm：报表 Request → 前身分页 Request（字段同构）
            PageOutpatientFeeDetailRequest detailRequest =
                this.outpatientFeeDetailReportMapper.toPageOutpatientFeeDetailRequest(request);
            // Pre：门面委托前身明细分页（就诊块 + 财务金额列）
            PageOutpatientFeeDetailResponse detailResponse =
                this.outpatientFeeDetailAppService.pageOutpatientFeeDetail(detailRequest);
            // Asm：包装为报表 Response（复用前身 DTO）
            return this.outpatientFeeDetailReportMapper.toPageReportResponse(detailResponse);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("门诊费用明细报表明细分页失败", runtimeException);
            throw new BusinessException(
                OutpatientFeeDetailReportConstants.EX_RPT_OFD_103,
                "费用明细查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-OFD-04：导出当前 Tab（仅 SUMMARY 或 DETAIL，BR-RPT-OFD-004）。
     *
     * @param request 筛选 + presentationMode
     * @return xlsx 字节流与行数
     * @throws BusinessException 呈现模式非法 {@code EX-RPT-OFD-102}；导出失败 {@code EX-RPT-OFD-104}
     */
    public ExportOutpatientFeeDetailReportResponse exportOutpatientFeeDetailReport(
        ExportOutpatientFeeDetailReportRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientFeeDetailReportConstants.EX_RPT_OFD_101,
            "导出失败，请稍后重试");
        // Gate：日期窗
        this.validateDateWindow(request.getDateFrom(), request.getDateTo());
        String presentationMode = trimPresentationMode(request.getPresentationMode());
        // Gate：导出必须指定当前 Tab，禁止空/非法 mode 默导全量
        if (!this.isAllowedPresentationMode(presentationMode)) {
            throw new BusinessException(
                OutpatientFeeDetailReportConstants.EX_RPT_OFD_102,
                "请选择有效的查询日期");
        }
        try {
            // 明细 Tab：委托前身导出行与列集，保证与 IF-03 口径一致
            if (OutpatientFeeDetailReportConstants.PRESENTATION_MODE_DETAIL.equals(presentationMode)) {
                // Pre：明细导出门面委托前身
                ExportOutpatientFeeDetailRequest detailRequest =
                    this.outpatientFeeDetailReportMapper.toExportOutpatientFeeDetailRequest(request);
                ExportOutpatientFeeDetailResponse detailResponse =
                    this.outpatientFeeDetailAppService.exportOutpatientFeeDetail(detailRequest);
                // Asm：包装导出响应（contentType/body/rowCount）
                return this.outpatientFeeDetailReportMapper.toExportReportResponse(detailResponse);
            }
            // 汇总 Tab：复用 IF-02 净汇总后再落 Excel
            SummarizeOutpatientFeeDetailReportRequest summarizeRequest =
                this.toSummarizeRequestFromExport(request);
            SummarizeOutpatientFeeDetailReportResponse summarizeResponse =
                this.summarizeOutpatientFeeDetailReport(summarizeRequest);
            List<FeeDetailReportSummaryRowDto> exportRows = summarizeResponse.getRows();
            // 无命中时仍导出表头空表，便于财务对账
            if (exportRows == null) {
                exportRows = List.of();
            }
            boolean truncated = false;
            // 触顶截断：保留前 N 行并标记 truncated，避免 OOM
            if (exportRows.size() > OutpatientFeeDetailReportConstants.EXPORT_MAX_ROWS) {
                truncated = true;
                exportRows = new ArrayList<>(
                    exportRows.subList(0, OutpatientFeeDetailReportConstants.EXPORT_MAX_ROWS));
            }
            // Asm：AnnotatedExcelExporter 按 @Excel 列导出
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                OutpatientFeeDetailReportConstants.EXPORT_SHEET_NAME_SUMMARY,
                exportRows,
                FeeDetailReportSummaryRowDto.class);
            log.info("门诊费用明细报表汇总导出成功 operatorId={} rowCount={} truncated={}",
                operatorId, exportRows.size(), truncated);
            return this.outpatientFeeDetailReportMapper.toSummaryExportResponse(
                body, exportRows.size(), truncated);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("门诊费用明细报表导出失败", runtimeException);
            throw new BusinessException(
                OutpatientFeeDetailReportConstants.EX_RPT_OFD_104,
                "导出失败，请稍后重试或缩小查询范围");
        }
    }

    /**
     * Gate：结算日期闭区间合法（BR-RPT-OFD-005）。
     *
     * @param dateFrom 结算日起
     * @param dateTo   结算日止
     */
    private void validateDateWindow(String dateFrom, String dateTo) {
        // 起止均须填写，否则前端未选日期即可空跑全库
        if (StringUtils.isEmpty(dateFrom) || StringUtils.isEmpty(dateTo)) {
            throw new BusinessException(
                OutpatientFeeDetailReportConstants.EX_RPT_OFD_102,
                "请选择有效的查询日期");
        }
        LocalDate from = LocalDate.parse(dateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        // 止日早于起日拒绝，避免负区间
        if (to.isBefore(from)) {
            throw new BusinessException(
                OutpatientFeeDetailReportConstants.EX_RPT_OFD_102,
                "请选择有效的查询日期");
        }
    }

    /**
     * 收费人筛选写入 payment enterer 白名单。
     *
     * @param query 费用明细 Query
     */
    private void applyEntererMatchToFeeDetailQuery(OutpatientFeeDetailPageQuery query) {
        // 未选收费人时不过滤 enterer，避免误收窄
        if (query == null || query.getCashierId() == null) {
            return;
        }
        query.setEntererMatchIds(
            this.paymentEntererMatchResolver.resolveEntererMatchIds(query.getCashierId()));
    }

    /**
     * 构造汇总/明细 Tab 选项。
     *
     * @return 呈现模式选项
     */
    private List<OptionDto> buildPresentationModeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.buildOption(
            OutpatientFeeDetailReportConstants.PRESENTATION_MODE_SUMMARY,
            OutpatientFeeDetailReportConstants.PRESENTATION_MODE_SUMMARY_LABEL));
        options.add(this.buildOption(
            OutpatientFeeDetailReportConstants.PRESENTATION_MODE_DETAIL,
            OutpatientFeeDetailReportConstants.PRESENTATION_MODE_DETAIL_LABEL));
        return options;
    }

    /**
     * 构造单个 OptionDto。
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
     * 呈现模式白名单（SUMMARY / DETAIL）。
     *
     * @param presentationMode 呈现模式 code
     * @return 是否允许
     */
    private boolean isAllowedPresentationMode(String presentationMode) {
        if (OutpatientFeeDetailReportConstants.PRESENTATION_MODE_SUMMARY.equals(presentationMode)) {
            return true;
        }
        if (OutpatientFeeDetailReportConstants.PRESENTATION_MODE_DETAIL.equals(presentationMode)) {
            return true;
        }
        return false;
    }

    /**
     * 导出请求筛选字段 → 汇总请求（不含 presentationMode / tradeDirection）。
     *
     * @param request 导出请求
     * @return 汇总请求
     */
    private SummarizeOutpatientFeeDetailReportRequest toSummarizeRequestFromExport(
        ExportOutpatientFeeDetailReportRequest request) {
        SummarizeOutpatientFeeDetailReportRequest summarizeRequest =
            new SummarizeOutpatientFeeDetailReportRequest();
        summarizeRequest.setDateFrom(request.getDateFrom());
        summarizeRequest.setDateTo(request.getDateTo());
        summarizeRequest.setRegistrationDepartmentId(request.getRegistrationDepartmentId());
        summarizeRequest.setFeeCategoryCodes(request.getFeeCategoryCodes());
        summarizeRequest.setSubCategoryCodes(request.getSubCategoryCodes());
        summarizeRequest.setPatientKeyword(request.getPatientKeyword());
        summarizeRequest.setProductNameKeyword(request.getProductNameKeyword());
        summarizeRequest.setProductCodeKeyword(request.getProductCodeKeyword());
        summarizeRequest.setCashierId(request.getCashierId());
        return summarizeRequest;
    }

    /**
     * 呈现模式去空白；空则 null（由白名单校验拒绝）。
     *
     * @param presentationMode 原始值
     * @return trim 后值；空输入为 null
     */
    private static String trimPresentationMode(String presentationMode) {
        if (StringUtils.isEmpty(presentationMode)) {
            return null;
        }
        return presentationMode.trim();
    }
}
