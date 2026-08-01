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
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.basic.administration.support.PractitionerCashierOptionProvider;
import com.openhis.health.application.shared.financial.PaymentEntererMatchResolver;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.PatientSettlementContextRow;
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

    /** payment enterer_id 匹配（Practitioner + 用户主键存量兼容）。 */
    private final PaymentEntererMatchResolver paymentEntererMatchResolver;

    /** 术语下拉与单位译文。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 收费员下拉：排除绑定用户已删/未启用的 practitioner。 */
    private final PractitionerCashierOptionProvider practitionerCashierOptionProvider;

    /** 跨聚合 scope 预解析。 */
    private final OutpatientFeeDetailLoader outpatientFeeDetailLoader;

    /** 定价行 fin_type 解析（与日结口径一致）。 */
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 历史进价解析（BR-PRICE-SRC-001）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 金额列装配。 */
    private final OutpatientFeeDetailAssembler outpatientFeeDetailAssembler;

    /** Request/Response 映射。 */
    private final OutpatientFeeDetailMapper outpatientFeeDetailMapper;

    /** 费用性质 + medType 报表上下文装配。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

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
            // Pre：SDD §3.3 步骤 4 — scope 预解析
            this.outpatientFeeDetailLoader.prepareOutpatientFeeDetailQueryScopes(query);
            // Pre：SDD §3.3 步骤 5 — 当前页就诊块分页
            Page<OutpatientFeeDetailEncounterBlockQueryModel> blockPage =
                this.chargeItemQueryService.pageOutpatientFeeDetailEncounterBlocks(query);
            // Pre：SDD §3.3 步骤 6～10 + Asm 步骤 12～13 — enrichment 与行/块装配
            List<OutpatientFeeDetailEncounterBlockDto> blockDtos =
                this.assembleEncounterBlockDtos(blockPage.getContent(), query);
            // Pre+Asm：全量 summary 走候选行轻量加总（禁止为页脚 enrichment 全部就诊块）
            OutpatientFeeDetailAmountSummaryDto summary = this.buildGlobalSummary(query);
            // Asm：SDD §3.3 步骤 14 — 分页响应
            return this.outpatientFeeDetailMapper.toPageOutpatientFeeDetailResponse(
                blockDtos,
                blockPage.getTotalElements(),
                summary,
                request);
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
            // Asm：SDD §3.4 步骤 7 — AnnotatedExcelExporter
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                OutpatientFeeDetailConstants.EXPORT_SHEET_NAME,
                exportRows,
                OutpatientFeeDetailExportRowDto.class);
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
        // Gate：起止日期均须填写
        if (StringUtils.isEmpty(dateFrom) || StringUtils.isEmpty(dateTo)) {
            throw new BusinessException(
                OutpatientFeeDetailConstants.EX_OP_FDT_102,
                "请选择有效的查询日期");
        }
        LocalDate from = LocalDate.parse(dateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        // Gate：止日不得早于起日
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
     * 全量命中 summary：候选行轻量加总（售价/实收/医保等），禁止为页脚全量 enrichment 就诊块。
     *
     * <p>明细分页页脚<strong>不</strong>跑全量历史进价（定价/批号/调价 IO 随命中行线性放大）；
     * {@code purchaseAmountTotal} 置空由前端展示「—」。行上进价仍由当前页 enrichment 解析。
     * 汇总 Tab / 导出进价合计走 fin_type 汇总路径，不受本方法约束。</p>
     *
     * @param query 已预解析 scope 的查询
     * @return 全量金额 summary（进价合计为 {@code null}）
     */
    private OutpatientFeeDetailAmountSummaryDto buildGlobalSummary(OutpatientFeeDetailPageQuery query) {
        // Pre：复用同请求候选行（含 payment 快照分母）
        List<OutpatientFeeDetailChargeRowQueryModel> rows =
            this.chargeItemQueryService.listOutpatientFeeDetailChargeRows(query);
        // Asm：页脚售价/实收/医保等轻量加总（BR-OP-FDT-013）；进价 Map 空，避免全量历史进价
        OutpatientFeeDetailAmountSummaryDto summary =
            this.outpatientFeeDetailAssembler.assembleGlobalSummaryFromChargeRows(rows, Map.of());
        // 显式置空：与「未算」区分，禁止落成 0.00 误导页脚
        summary.setPurchaseAmountTotal(null);
        return summary;
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
            PatientSettlementContextRow settlementContext = contextByEncounterId.get(domainBlock.getEncounterId());
            if (settlementContext != null) {
                blockDto.setFeeNatureContractNo(settlementContext.getFeeNatureContractNo());
                blockDto.setFeeNatureDisplay(settlementContext.getFeeNatureDisplay());
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
        Long orderingDoctorId = row.getChargeItem().getEntererId();
        if (orderingDoctorId != null) {
            lineDto.setOrderingDoctorName(bundle.practitionerNameById.get(orderingDoctorId));
        }
        if (lineDto.getCashierId() != null) {
            lineDto.setCashierName(bundle.practitionerNameById.get(lineDto.getCashierId()));
        }
        // Asm：回填历史进价（与售价同号；缺锚点置 0）
        this.applyPurchasePrices(lineDto, chargeItemId, bundle);
        return lineDto;
    }

    /**
     * 将批量进价回填到明细行（退费行与售价同号）。
     *
     * @param lineDto      已装配售价/实收的明细行
     * @param chargeItemId 收费明细主键
     * @param bundle       enrichment 上下文
     */
    private void applyPurchasePrices(
        OutpatientFeeDetailLineDto lineDto,
        Long chargeItemId,
        FeeDetailEnrichmentBundle bundle) {
        BigDecimal signMultiplier = BigDecimal.ONE;
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(lineDto.getTradeDirection())) {
            signMultiplier = new BigDecimal("-1");
        }
        BigDecimal purchaseUnit = null;
        BigDecimal purchaseAmount = null;
        if (chargeItemId != null) {
            purchaseUnit = bundle.purchaseUnitByChargeItemId.get(chargeItemId);
            purchaseAmount = bundle.purchaseAmountByChargeItemId.get(chargeItemId);
        }
        if (purchaseUnit == null) {
            purchaseUnit = BigDecimal.ZERO;
        }
        if (purchaseAmount == null) {
            purchaseAmount = BigDecimal.ZERO;
        }
        lineDto.setPurchaseUnitPrice(
            MonetaryAmountSupport.applySignAtCalcScale(purchaseUnit, signMultiplier));
        lineDto.setPurchaseAmount(
            MonetaryAmountSupport.applySignAtCalcScale(purchaseAmount, signMultiplier));
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
                if (row.getChargeItem().getPatientId() != null) {
                    patientIds.add(row.getChargeItem().getPatientId());
                }
                if (row.getChargeItem().getEntererId() != null) {
                    practitionerIds.add(row.getChargeItem().getEntererId());
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
        // Pre：SDD §3.3 步骤 9 — PractitionerQueryService#findAllByIds / mapDisplayNameByEntererIds
        bundle.practitionerNameById = this.loadPractitionerNameMap(practitionerIds);
        List<ProductDefinitionBatchLookup> lookupList = new ArrayList<>(productLookups);
        // Pre：SDD §3.3 步骤 10 — ProductDefinitionDisplayTerminologyAssembler#loadAsMap
        bundle.productByCompositeKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookupList);
        // Pre：定价行 fin_type 批量解析，供细分类展示与日结口径对齐
        bundle.finTypeCodeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItemsForFinType);
        bundle.finTypeLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
        // Pre：历史进价批量解析（禁当前 ACTIVE 目录价）；单价一次、行额由单价推导，禁止双倍定价 IO
        bundle.purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(
                chargeItemsForFinType);
        bundle.purchaseAmountByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeItemsForFinType, bundle.purchaseUnitByChargeItemId);
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
     * 批量解析医务人员展示名（含 entererId 存量兼容）。
     *
     * @param practitionerIds 医务人员/enterer 主键集合
     * @return id → 姓名
     */
    private Map<Long, String> loadPractitionerNameMap(Set<Long> practitionerIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return nameById;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null && StringUtils.isNotEmpty(practitioner.getName())) {
                nameById.put(practitioner.getId(), practitioner.getName());
            }
        }
        Map<Long, String> entererNames = this.practitionerQueryService.mapDisplayNameByEntererIds(practitionerIds);
        for (Map.Entry<Long, String> entry : entererNames.entrySet()) {
            if (entry.getKey() != null && StringUtils.isNotEmpty(entry.getValue())) {
                nameById.putIfAbsent(entry.getKey(), entry.getValue());
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
     * 收费人筛选写入 payment {@code enterer_id} 白名单（Practitioner + 用户主键存量兼容）。
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

        /** practitionerId/entererId → 姓名。 */
        private Map<Long, String> practitionerNameById = Map.of();

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
    }
}
