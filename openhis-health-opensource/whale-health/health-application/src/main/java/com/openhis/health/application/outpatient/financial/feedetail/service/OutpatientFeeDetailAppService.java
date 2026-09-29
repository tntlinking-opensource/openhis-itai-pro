/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.feedetail.service;

import com.openhis.health.application.outpatient.financial.feedetail.assembler.OutpatientFeeDetailAssembler;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailExportRowDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailLineDto;
import com.openhis.health.application.outpatient.financial.feedetail.mapper.OutpatientFeeDetailMapper;
import com.openhis.health.application.outpatient.financial.feedetail.request.ExportOutpatientFeeDetailRequest;
import com.openhis.health.application.outpatient.financial.feedetail.request.LoadOutpatientFeeDetailPageInitRequest;
import com.openhis.health.application.outpatient.financial.feedetail.request.PageOutpatientFeeDetailRequest;
import com.openhis.health.application.outpatient.financial.feedetail.response.ExportOutpatientFeeDetailResponse;
import com.openhis.health.application.outpatient.financial.feedetail.response.LoadOutpatientFeeDetailPageInitResponse;
import com.openhis.health.application.outpatient.financial.feedetail.response.PageOutpatientFeeDetailResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailEncounterBlockQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.practitioner.PractitionerCashierOptionProvider;
import com.openhis.health.application.shared.financial.PaymentEntererMatchResolver;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.ChargeItemActivityOperatorResolver;
import com.openhis.health.application.shared.report.PatientSettlementContextRow;
import com.openhis.health.application.shared.report.ChargeItemOrderingDoctorFromRequestSupport;
import com.openhis.health.application.shared.report.FeeDetailMatchedScopePurchaseTotalSupport;
import com.openhis.health.application.shared.report.ReportDateTimeWindowSupport;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊费用明细应用服务（AS-OP-FIN-005，IF-OP-FDT-01～03）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、日期窗、收退 Tab 与分页参数。</li>
 *   <li>Pre：{@link OutpatientFeeDetailLoader} scope 预解析与跨聚合批量读 enrichment。</li>
 *   <li>Asm：{@link OutpatientFeeDetailAssembler} 金额列 + Mapper 装配响应/导出。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/outpatientFeeDetail/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientFeeDetailAppService {

    /** 读/写路径登录门禁。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 收费明细事实读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 就诊挂号科室、busNo 读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者姓名读侧。 */
    private final PatientQueryService patientQueryService;

    /** 挂号科室主数据读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /** 开单医生/收费员读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 产品定义批量读侧。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 号源类型（挂号费）单条回落读侧。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /** payment enterer_id 匹配（仅 Practitioner 主键，**BR-OPC-016**）。 */
    private final PaymentEntererMatchResolver paymentEntererMatchResolver;

    /** 术语下拉与单位译文。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 收费员下拉：排除绑定用户已删/未启用的 practitioner。 */
    private final PractitionerCashierOptionProvider practitionerCashierOptionProvider;

    /** 跨聚合 scope 预解析。 */
    private final OutpatientFeeDetailLoader outpatientFeeDetailLoader;

    /** 开单医生自三类 Request 批量解析。 */
    private final ChargeItemOrderingDoctorFromRequestSupport chargeItemOrderingDoctorFromRequestSupport;

    /** 收费行活动操作人（Procedure / DeviceDispense）。 */
    private final ChargeItemActivityOperatorResolver chargeItemActivityOperatorResolver;

    /** 定价行 fin_type 解析（与日结口径一致）。 */
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 历史进价解析（BR-PRICE-SRC-001）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 报表包装倍数与拆零展示单价。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 金额列装配。 */
    private final OutpatientFeeDetailAssembler outpatientFeeDetailAssembler;

    /** Request/Response 映射。 */
    private final OutpatientFeeDetailMapper outpatientFeeDetailMapper;

    /** 费用性质 + medType 报表上下文装配。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /** 明细分页页脚：命中 scope 历史进价合计（禁 RowQueryModel）。 */
    private final FeeDetailMatchedScopePurchaseTotalSupport feeDetailMatchedScopePurchaseTotalSupport;

    /**
     * IF-OP-FDT-01：门诊费用明细页初始化。
     *
     * @param request 空体占位
     * @return 默认日期、收退 Tab、大类/细分类、科室与收费员下拉
     * @throws BusinessException 未登录或主数据加载失败时抛出 EX-OP-FDT-101
     */
    public LoadOutpatientFeeDetailPageInitResponse loadOutpatientFeeDetailPageInit(
        LoadOutpatientFeeDetailPageInitRequest request) {
        // Gate：须已登录方可加载租户内主数据
        this.currentOperatorResolver.requireOperatorId(
            OutpatientFeeDetailConstants.EX_OP_FDT_101,
            "页面配置加载失败，请稍后重试");
        try {
            LocalDate today = LocalDate.now();
            String todayText = today.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            // Pre：OrganizationQueryService#listOrganizationsForOutpatientRegistration（SDD §3.2 步骤 2）
            List<OptionDto> registrationDepartmentOptions = this.buildRegistrationDepartmentOptions();
            // Pre：PractitionerQueryService#pageActivePractitionersForPosition（SDD §3.2 步骤 3）
            List<OptionDto> cashierOptions = this.buildCashierOptions();
            List<OptionDto> tradeDirectionOptions = this.buildTradeDirectionOptions();
            List<OptionDto> feeCategoryOptions = this.buildFeeCategoryOptions();
            // Pre：细分类下拉统一取 fin_type_code 字典（与日结、金额统计口径一致）
            List<OptionDto> finTypeSubCategoryOptions =
                this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            List<OptionDto> feeNatureOptions = this.reportPatientSettlementContextAssembler.loadFeeNatureOptions();
            // Asm：步骤 5 toLoadOutpatientFeeDetailPageInitResponse
            return this.outpatientFeeDetailMapper.toLoadOutpatientFeeDetailPageInitResponse(
                todayText,
                todayText,
                OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE,
                tradeDirectionOptions,
                feeCategoryOptions,
                finTypeSubCategoryOptions,
                finTypeSubCategoryOptions,
                finTypeSubCategoryOptions,
                registrationDepartmentOptions,
                cashierOptions,
                feeNatureOptions);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            // Gate：主数据加载异常映射 EX-OP-FDT-101
            log.error("门诊费用明细页初始化失败", runtimeException);
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_101,
                "页面配置加载失败，请稍后重试");
        }
    }

    /**
     * IF-OP-FDT-02：分页查询门诊费用明细（按就诊块 + 全量 summary）。
     *
     * @param request 筛选与分页参数
     * @return 当前页就诊块与全量金额合计
     * @throws BusinessException 参数非法、数据不完整或查询失败
     */
    public PageOutpatientFeeDetailResponse pageOutpatientFeeDetail(PageOutpatientFeeDetailRequest request) {
        // Gate：SDD §3.3 步骤 1 — 登录
        this.currentOperatorResolver.requireOperatorId(
            OutpatientFeeDetailConstants.EX_OP_FDT_101,
            "费用明细查询失败，请稍后重试");
        // Gate：SDD §3.3 步骤 2 — 日期/Tab/分页（BR-OP-FDT-001、009、013）
        this.validateQueryWindow(
            request.getDateFrom(),
            request.getDateTo(),
            request.getTradeDirection(),
            request.getPageNo(),
            request.getPageSize());
        try {
            // Asm：SDD §3.3 步骤 3 — Request → OutpatientFeeDetailPageQuery
            OutpatientFeeDetailPageQuery query = this.outpatientFeeDetailMapper.toOutpatientFeeDetailPageQuery(request);
            this.applyEntererMatchToFeeDetailQuery(query);
            // Pre：SDD §3.3 步骤 4 — Payment bill_date/收退链路预解析（与日结同轴）
            this.outpatientFeeDetailLoader.prepareOutpatientFeeDetailPageScopes(query);
            // Pre：SDD §3.3 步骤 5 — 当前页就诊块分页
            Page<OutpatientFeeDetailEncounterBlockQueryModel> blockPage =
                this.chargeItemQueryService.pageOutpatientFeeDetailEncounterBlocks(query);
            // Pre：SDD §3.3 步骤 6～10 + Asm 步骤 12～13 — enrichment 与行/块装配
            List<OutpatientFeeDetailEncounterBlockDto> blockDtos =
                this.assembleEncounterBlockDtos(blockPage.getContent(), query);
            // Pre+Asm：页脚售价 SQL SUM（或投影合计），禁止全日窗实体
            OutpatientFeeDetailAmountSummaryDto summary = this.buildGlobalSummary(query);
            // Asm：SDD §3.3 步骤 14 — 分页响应；pageNo 回写钳制后的实际页（防空页）
            PageOutpatientFeeDetailResponse response =
                this.outpatientFeeDetailMapper.toPageOutpatientFeeDetailResponse(
                    blockDtos,
                    blockPage.getTotalElements(),
                    summary,
                    request);
            response.setPageNo(blockPage.getNumber() + 1);
            return response;
        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("门诊费用明细数据不完整: {}", illegalArgumentException.getMessage());
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_103,
                "费用数据不完整，请联系管理员核对收费项目配置");
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("门诊费用明细查询失败", runtimeException);
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_104,
                "费用明细查询失败，请稍后重试");
        }
    }

    /**
     * IF-OP-FDT-03：导出门诊费用明细（与 IF-02 同口径）。
     *
     * @param request 筛选条件
     * @return xlsx 字节流与导出行数
     * @throws BusinessException 参数非法、数据不完整或导出失败
     */
    public ExportOutpatientFeeDetailResponse exportOutpatientFeeDetail(ExportOutpatientFeeDetailRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientFeeDetailConstants.EX_OP_FDT_101,
            "导出失败，请稍后重试");
        this.validateQueryWindow(
            request.getDateFrom(),
            request.getDateTo(),
            request.getTradeDirection(),
            null,
            null);
        try {
            // Asm：导出 Request → Query（与 IF-02 同筛选，BR-OP-FDT-016）
            OutpatientFeeDetailPageQuery query = this.outpatientFeeDetailMapper.toOutpatientFeeDetailPageQuery(request);
            this.applyEntererMatchToFeeDetailQuery(query);
            // Pre：复用 IF-02 步骤 4～13 全量 blocks
            this.outpatientFeeDetailLoader.prepareOutpatientFeeDetailQueryScopes(query);
            List<OutpatientFeeDetailEncounterBlockDto> allBlocks =
                this.loadAllEncounterBlocks(query);
            List<OutpatientFeeDetailExportRowDto> exportRows = new ArrayList<>();
            boolean truncated = false;
            // Asm：平铺 blocks → ExportRowDto；触顶 EXPORT_MAX_ROWS 时截断（SDD §3.4 步骤 5～6）
            for (OutpatientFeeDetailEncounterBlockDto block : allBlocks) {
                if (block == null || block.getLines() == null) {
                    continue;
                }
                for (OutpatientFeeDetailLineDto line : block.getLines()) {
                    if (exportRows.size() >= OutpatientFeeDetailConstants.EXPORT_MAX_ROWS) {
                        // Gate：导出行触顶，标记 truncated 继续交付部分数据
                        truncated = true;
                        break;
                    }
                    exportRows.add(this.outpatientFeeDetailMapper.toExportRowDto(line, block));
                }
                if (truncated) {
                    break;
                }
            }
            // Asm：SDD §3.4 步骤 7 — AnnotatedExcelExporter（前端 exportColumns 裁剪）
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                OutpatientFeeDetailConstants.EXPORT_SHEET_NAME,
                exportRows,
                OutpatientFeeDetailExportRowDto.class,
                request.getExportColumns());
            log.info("门诊费用明细导出成功 operatorId={} rowCount={} truncated={}",
                operatorId, exportRows.size(), truncated);
            // Asm：SDD §3.4 步骤 8 — 导出响应
            return this.outpatientFeeDetailMapper.toExportOutpatientFeeDetailResponse(body, exportRows.size(), truncated);
        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("门诊费用明细导出数据不完整: {}", illegalArgumentException.getMessage());
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_103,
                "费用数据不完整，请联系管理员核对收费项目配置");
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("门诊费用明细导出失败", runtimeException);
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_105,
                "导出失败，请稍后重试或缩小查询范围");
        }
    }

    /**
     * Gate：结算日期闭区间、收退 Tab 与分页参数合法（BR-OP-FDT-001、009、013）。
     *
     * @param dateFrom        结算日起
     * @param dateTo          结算日止
     * @param tradeDirection  收退 Tab
     * @param pageNo          页码（导出时可 {@code null}）
     * @param pageSize        页大小（导出时可 {@code null}）
     */
    private void validateQueryWindow(
        String dateFrom,
        String dateTo,
        String tradeDirection,
        Integer pageNo,
        Integer pageSize) {
        // Gate：起止日期/时刻均须填写
        if (StringUtils.isEmpty(dateFrom) || StringUtils.isEmpty(dateTo)) {
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_102,
                "请选择有效的查询日期");
        }
        LocalDateTime from;
        LocalDateTime to;
        try {
            // 报表可传时刻；仅日历日时扩成 00:00:00～23:59:59
            from = ReportDateTimeWindowSupport.parseInclusiveStart(dateFrom);
            to = ReportDateTimeWindowSupport.parseInclusiveEnd(dateTo);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_102,
                "请选择有效的查询日期");
        }
        // Gate：止时刻不得早于起时刻
        if (to.isBefore(from)) {
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_102,
                "请选择有效的查询日期");
        }
        String resolvedTradeDirection = tradeDirection;
        if (StringUtils.isEmpty(resolvedTradeDirection)) {
            resolvedTradeDirection = OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL;
        }
        // Gate：收退 Tab 须在白名单内
        if (!this.isAllowedTradeDirection(resolvedTradeDirection.trim())) {
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_102,
                "请选择有效的查询日期");
        }
        if (pageNo != null && pageNo < 1) {
            // Gate：页码须 ≥1
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_102,
                "请选择有效的查询日期");
        }
        if (pageSize != null && pageSize < 1) {
            // Gate：页大小须 ≥1
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_102,
                "请选择有效的查询日期");
        }
    }

    /**
     * 按全量就诊块分页遍历并装配 DTO（导出与全量 summary 共用；**BR-OP-FDT-016**）。
     *
     * @param baseQuery 已预解析 scope 的查询
     * @return 全部就诊块 DTO
     */
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
        int pageSize = OutpatientFeeDetailConstants.ENCOUNTER_BLOCK_SCAN_PAGE_SIZE;
        while (true) {
            OutpatientFeeDetailPageQuery pageQuery = this.copyPageQuery(baseQuery);
            pageQuery.setPageNo(pageNo);
            pageQuery.setPageSize(pageSize);
            Page<OutpatientFeeDetailEncounterBlockQueryModel> blockPage =
                this.chargeItemQueryService.pageOutpatientFeeDetailEncounterBlocks(pageQuery);
            if (blockPage.isEmpty()) {
                // 已无更多就诊块，结束扫描
                break;
            }
            allBlocks.addAll(this.assembleEncounterBlockDtos(blockPage.getContent(), pageQuery));
            long total = blockPage.getTotalElements();
            if ((long) pageNo * pageSize >= total) {
                // 已覆盖全部就诊块
                break;
            }
            pageNo = pageNo + 1;
        }
        return allBlocks;
    }

    /**
     * 全量命中 summary：明细分页页脚仅售价合计；进价列角由独立 IF 异步回填。
     *
     * <p>售价：优先 Loader {@code resolvedSaleAmountTotal}，否则命中就诊全集 SQL SUM。
     * 进价：本方法刻意不跑 {@link FeeDetailMatchedScopePurchaseTotalSupport}（全窗历史进价是列角瓶颈）。</p>
     *
     * @param query 已预解析 scope 的查询
     * @return 全量金额 summary（purchaseAmountTotal 为 null 表示待刷）
     */
    private OutpatientFeeDetailAmountSummaryDto buildGlobalSummary(OutpatientFeeDetailPageQuery query) {
        // Pre：Payment 链路带符号售价代数和（退费负；禁止再走 entered_date 仅 SETTLED SUM）
        BigDecimal saleAmountTotal =
            this.chargeItemQueryService.summarizeOutpatientFeeDetailSaleAmountFromPreloadedScope(query);
        // Asm：仅售价；进价列角延后到 summarizeOutpatientFeeDetailPurchaseCorner
        return this.outpatientFeeDetailAssembler.assembleDetailPageFooterSaleSummary(
            saleAmountTotal, null);
    }

    /**
     * IF-OP-FDT-05：明细分页进价列角（命中 scope 历史进价合计；与 IF-02 筛选同口径）。
     *
     * <p>供前端在 page 成功后异步回填页脚「进价合计」，避免阻塞列表首屏。</p>
     *
     * @param request 与明细分页同筛选（忽略分页）
     * @return 进价合计（settle）；无命中为 0
     * @throws BusinessException 参数非法或查询失败
     */
    public BigDecimal summarizeOutpatientFeeDetailPurchaseCorner(PageOutpatientFeeDetailRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            OutpatientFeeDetailConstants.EX_OP_FDT_101,
            "费用明细查询失败，请稍后重试");
        // Gate：日期/收退合法；分页字段不参与列角，传占位避免校验拒
        this.validateQueryWindow(
            request.getDateFrom(),
            request.getDateTo(),
            request.getTradeDirection(),
            1,
            OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE);
        try {
            // Asm：Request → Query（与 IF-02 同筛选）
            OutpatientFeeDetailPageQuery query = this.outpatientFeeDetailMapper.toOutpatientFeeDetailPageQuery(request);
            this.applyEntererMatchToFeeDetailQuery(query);
            // Pre：与明细分页同口径预解析命中 scope（供进价 IN / 就诊窗装载）
            this.outpatientFeeDetailLoader.prepareOutpatientFeeDetailPageScopes(query);
            // Asm：仅历史进价代数和，禁就诊块/展示 enrich
            return this.feeDetailMatchedScopePurchaseTotalSupport.sumPurchaseAmountForMatchedScope(query);
        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("门诊费用明细进价列角数据不完整: {}", illegalArgumentException.getMessage());
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_103,
                "费用数据不完整，请联系管理员核对收费项目配置");
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("门诊费用明细进价列角失败", runtimeException);
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_104,
                "费用明细查询失败，请稍后重试");
        }
    }

    /**
     * 将域层就诊块批量 enrichment 并装配为 DTO（SDD §3.3 步骤 6～10、12～13）。
     *
     * @param domainBlocks 域层就诊块
     * @param query        同批查询（含 payment 上下文）
     * @return 就诊块 DTO 列表
     */
    private List<OutpatientFeeDetailEncounterBlockDto> assembleEncounterBlockDtos(
        List<OutpatientFeeDetailEncounterBlockQueryModel> domainBlocks,
        OutpatientFeeDetailPageQuery query) {
        if (domainBlocks == null || domainBlocks.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> encounterIdsForContext = new LinkedHashSet<>();
        for (OutpatientFeeDetailEncounterBlockQueryModel domainBlock : domainBlocks) {
            if (domainBlock != null && domainBlock.getEncounterId() != null) {
                encounterIdsForContext.add(domainBlock.getEncounterId());
            }
        }
        Map<Long, PatientSettlementContextRow> contextByEncounterId =
            this.reportPatientSettlementContextAssembler.mapContextsByEncounterIds(encounterIdsForContext);
        FeeDetailEnrichmentBundle bundle = this.loadEnrichmentBundle(domainBlocks, query);
        Map<String, String> unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        List<OutpatientFeeDetailEncounterBlockDto> result = new ArrayList<>(domainBlocks.size());
        for (OutpatientFeeDetailEncounterBlockQueryModel domainBlock : domainBlocks) {
            if (domainBlock == null || domainBlock.getEncounterId() == null) {
                // 跳过无就诊锚点的脏块
                continue;
            }
            OutpatientFeeDetailEncounterBlockDto blockDto = new OutpatientFeeDetailEncounterBlockDto();
            blockDto.setEncounterId(domainBlock.getEncounterId());
            Encounter encounter = bundle.encounterById.get(domainBlock.getEncounterId());
            if (encounter != null) {
                blockDto.setBusNo(encounter.getBusNo());
                if (encounter.getStartTime() != null) {
                    blockDto.setEncounterDate(encounter.getStartTime().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
                }
                if (encounter.getOrganizationId() != null) {
                    blockDto.setRegistrationDepartmentName(
                        bundle.organizationNameById.get(encounter.getOrganizationId()));
                }
            }
            Long patientId = domainBlock.getPatientId();
            if (patientId == null && encounter != null) {
                patientId = encounter.getPatientId();
            }
            if (patientId != null) {
                Patient patient = bundle.patientById.get(patientId);
                if (patient != null) {
                    blockDto.setPatientName(patient.getName());
                }
            }
            // Pre：医疗类别仍按就诊主诊；费用性质改由行级账单账户装配（下方回填块级首行）
            PatientSettlementContextRow settlementContext = contextByEncounterId.get(domainBlock.getEncounterId());
            if (settlementContext != null) {
                blockDto.setMedType(settlementContext.getMedType());
                blockDto.setMedTypeDisplay(settlementContext.getMedTypeDisplay());
            }
            List<OutpatientFeeDetailLineDto> lineDtos = new ArrayList<>();
            if (domainBlock.getLines() != null) {
                for (OutpatientFeeDetailChargeRowQueryModel row : domainBlock.getLines()) {
                    OutpatientFeeDetailLineDto lineDto = this.assembleSingleLine(row, bundle, unitLabelByCode);
                    if (lineDto != null) {
                        lineDtos.add(lineDto);
                    }
                }
            }
            // Asm：块级费用性质回落首行（同就诊可混挂多账户时以行级为准）
            if (!lineDtos.isEmpty()) {
                OutpatientFeeDetailLineDto firstLine = lineDtos.get(0);
                blockDto.setFeeNatureContractNo(firstLine.getFeeNatureContractNo());
                blockDto.setFeeNatureDisplay(firstLine.getFeeNatureDisplay());
            }
            // Asm：开立科室取块内首条账单 requesting_org_id
            if (domainBlock.getLines() != null && !domainBlock.getLines().isEmpty()) {
                for (OutpatientFeeDetailChargeRowQueryModel row : domainBlock.getLines()) {
                    if (row == null || row.getChargeItem() == null) {
                        continue;
                    }
                    Long requestingOrgId = row.getChargeItem().getRequestingOrgId();
                    if (requestingOrgId != null) {
                        blockDto.setRequestingOrgName(bundle.organizationNameById.get(requestingOrgId));
                        break;
                    }
                }
            }
            blockDto.setLines(lineDtos);
            // Asm：块内就诊小计（SDD §3.3 步骤 13）
            blockDto.setSubtotal(this.outpatientFeeDetailAssembler.assembleEncounterSubtotal(lineDtos));
            result.add(blockDto);
        }
        return result;
    }

    /**
     * 装配单条明细行（含产品/人员/单位 enrichment；SDD §3.3 步骤 12）。
     *
     * @param row             域层候选行
     * @param bundle          批量 enrichment 上下文
     * @param unitLabelByCode 单位码 → 展示名
     * @return 明细行 DTO；缺 chargeItem 时 {@code null}
     */
    private OutpatientFeeDetailLineDto assembleSingleLine(
        OutpatientFeeDetailChargeRowQueryModel row,
        FeeDetailEnrichmentBundle bundle,
        Map<String, String> unitLabelByCode) {
        if (row == null || row.getChargeItem() == null) {
            return null;
        }
        Long chargeItemId = row.getChargeItem().getId();
        OutpatientFeeDetailPaymentContextQueryModel paymentCtx = row.getPaymentContext();
        ProductDefinitionDisplayDto product = this.resolveProduct(row, bundle);
        if (product == null) {
            // Gate：缺 product 锚点时整单失败（BR-OP-FDT-015）
            throw new IllegalArgumentException("missing product definition for charge item " + chargeItemId);
        }
        String finTypeCode = null;
        String finTypeDisplay = null;
        if (chargeItemId != null && bundle.finTypeCodeByChargeItemId != null) {
            finTypeCode = bundle.finTypeCodeByChargeItemId.get(chargeItemId);
        }
        if (StringUtils.isNotEmpty(finTypeCode) && bundle.finTypeLabelByCode != null) {
            finTypeDisplay = bundle.finTypeLabelByCode.get(finTypeCode.trim());
        }
        if (StringUtils.isEmpty(finTypeDisplay) && StringUtils.isNotEmpty(finTypeCode)) {
            finTypeDisplay = finTypeCode.trim();
        }
        OutpatientFeeDetailLineDto lineDto =
            this.outpatientFeeDetailAssembler.assembleLineAmounts(
                row, paymentCtx, product, finTypeCode, finTypeDisplay);
        if (lineDto == null) {
            return null;
        }
        String unitCode = row.getChargeItem().getQuantityUnit();
        if (StringUtils.isNotEmpty(unitCode)) {
            String unitDisplay = unitLabelByCode.get(unitCode.trim());
            if (StringUtils.isNotEmpty(unitDisplay)) {
                lineDto.setQuantityUnitDisplay(unitDisplay);
            } else {
                // 字典未命中时回落持久化 code
                lineDto.setQuantityUnitDisplay(unitCode.trim());
            }
        }
        Long orderingDoctorId = null;
        if (chargeItemId != null && bundle.orderingDoctorByChargeItemId != null) {
            orderingDoctorId = bundle.orderingDoctorByChargeItemId.get(chargeItemId);
        }
        if (orderingDoctorId != null) {
            lineDto.setOrderingDoctorId(orderingDoctorId);
            lineDto.setOrderingDoctorName(bundle.practitionerNameById.get(orderingDoctorId));
        }
        // Asm：执行人跟 Procedure / DeviceDispense（不再读收费行操作人戳）
        Long performerId = null;
        if (chargeItemId != null && bundle.performerByChargeItemId != null) {
            performerId = bundle.performerByChargeItemId.get(chargeItemId);
        }
        if (performerId != null) {
            lineDto.setPerformerId(performerId);
            lineDto.setPerformerName(bundle.practitionerNameById.get(performerId));
        }
        if (lineDto.getCashierId() != null) {
            lineDto.setCashierName(bundle.practitionerNameById.get(lineDto.getCashierId()));
        }
        // Asm：费用性质跟收费行挂账账户（转自费后为自费合同名）
        Long accountId = row.getChargeItem().getAccountId();
        if (accountId != null && bundle.feeNatureByAccountId != null) {
            PatientSettlementContextRow feeNature = bundle.feeNatureByAccountId.get(accountId);
            if (feeNature != null) {
                lineDto.setFeeNatureContractNo(feeNature.getFeeNatureContractNo());
                lineDto.setFeeNatureDisplay(feeNature.getFeeNatureDisplay());
            }
        }
        // Asm：回填历史进价（与售价同号；缺锚点置 0）
        this.applyPurchasePrices(lineDto, row.getChargeItem(), bundle);
        return lineDto;
    }

    /**
     * 将批量进价回填到明细行（退费行与售价同号；诊疗无进价则留空）。
     *
     * <p>单价与收费数量单位对齐（包装操作单位展示包装价；拆零操作单位才折展示拆零价）；行额为包装锚合计（已在 bundle 中算好）。</p>
     *
     * @param lineDto      已装配售价的明细行
     * @param chargeItem   收费明细实体
     * @param bundle       enrichment 上下文
     */
    private void applyPurchasePrices(
        OutpatientFeeDetailLineDto lineDto,
        ChargeItem chargeItem,
        FeeDetailEnrichmentBundle bundle) {
        BigDecimal signMultiplier = BigDecimal.ONE;
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(lineDto.getTradeDirection())) {
            signMultiplier = new BigDecimal("-1");
        }
        Long chargeItemId = null;
        if (chargeItem != null) {
            chargeItemId = chargeItem.getId();
        }
        BigDecimal purchaseUnit = null;
        BigDecimal purchaseAmount = null;
        if (chargeItemId != null) {
            purchaseUnit = bundle.purchaseUnitByChargeItemId.get(chargeItemId);
            purchaseAmount = bundle.purchaseAmountByChargeItemId.get(chargeItemId);
        }
        // 诊疗/挂号等无进价：不编 0，前端展示为空
        if (purchaseUnit == null && purchaseAmount == null) {
            lineDto.setPurchaseUnitPrice(null);
            lineDto.setPurchaseAmount(null);
            return;
        }
        BigDecimal ratio = null;
        if (chargeItemId != null && bundle.packageUnitRatioByChargeItemId != null) {
            ratio = bundle.packageUnitRatioByChargeItemId.get(chargeItemId);
        }
        ProductDefinitionDisplayDto product = null;
        if (chargeItem != null && bundle.productByCompositeKey != null) {
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup != null
                && lookup.getProductTableName() != null
                && lookup.getProductDefinitionId() != null) {
                String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    lookup.getProductTableName(), lookup.getProductDefinitionId());
                if (compositeKey != null) {
                    product = bundle.productByCompositeKey.get(compositeKey);
                }
            }
        }
        BigDecimal displayUnit = this.chargeItemPackageUnitRatioSupport.toChargeAlignedPurchaseDisplayUnitPrice(
            purchaseUnit, ratio, chargeItem, product);
        if (displayUnit != null) {
            lineDto.setPurchaseUnitPrice(
                MonetaryAmountSupport.applySignAtCalcScale(displayUnit, signMultiplier));
        } else {
            lineDto.setPurchaseUnitPrice(null);
        }
        if (purchaseAmount != null) {
            lineDto.setPurchaseAmount(
                MonetaryAmountSupport.applySignAtCalcScale(purchaseAmount, signMultiplier));
        } else {
            lineDto.setPurchaseAmount(null);
        }
    }

    /**
     * 批量加载就诊块 enrichment 上下文（SDD §3.3 步骤 6～10；避免 N+1）。
     *
     * @param domainBlocks 当前批域层就诊块
     * @param query        同批查询
     * @return enrichment 上下文
     */
    private FeeDetailEnrichmentBundle loadEnrichmentBundle(
        List<OutpatientFeeDetailEncounterBlockQueryModel> domainBlocks,
        OutpatientFeeDetailPageQuery query) {
        FeeDetailEnrichmentBundle bundle = new FeeDetailEnrichmentBundle();
        List<ChargeItem> chargeItemsForFinType = new ArrayList<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        LinkedHashSet<ProductDefinitionBatchLookup> productLookups = new LinkedHashSet<>();
        for (OutpatientFeeDetailEncounterBlockQueryModel block : domainBlocks) {
            if (block == null) {
                continue;
            }
            if (block.getEncounterId() != null) {
                encounterIds.add(block.getEncounterId());
            }
            if (block.getPatientId() != null) {
                patientIds.add(block.getPatientId());
            }
            if (block.getLines() == null) {
                continue;
            }
            for (OutpatientFeeDetailChargeRowQueryModel row : block.getLines()) {
                if (row == null || row.getChargeItem() == null) {
                    continue;
                }
                chargeItemsForFinType.add(row.getChargeItem());
                if (row.getChargeItem().getAccountId() != null) {
                    accountIds.add(row.getChargeItem().getAccountId());
                }
                if (row.getChargeItem().getPatientId() != null) {
                    patientIds.add(row.getChargeItem().getPatientId());
                }
                // 开立科室名：纳入 organization 批量解析
                if (row.getChargeItem().getRequestingOrgId() != null) {
                    bundle.organizationIds.add(row.getChargeItem().getRequestingOrgId());
                }
                // 收费人：已结/退费中/已退的 enterer_id（结算写入；合成上下文，不查 Payment）
                if (row.getChargeItem().getEntererId() != null) {
                    String statusCode = row.getChargeItem().getStatusEnum();
                    boolean settledRefundingOrRefunded =
                        ChargeItemStatus.SETTLED.getCode().equals(statusCode)
                            || ChargeItemStatus.REFUNDING.getCode().equals(statusCode)
                            || ChargeItemStatus.REFUNDED.getCode().equals(statusCode);
                    if (settledRefundingOrRefunded) {
                        practitionerIds.add(row.getChargeItem().getEntererId());
                    }
                }
                if (row.getPaymentContext() != null && row.getPaymentContext().getCashierId() != null) {
                    practitionerIds.add(row.getPaymentContext().getCashierId());
                }
                ProductDefinitionBatchLookup lookup = this.buildProductLookup(row);
                if (lookup != null) {
                    productLookups.add(lookup);
                }
            }
        }
        // Pre：开单医生仅医生开立 Request.requester_id（划价等不回填）
        bundle.orderingDoctorByChargeItemId =
            this.chargeItemOrderingDoctorFromRequestSupport.mapOrderingDoctorIdByChargeItemId(chargeItemsForFinType);
        for (Long orderingDoctorId : bundle.orderingDoctorByChargeItemId.values()) {
            if (orderingDoctorId != null) {
                practitionerIds.add(orderingDoctorId);
            }
        }
        // Pre：执行人跟 Procedure / DeviceDispense
        bundle.performerByChargeItemId =
            this.chargeItemActivityOperatorResolver.mapOperatorPractitionerIdByChargeItemId(chargeItemsForFinType);
        for (Long performerId : bundle.performerByChargeItemId.values()) {
            if (performerId != null) {
                practitionerIds.add(performerId);
            }
        }
        // Pre：SDD §3.3 步骤 7 — EncounterQueryService#listEncounterEntitiesByIds
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        for (Encounter encounter : encounters) {
            if (encounter != null && encounter.getId() != null) {
                bundle.encounterById.put(encounter.getId(), encounter);
                if (encounter.getPatientId() != null) {
                    patientIds.add(encounter.getPatientId());
                }
                if (encounter.getOrganizationId() != null) {
                    bundle.organizationIds.add(encounter.getOrganizationId());
                }
            }
        }
        // Pre：SDD §3.3 步骤 8 — PatientQueryService#loadPatientEntityMapByIds
        bundle.patientById = this.patientQueryService.loadPatientEntityMapByIds(patientIds);
        bundle.organizationNameById =
            this.organizationQueryService.findOrganizationNamesByIds(bundle.organizationIds);
        // Pre：SDD §3.3 步骤 9 — PractitionerQueryService#findAllByIds（enterer 同为医务人员主键）
        bundle.practitionerNameById = this.loadPractitionerNameMap(practitionerIds);
        List<ProductDefinitionBatchLookup> lookupList = new ArrayList<>(productLookups);
        // Pre：SDD §3.3 步骤 10 — ProductDefinitionDisplayTerminologyAssembler#loadAsMap
        bundle.productByCompositeKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookupList);
        // Pre：定价行 fin_type 批量解析，供细分类展示与日结口径对齐
        bundle.finTypeCodeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItemsForFinType);
        bundle.finTypeLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
        // Pre：历史进价批量解析（禁当前 ACTIVE 目录价）；单价一次、行额按包装锚，禁止双倍定价 IO
        bundle.purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(
                chargeItemsForFinType);
        bundle.packageUnitRatioByChargeItemId =
            this.chargeItemPackageUnitRatioSupport.mapPackageUnitRatioByChargeItemId(
                chargeItemsForFinType, bundle.productByCompositeKey);
        // 必须传 product Map：包装操作单位禁止走「假定拆零量」兼容分支（#4M-OP）
        bundle.purchaseAmountByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeItemsForFinType,
                bundle.purchaseUnitByChargeItemId,
                bundle.packageUnitRatioByChargeItemId,
                bundle.productByCompositeKey);
        // Pre：账单挂账账户 → 费用性质（与收银 IF-OPC-03 同口径）
        bundle.feeNatureByAccountId =
            this.reportPatientSettlementContextAssembler.mapFeeNatureContextsByAccountIds(accountIds);
        return bundle;
    }

    /**
     * 解析单行产品定义（复合键 {@link ProductDefinitionKeyUtil#buildCompositeKey}）。
     *
     * @param row    域层候选行
     * @param bundle enrichment 上下文
     * @return 产品展示 DTO；缺锚点时 {@code null}
     */
    private ProductDefinitionDisplayDto resolveProduct(
        OutpatientFeeDetailChargeRowQueryModel row,
        FeeDetailEnrichmentBundle bundle) {
        if (row == null || row.getChargeItem() == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = ChargeItemProductLookupHelper.buildLookupFromChargeItem(row.getChargeItem());
        if (lookup == null) {
            return null;
        }
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(),
            lookup.getProductDefinitionId());
        if (compositeKey == null) {
            return null;
        }
        ProductDefinitionDisplayDto product = bundle.productByCompositeKey.get(compositeKey);
        if (product != null) {
            return product;
        }
        // 挂号费批量未命中时单条回落 adm_healthcare_service，避免整单 EX-OP-FDT-103
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(lookup.getProductTableName())
            && lookup.getProductDefinitionId() != null) {
            Map<Long, HealthcareService> healthcareById = this.healthcareServiceQueryService.loadHealthcareServiceMapByIds(
                List.of(lookup.getProductDefinitionId()));
            HealthcareService healthcareService = healthcareById.get(lookup.getProductDefinitionId());
            return ProductDefinitionDisplaySupport.fromHealthcareService(healthcareService);
        }
        return null;
    }

    /**
     * 由 ChargeItem 产品锚点构造批量 lookup 项（含挂号费回落）。
     *
     * @param row 域层候选行
     * @return lookup；缺锚点时 {@code null}
     */
    private ProductDefinitionBatchLookup buildProductLookup(OutpatientFeeDetailChargeRowQueryModel row) {
        if (row == null || row.getChargeItem() == null) {
            return null;
        }
        return ChargeItemProductLookupHelper.buildLookupFromChargeItem(row.getChargeItem());
    }

    /**
     * 批量解析医务人员展示名（enterer_id / practitioner 主键，**BR-OPC-016**）。
     *
     * @param practitionerIds 医务人员主键集合
     * @return id → 姓名
     */
    private Map<Long, String> loadPractitionerNameMap(Set<Long> practitionerIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return nameById;
        }
        // Pre：仅按医务人员主键批量取姓名（与 mapDisplayNameByEntererIds 同口径）
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null && StringUtils.isNotEmpty(practitioner.getName())) {
                nameById.put(practitioner.getId(), practitioner.getName());
            }
        }
        return nameById;
    }

    /**
     * 复制 Query 分页窗口与 scope 字段（全量扫描时复用同一筛选条件）。
     *
     * @param source 源 Query
     * @return 新 Query 实例
     */
    private OutpatientFeeDetailPageQuery copyPageQuery(OutpatientFeeDetailPageQuery source) {
        OutpatientFeeDetailPageQuery target = new OutpatientFeeDetailPageQuery();
        target.setBillDateFrom(source.getBillDateFrom());
        target.setBillDateTo(source.getBillDateTo());
        target.setRegistrationDepartmentId(source.getRegistrationDepartmentId());
        target.setRequestingOrgId(source.getRequestingOrgId());
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
     * 构造收退 Tab 固定三选项（**BR-OP-FDT-009**）。
     *
     * @return ALL/CHARGE/REFUND 选项
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
     * 构造费用大类四选项（**BR-OP-FDT-006**）。
     *
     * @return 药品/诊疗/耗材/挂号选项
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
     * 构造挂号科室下拉（OrganizationQueryService#listOrganizationsForOutpatientRegistration）。
     *
     * @return 科室 OptionDto 列表
     */
    private List<OptionDto> buildRegistrationDepartmentOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<Organization> organizations = this.organizationQueryService.listOrganizationsForOutpatientRegistration();
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                // 跳过无有效主键的组织行
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
     * 构造收费员下拉（PractitionerQueryService#pageActivePractitionersForPosition，须绑定系统用户）。
     *
     * @return 收费员 OptionDto 列表
     */
    private List<OptionDto> buildCashierOptions() {
        Page<Practitioner> page = this.practitionerQueryService.pageActivePractitionersForPosition(
            null,
            null,
            PageRequest.of(0, OutpatientFeeDetailConstants.CASHIER_OPTION_PAGE_SIZE),
            true);
        List<Practitioner> eligiblePractitioners =
            this.practitionerCashierOptionProvider.filterWithActiveBoundSystemUser(page.getContent());
        return this.practitionerCashierOptionProvider.toCashierOptions(eligiblePractitioners);
    }

    /**
     * 构造单个 OptionDto。
     *
     * @param value 选项值（code 或 id 字符串）
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
     * 收退 Tab 白名单校验（**BR-OP-FDT-009**）。
     *
     * @param tradeDirection Tab code
     * @return 是否允许
     */
    private boolean isAllowedTradeDirection(String tradeDirection) {
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL.equals(tradeDirection)) {
            return true;
        }
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_CHARGE.equals(tradeDirection)) {
            return true;
        }
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(tradeDirection)) {
            return true;
        }
        // 非 ALL/CHARGE/REFUND 一律拒绝
        return false;
    }

    /**
     * 收费人筛选写入 payment {@code enterer_id} 白名单（仅 Practitioner 主键，**BR-OPC-016**）。
     *
     * @param query 费用明细分页/导出 Query
     */
    private void applyEntererMatchToFeeDetailQuery(OutpatientFeeDetailPageQuery query) {
        if (query == null || query.getCashierId() == null) {
            return;
        }
        query.setEntererMatchIds(
            this.paymentEntererMatchResolver.resolveEntererMatchIds(query.getCashierId()));
    }

    /**
     * 单次查询批量 enrichment 上下文容器（步骤 6～10 中间态）。
     */
    private static final class FeeDetailEnrichmentBundle {

        /** encounterId → 就诊实体。 */
        private Map<Long, Encounter> encounterById = new HashMap<>();

        /** patientId → 患者实体。 */
        private Map<Long, Patient> patientById = Map.of();

        /** 待解析名称的 organizationId 集合。 */
        private Set<Long> organizationIds = new HashSet<>();

        /** organizationId → 科室名称。 */
        private Map<Long, String> organizationNameById = Map.of();

        /** practitionerId → 姓名（开单医生 + 收费人）。 */
        private Map<Long, String> practitionerNameById = Map.of();

        /** chargeItemId → 开单医生（Request.requester_id）。 */
        private Map<Long, Long> orderingDoctorByChargeItemId = Map.of();

        /** chargeItemId → 活动操作人（Procedure / DeviceDispense）。 */
        private Map<Long, Long> performerByChargeItemId = Map.of();

        /** 产品复合键 → 展示 DTO。 */
        private Map<String, ProductDefinitionDisplayDto> productByCompositeKey = Map.of();

        /** chargeItemId → fin_type_code。 */
        private Map<Long, String> finTypeCodeByChargeItemId = Map.of();

        /** fin_type_code → 展示名。 */
        private Map<String, String> finTypeLabelByCode = Map.of();

        /** chargeItemId → 进价单价（未带收退符号）。 */
        private Map<Long, BigDecimal> purchaseUnitByChargeItemId = Map.of();

        /** chargeItemId → 进价总价（未带收退符号）。 */
        private Map<Long, BigDecimal> purchaseAmountByChargeItemId = Map.of();

        /** chargeItemId → 包装→拆零倍数（行进价合计与展示单价用）。 */
        private Map<Long, BigDecimal> packageUnitRatioByChargeItemId = Map.of();

        /** accountId → 费用性质（ChargeItem.account_id 真源）。 */
        private Map<Long, PatientSettlementContextRow> feeNatureByAccountId = Map.of();
    }
}
