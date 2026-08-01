/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.openhis.health.application.report.feedetail.inpatient.service;

import com.openhis.health.application.basic.administration.support.PractitionerCashierOptionProvider;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailExportRowDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailLineDto;
import com.openhis.health.application.outpatient.financial.feedetail.mapper.OutpatientFeeDetailMapper;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryFooterDto;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryRowDto;
import com.openhis.health.application.report.feedetail.inpatient.assembler.InpatientFeeDetailReportBlockAssembler;
import com.openhis.health.application.report.feedetail.inpatient.mapper.InpatientFeeDetailReportMapper;
import com.openhis.health.application.report.feedetail.inpatient.request.ExportInpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.inpatient.request.LoadInpatientFeeDetailReportPageInitRequest;
import com.openhis.health.application.report.feedetail.inpatient.request.PageInpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.inpatient.request.SummarizeInpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.inpatient.response.ExportInpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.inpatient.response.LoadInpatientFeeDetailReportPageInitResponse;
import com.openhis.health.application.report.feedetail.inpatient.response.PageInpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.inpatient.response.SummarizeInpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.inpatient.support.InpatientFeeDetailReportQuerySupport;
import com.openhis.health.application.report.feedetail.support.FeeDetailReportFinTypeSummaryAssembler;
import com.openhis.health.application.shared.financial.PaymentEntererMatchResolver;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.administration.chargeitem.query.FeeDetailFinTypeSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailEncounterBlockQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientFeeDetailReportConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 住院费用明细报表应用服务（AS-RPT-009，IF-RPT-IFD-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、日期窗、收退 Tab、呈现模式与分页参数。</li>
 *   <li>Pre：{@link InpatientFeeDetailReportQuerySupport} scope 预解析与跨聚合 enrichment。</li>
 *   <li>Cmd：无写库。</li>
 *   <li>Asm：fin_type 净汇总、就诊块明细与导出。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientFeeDetailReport/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientFeeDetailReportAppService {

    /** 读路径登录门禁。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 收费明细事实读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 住院科室主数据读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /** 收费员读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** payment enterer_id 匹配。 */
    private final PaymentEntererMatchResolver paymentEntererMatchResolver;

    /** 术语下拉与 fin_type 译文。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 收费员下拉过滤。 */
    private final PractitionerCashierOptionProvider practitionerCashierOptionProvider;

    /** 住院费用明细 scope 预解析。 */
    private final InpatientFeeDetailReportQuerySupport inpatientFeeDetailReportQuerySupport;

    /** definition → fin_type 合并装配。 */
    private final FeeDetailReportFinTypeSummaryAssembler feeDetailReportFinTypeSummaryAssembler;

    /** 就诊块 enrichment 装配。 */
    private final InpatientFeeDetailReportBlockAssembler inpatientFeeDetailReportBlockAssembler;

    /** 复用门诊费用明细导出行映射。 */
    private final OutpatientFeeDetailMapper outpatientFeeDetailMapper;

    /** Request/Response 映射。 */
    private final InpatientFeeDetailReportMapper inpatientFeeDetailReportMapper;

    /** 费用性质下拉（fin_contract.bus_no）。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * IF-RPT-IFD-01：住院费用明细报表页初始化。
     *
     * @param request 空体占位
     * @return 默认日期、筛选项、汇总/明细 Tab
     * @throws BusinessException 未登录或主数据失败时抛出 {@code EX-RPT-IFD-101}
     */
    public LoadInpatientFeeDetailReportPageInitResponse loadInpatientFeeDetailReportPageInit(
        LoadInpatientFeeDetailReportPageInitRequest request) {
        // Gate：须已登录方可加载租户内主数据，拒绝匿名拉下拉
        this.currentOperatorResolver.requireOperatorId(
            InpatientFeeDetailReportConstants.EX_RPT_IFD_101,
            "页面配置加载失败，请稍后重试");
        try {
            LocalDate today = LocalDate.now();
            String todayText = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Pre：住院入院科室下拉（入院科室口径，供科室筛选）
            List<OptionDto> departmentOptions = this.buildDepartmentOptions();
            // Pre：收费员下拉（在职且绑定系统用户）
            List<OptionDto> cashierOptions = this.buildCashierOptions();
            // Pre：收退 Tab / 费用大类 / fin_type 细分类术语
            List<OptionDto> tradeDirectionOptions = this.buildTradeDirectionOptions();
            List<OptionDto> feeCategoryOptions = this.buildFeeCategoryOptions();
            List<OptionDto> finTypeSubCategoryOptions =
                this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            List<OptionDto> feeNatureOptions = this.reportPatientSettlementContextAssembler.loadFeeNatureOptions();
            // Asm：装配 init 响应（默认今日闭区间、呈现模式与默认汇总 Tab）
            return this.inpatientFeeDetailReportMapper.toLoadPageInitResponse(
                todayText,
                todayText,
                InpatientFeeDetailReportConstants.DEFAULT_PAGE_SIZE,
                tradeDirectionOptions,
                feeCategoryOptions,
                finTypeSubCategoryOptions,
                finTypeSubCategoryOptions,
                finTypeSubCategoryOptions,
                departmentOptions,
                cashierOptions,
                this.buildPresentationModeOptions(),
                InpatientFeeDetailReportConstants.DEFAULT_ACTIVE_TAB,
                feeNatureOptions);
        } catch (BusinessException businessException) {
            // 业务码原样上抛，避免二次包装丢失语义
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：主数据异常映射报表初始化失败码
            log.error("住院费用明细报表页初始化失败", runtimeException);
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_101,
                "页面配置加载失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-IFD-02：费用类型净汇总。
     *
     * <p>收退 Tab 强制 ALL（净视角）；与明细同日期/科室等筛选。</p>
     *
     * @param request 筛选条件（收退方向强制 ALL）
     * @return fin_type 汇总行 + 页脚
     * @throws BusinessException 未登录 {@code EX-RPT-IFD-101}；日期非法 {@code EX-RPT-IFD-102}；
     *     查询/数据不完整 {@code EX-RPT-IFD-103}
     */
    public SummarizeInpatientFeeDetailReportResponse summarizeInpatientFeeDetailReport(
        SummarizeInpatientFeeDetailReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            InpatientFeeDetailReportConstants.EX_RPT_IFD_101,
            "费用明细汇总失败，请稍后重试");
        // Gate：日期闭区间必填且止日不早于起日；汇总不校验收退/分页
        this.validateQueryWindow(request.getDateFrom(), request.getDateTo(), null, null, null);
        try {
            // Asm：Request → Query（Mapper 内强制 tradeDirection=ALL）
            OutpatientFeeDetailPageQuery query = this.inpatientFeeDetailReportMapper.toSummarizeQuery(request);
            this.applyEntererMatchToFeeDetailQuery(query);
            // Pre：住院 scope 预解析（payment → chargeItem / 就诊收窄）
            this.inpatientFeeDetailReportQuerySupport.prepareInpatientFeeDetailQueryScopes(query);
            // Pre：域层按 definition_id 代数净汇总（含退负号）
            List<FeeDetailFinTypeSummaryQueryModel> domainRows =
                this.chargeItemQueryService.summarizeInpatientFeeDetailByFinType(query);
            Map<String, String> finTypeLabelByCode =
                this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            // Asm：历史定价 type_code enrich + 同 fin_type 合并（禁止当前 ACTIVE 目录价）
            List<FeeDetailReportSummaryRowDto> rows =
                this.feeDetailReportFinTypeSummaryAssembler.assembleMergedFinTypeRows(
                    query,
                    domainRows,
                    finTypeLabelByCode,
                    InpatientFeeDetailReportConstants.EX_RPT_IFD_103,
                    "统计数据不完整，请联系管理员核对收费项目配置");
            // Asm：页脚先加总再 2 位结算刻度 + 映射响应
            FeeDetailReportSummaryFooterDto footer =
                this.feeDetailReportFinTypeSummaryAssembler.assembleFooter(rows);
            return this.inpatientFeeDetailReportMapper.toSummarizeResponse(rows, footer);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("住院费用明细报表汇总失败", runtimeException);
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_103,
                "费用明细汇总失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-IFD-03：住院就诊块明细分页。
     *
     * @param request 筛选与分页
     * @return 就诊块分页 + 全量 summary
     * @throws BusinessException 未登录 {@code EX-RPT-IFD-101}；日期/Tab/分页非法 {@code EX-RPT-IFD-102}；
     *     查询/数据不完整 {@code EX-RPT-IFD-103}
     */
    public PageInpatientFeeDetailReportResponse pageInpatientFeeDetailReport(
        PageInpatientFeeDetailReportRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            InpatientFeeDetailReportConstants.EX_RPT_IFD_101,
            "费用明细查询失败，请稍后重试");
        // Gate：日期闭区间、收退 Tab 与分页窗口合法
        this.validateQueryWindow(
            request.getDateFrom(),
            request.getDateTo(),
            request.getTradeDirection(),
            request.getPageNo(),
            request.getPageSize());
        try {
            // Asm：Request → Query
            OutpatientFeeDetailPageQuery query = this.inpatientFeeDetailReportMapper.toPageQuery(request);
            this.applyEntererMatchToFeeDetailQuery(query);
            // Pre：住院 scope 预解析
            this.inpatientFeeDetailReportQuerySupport.prepareInpatientFeeDetailQueryScopes(query);
            // Pre：域层按就诊块分页（本页块）
            Page<OutpatientFeeDetailEncounterBlockQueryModel> blockPage =
                this.chargeItemQueryService.pageInpatientFeeDetailEncounterBlocks(query);
            // Asm：就诊块 enrichment（患者/科室/金额列）
            List<OutpatientFeeDetailEncounterBlockDto> blockDtos =
                this.inpatientFeeDetailReportBlockAssembler.assembleEncounterBlockDtos(
                    blockPage.getContent(), query);
            // Asm：全量命中金额 summary（跨页合计，非本页）
            OutpatientFeeDetailAmountSummaryDto summary = this.buildGlobalSummary(query);
            // Asm：包装分页响应
            return this.inpatientFeeDetailReportMapper.toPageResponse(
                blockDtos, blockPage.getTotalElements(), summary, request);
        } catch (IllegalArgumentException illegalArgumentException) {
            // 配置/定价缺口映射数据不完整码，引导财务核对收费项目
            log.warn("住院费用明细数据不完整: {}", illegalArgumentException.getMessage());
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_103,
                "费用数据不完整，请联系管理员核对收费项目配置");
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("住院费用明细报表明细分页失败", runtimeException);
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_103,
                "费用明细查询失败，请稍后重试");
        }
    }

    /**
     * IF-RPT-IFD-04：导出当前 Tab（仅 SUMMARY 或 DETAIL）。
     *
     * @param request 筛选 + presentationMode
     * @return xlsx 字节流与行数
     * @throws BusinessException 未登录 {@code EX-RPT-IFD-101}；日期/呈现模式非法 {@code EX-RPT-IFD-102}；
     *     数据不完整 {@code EX-RPT-IFD-103}；导出失败 {@code EX-RPT-IFD-104}
     */
    public ExportInpatientFeeDetailReportResponse exportInpatientFeeDetailReport(
        ExportInpatientFeeDetailReportRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            InpatientFeeDetailReportConstants.EX_RPT_IFD_101,
            "导出失败，请稍后重试");
        // Gate：日期闭区间与收退 Tab（导出不校验分页）
        this.validateQueryWindow(request.getDateFrom(), request.getDateTo(), request.getTradeDirection(), null, null);
        String presentationMode = trimPresentationMode(request.getPresentationMode());
        // Gate：导出必须指定当前 Tab，禁止空/非法 mode 默导全量
        if (!this.isAllowedPresentationMode(presentationMode)) {
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_102,
                "请选择有效的查询日期");
        }
        try {
            // 汇总 Tab：复用 IF-02 净汇总后再落 Excel
            if (InpatientFeeDetailReportConstants.PRESENTATION_MODE_SUMMARY.equals(presentationMode)) {
                SummarizeInpatientFeeDetailReportRequest summarizeRequest =
                    this.toSummarizeRequestFromExport(request);
                SummarizeInpatientFeeDetailReportResponse summarizeResponse =
                    this.summarizeInpatientFeeDetailReport(summarizeRequest);
                List<FeeDetailReportSummaryRowDto> exportRows = summarizeResponse.getRows();
                // 无命中时仍导出表头空表，便于财务对账
                if (exportRows == null) {
                    exportRows = List.of();
                }
                boolean truncated = false;
                // 触顶截断：保留前 N 行并标记 truncated，避免 OOM
                if (exportRows.size() > InpatientFeeDetailReportConstants.EXPORT_MAX_ROWS) {
                    truncated = true;
                    exportRows = new ArrayList<>(
                        exportRows.subList(0, InpatientFeeDetailReportConstants.EXPORT_MAX_ROWS));
                }
                // Asm：AnnotatedExcelExporter 按 @Excel 列导出汇总 sheet
                byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                    InpatientFeeDetailReportConstants.EXPORT_SHEET_NAME_SUMMARY,
                    exportRows,
                    FeeDetailReportSummaryRowDto.class);
                log.info("住院费用明细报表汇总导出成功 operatorId={} rowCount={} truncated={}",
                    operatorId, exportRows.size(), truncated);
                return this.inpatientFeeDetailReportMapper.toSummaryExportResponse(
                    body, exportRows.size(), truncated);
            }
            // 明细 Tab：全量扫描就诊块后展平行
            // Pre：明细导出行查询与 scope 预解析
            OutpatientFeeDetailPageQuery query = this.inpatientFeeDetailReportMapper.toExportQuery(request);
            this.applyEntererMatchToFeeDetailQuery(query);
            this.inpatientFeeDetailReportQuerySupport.prepareInpatientFeeDetailQueryScopes(query);
            List<OutpatientFeeDetailEncounterBlockDto> allBlocks = this.loadAllEncounterBlocks(query);
            List<OutpatientFeeDetailExportRowDto> exportRows = new ArrayList<>();
            boolean truncated = false;
            // 逐就诊块展平行，直至触顶或扫完
            for (OutpatientFeeDetailEncounterBlockDto block : allBlocks) {
                // 空块或无线跳过，避免 NPE 与空行污染
                if (block == null || block.getLines() == null) {
                    continue;
                }
                // 块内明细行写入导出行
                for (OutpatientFeeDetailLineDto line : block.getLines()) {
                    // 触顶截断：不再追加后续行，标记 truncated 提示缩小范围
                    if (exportRows.size() >= InpatientFeeDetailReportConstants.EXPORT_MAX_ROWS) {
                        truncated = true;
                        break;
                    }
                    exportRows.add(this.outpatientFeeDetailMapper.toExportRowDto(line, block));
                }
                // 已触顶则停止扫描后续就诊块
                if (truncated) {
                    break;
                }
            }
            // Asm：落明细 sheet（无命中时仍导出空表头）
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                InpatientFeeDetailReportConstants.EXPORT_SHEET_NAME_DETAIL,
                exportRows,
                OutpatientFeeDetailExportRowDto.class);
            log.info("住院费用明细报表明细导出成功 operatorId={} rowCount={} truncated={}",
                operatorId, exportRows.size(), truncated);
            return this.inpatientFeeDetailReportMapper.toDetailExportResponse(
                body, exportRows.size(), truncated);
        } catch (IllegalArgumentException illegalArgumentException) {
            // 配置/定价缺口映射数据不完整码
            log.warn("住院费用明细导出数据不完整: {}", illegalArgumentException.getMessage());
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_103,
                "费用数据不完整，请联系管理员核对收费项目配置");
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("住院费用明细报表导出失败", runtimeException);
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_104,
                "导出失败，请稍后重试或缩小查询范围");
        }
    }

    /**
     * Gate：结算日期闭区间、收退 Tab 与分页参数合法。
     *
     * @param dateFrom       结算日起
     * @param dateTo         结算日止
     * @param tradeDirection 收退 Tab（汇总可 null）
     * @param pageNo         页码
     * @param pageSize       页大小
     */
    private void validateQueryWindow(
        String dateFrom,
        String dateTo,
        String tradeDirection,
        Integer pageNo,
        Integer pageSize) {
        // 起止均须填写，否则前端未选日期即可空跑全库
        if (StringUtils.isEmpty(dateFrom) || StringUtils.isEmpty(dateTo)) {
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_102,
                "请选择有效的查询日期");
        }
        LocalDate from = LocalDate.parse(dateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        // 止日早于起日拒绝，避免负区间
        if (to.isBefore(from)) {
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_102,
                "请选择有效的查询日期");
        }
        // 调用方传入收退 Tab 时须校验白名单（汇总传 null 跳过）
        if (tradeDirection != null) {
            String resolvedTradeDirection = tradeDirection;
            // 空串按 ALL 净视角，与前端默认 Tab 对齐
            if (StringUtils.isEmpty(resolvedTradeDirection)) {
                resolvedTradeDirection = OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL;
            }
            // 非法 Tab code 拒绝，防止未知方向漏筛或错筛
            if (!this.isAllowedTradeDirection(resolvedTradeDirection.trim())) {
                throw new BusinessException(
                    InpatientFeeDetailReportConstants.EX_RPT_IFD_102,
                    "请选择有效的查询日期");
            }
        }
        // 页码须 ≥1，拒绝 0/负数导致偏移错乱
        if (pageNo != null && pageNo < 1) {
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_102,
                "请选择有效的查询日期");
        }
        // 页大小须 ≥1，拒绝空页请求
        if (pageSize != null && pageSize < 1) {
            throw new BusinessException(
                InpatientFeeDetailReportConstants.EX_RPT_IFD_102,
                "请选择有效的查询日期");
        }
    }

    /**
     * 全量就诊块扫描装配（导出用）。
     *
     * <p>先物化候选行缓存，再按页切片 enrichment，避免每页重复 IN ChargeItem。</p>
     *
     * @param baseQuery 已预解析 scope 的查询
     * @return 全部就诊块 DTO
     */
    private List<OutpatientFeeDetailEncounterBlockDto> loadAllEncounterBlocks(OutpatientFeeDetailPageQuery baseQuery) {
        // Pre：同请求先物化候选行，后续翻页仅内存切片
        this.chargeItemQueryService.listOutpatientFeeDetailChargeRows(baseQuery);
        List<OutpatientFeeDetailEncounterBlockDto> allBlocks = new ArrayList<>();
        int pageNo = 1;
        int pageSize = InpatientFeeDetailReportConstants.ENCOUNTER_BLOCK_SCAN_PAGE_SIZE;
        // 分页扫描全部就诊块，供导出使用
        while (true) {
            OutpatientFeeDetailPageQuery pageQuery = this.copyPageQuery(baseQuery);
            pageQuery.setPageNo(pageNo);
            pageQuery.setPageSize(pageSize);
            Page<OutpatientFeeDetailEncounterBlockQueryModel> blockPage =
                this.chargeItemQueryService.pageInpatientFeeDetailEncounterBlocks(pageQuery);
            // 本页无数据则结束扫描
            if (blockPage.isEmpty()) {
                break;
            }
            allBlocks.addAll(
                this.inpatientFeeDetailReportBlockAssembler.assembleEncounterBlockDtos(
                    blockPage.getContent(), pageQuery));
            long total = blockPage.getTotalElements();
            // 已覆盖 total 则停止翻页
            if ((long) pageNo * pageSize >= total) {
                break;
            }
            pageNo = pageNo + 1;
        }
        return allBlocks;
    }

    /**
     * 全量命中 summary：候选行轻量加总（售价/实收/医保等），禁止为页脚全量 enrichment 就诊块。
     *
     * <p>明细分页页脚不跑全量历史进价；{@code purchaseAmountTotal} 置空（与门诊同构）。</p>
     *
     * @param query 已预解析 scope 的查询
     * @return 全量金额 summary
     */
    private OutpatientFeeDetailAmountSummaryDto buildGlobalSummary(OutpatientFeeDetailPageQuery query) {
        // Pre：复用同请求候选行（含 payment 快照分母）
        List<OutpatientFeeDetailChargeRowQueryModel> rows =
            this.chargeItemQueryService.listOutpatientFeeDetailChargeRows(query);
        // Asm：轻量加总为页脚跨页合计真源（BR-OP-FDT-013 同构；进价合计可空）
        return this.inpatientFeeDetailReportBlockAssembler.assembleGlobalSummaryFromChargeRows(rows);
    }

    /**
     * 复制 Query 分页窗口与 scope 字段。
     *
     * @param source 源 Query
     * @return 新 Query
     */
    private OutpatientFeeDetailPageQuery copyPageQuery(OutpatientFeeDetailPageQuery source) {
        OutpatientFeeDetailPageQuery target = new OutpatientFeeDetailPageQuery();
        target.setBillDateFrom(source.getBillDateFrom());
        target.setBillDateTo(source.getBillDateTo());
        target.setRegistrationDepartmentId(source.getRegistrationDepartmentId());
        target.setFeeCategoryCodes(source.getFeeCategoryCodes());
        target.setSubCategoryCodes(source.getSubCategoryCodes());
        target.setPatientKeyword(source.getPatientKeyword());
        target.setProductNameKeyword(source.getProductNameKeyword());
        target.setProductCodeKeyword(source.getProductCodeKeyword());
        target.setCashierId(source.getCashierId());
        target.setEntererMatchIds(source.getEntererMatchIds());
        target.setTradeDirection(source.getTradeDirection());
        target.setFeeNatureContractNo(source.getFeeNatureContractNo());
        target.setResolvedChargeItemScope(source.getResolvedChargeItemScope());
        target.setResolvedEncounterScope(source.getResolvedEncounterScope());
        target.setPaymentChargeItemLinks(source.getPaymentChargeItemLinks());
        target.setResolvedPaymentChargeItemLinks(source.getResolvedPaymentChargeItemLinks());
        target.setPaymentContextByChargeItemId(source.getPaymentContextByChargeItemId());
        // Pre：导出翻页复用同请求候选行/预加载 ChargeItem，避免每页重复 IN
        target.setPreloadedChargeItemsById(source.getPreloadedChargeItemsById());
        target.setCachedChargeRows(source.getCachedChargeRows());
        return target;
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
     * 构造住院科室下拉。
     *
     * @return 科室选项
     */
    private List<OptionDto> buildDepartmentOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<Organization> organizations = this.organizationQueryService.listOrganizationsForInpatientAdmission();
        // 入院科室主数据 → 筛选项
        for (Organization organization : organizations) {
            // 缺主键的脏数据不入下拉，避免前端传无效科室 id
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
     * 构造收费员下拉。
     *
     * @return 收费员选项
     */
    private List<OptionDto> buildCashierOptions() {
        Page<Practitioner> page = this.practitionerQueryService.pageActivePractitionersForPosition(
            null,
            null,
            PageRequest.of(0, InpatientFeeDetailReportConstants.CASHIER_OPTION_PAGE_SIZE),
            true);
        List<Practitioner> eligiblePractitioners =
            this.practitionerCashierOptionProvider.filterWithActiveBoundSystemUser(page.getContent());
        return this.practitionerCashierOptionProvider.toCashierOptions(eligiblePractitioners);
    }

    /**
     * 构造收退 Tab 选项。
     *
     * @return ALL/CHARGE/REFUND
     */
    private List<OptionDto> buildTradeDirectionOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.buildOption(
            OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL,
            OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL_LABEL));
        options.add(this.buildOption(
            OutpatientFeeDetailConstants.TRADE_DIRECTION_CHARGE,
            OutpatientFeeDetailConstants.TRADE_DIRECTION_CHARGE_LABEL));
        options.add(this.buildOption(
            OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND,
            OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND_LABEL));
        return options;
    }

    /**
     * 构造费用大类选项。
     *
     * @return 大类选项
     */
    private List<OptionDto> buildFeeCategoryOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.buildOption(
            OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION,
            OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION_LABEL));
        options.add(this.buildOption(
            OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY,
            OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY_LABEL));
        options.add(this.buildOption(
            OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE,
            OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE_LABEL));
        options.add(this.buildOption(
            OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER,
            OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER_LABEL));
        return options;
    }

    /**
     * 构造汇总/明细 Tab 选项。
     *
     * @return 呈现模式选项
     */
    private List<OptionDto> buildPresentationModeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.buildOption(
            InpatientFeeDetailReportConstants.PRESENTATION_MODE_SUMMARY,
            InpatientFeeDetailReportConstants.PRESENTATION_MODE_SUMMARY_LABEL));
        options.add(this.buildOption(
            InpatientFeeDetailReportConstants.PRESENTATION_MODE_DETAIL,
            InpatientFeeDetailReportConstants.PRESENTATION_MODE_DETAIL_LABEL));
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
     * 收退 Tab 白名单。
     *
     * @param tradeDirection Tab code
     * @return 是否允许
     */
    private boolean isAllowedTradeDirection(String tradeDirection) {
        // ALL：收退净视角
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL.equals(tradeDirection)) {
            return true;
        }
        // CHARGE：仅收费方向
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_CHARGE.equals(tradeDirection)) {
            return true;
        }
        // REFUND：仅退费方向
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(tradeDirection)) {
            return true;
        }
        return false;
    }

    /**
     * 呈现模式白名单。
     *
     * @param presentationMode SUMMARY / DETAIL
     * @return 是否允许
     */
    private boolean isAllowedPresentationMode(String presentationMode) {
        // SUMMARY：费用类型净汇总 Tab
        if (InpatientFeeDetailReportConstants.PRESENTATION_MODE_SUMMARY.equals(presentationMode)) {
            return true;
        }
        // DETAIL：就诊块明细 Tab
        if (InpatientFeeDetailReportConstants.PRESENTATION_MODE_DETAIL.equals(presentationMode)) {
            return true;
        }
        return false;
    }

    /**
     * 导出请求 → 汇总请求。
     *
     * @param request 导出请求
     * @return 汇总请求
     */
    private SummarizeInpatientFeeDetailReportRequest toSummarizeRequestFromExport(
        ExportInpatientFeeDetailReportRequest request) {
        SummarizeInpatientFeeDetailReportRequest summarizeRequest =
            new SummarizeInpatientFeeDetailReportRequest();
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
     * 呈现模式去空白。
     *
     * @param presentationMode 原始值
     * @return trim 后值
     */
    private static String trimPresentationMode(String presentationMode) {
        // 空输入返回 null，由呈现模式白名单拒绝
        if (StringUtils.isEmpty(presentationMode)) {
            return null;
        }
        return presentationMode.trim();
    }
}
