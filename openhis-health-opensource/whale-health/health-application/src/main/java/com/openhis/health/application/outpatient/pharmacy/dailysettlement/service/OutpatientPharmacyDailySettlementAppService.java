/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dailysettlement.service;

import com.openhis.health.application.shared.practitioner.PractitionerRoleScopeLoader;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.mapper.OutpatientPharmacyDailySettlementMapper;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.LoadOutpatientPharmacyDailySettlementPageInitRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.PageOutpatientPharmacyDailySettlementHistoryRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.PreviewOutpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.SubmitOutpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.request.WithdrawOutpatientPharmacyDailySettlementRequest;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.LoadOutpatientPharmacyDailySettlementPageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.OutpatientPharmacyDailySettlementHistoryItemDto;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.PageOutpatientPharmacyDailySettlementHistoryResponse;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.PreviewOutpatientPharmacyDailySettlementResponse;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.SubmitOutpatientPharmacyDailySettlementResponse;
import com.openhis.health.application.outpatient.pharmacy.dailysettlement.response.WithdrawOutpatientPharmacyDailySettlementResponse;
import com.openhis.health.application.shared.financial.DailySettlementOrgLocationScopeSupport;
import com.openhis.health.application.shared.financial.cashiersettlement.CashierSettlementPartyNamesEnricher;
import com.openhis.health.application.shared.pharmacydailysettlement.PharmacyDailySettlementCategoryRowDto;
import com.openhis.health.application.shared.pharmacydailysettlement.PharmacyDailySettlementDispenseItemDto;
import com.openhis.health.application.shared.pharmacydailysettlement.PharmacyDailySettlementProductRowDto;
import com.openhis.health.application.shared.pharmacydailysettlement.PharmacyDailySettlementPreviewAssembler;
import com.openhis.health.application.shared.pharmacydailysettlement.PharmacyDailySettlementPricingSupport;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.command.WithdrawCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.entity.CashierSettlement;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementHistoryItemQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.service.CashierSettlementDomainService;
import com.openhis.health.domain.financial.cashiersettlement.service.CashierSettlementQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.PreviewPharmacyCashierSettlementQuery;
import com.openhis.health.domain.medication.medicationdispense.queries.model.UnsettledDispensePreviewItemQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientPharmacyDailySettlementConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.CashierSettlementStatus;
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊药房日结应用服务（AS-OP-PHA-006，IF-OP-PDC-01～05）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、药师岗提交/撤回、日期窗、单药房必填、岗位管辖药房（{@link CurrentManagedLocationIdsResolver}）、漏交硬栏。</li>
 *   <li>Pre：未挂载发药候选、窗内发药人自动带出、主区汇总读侧。</li>
 *   <li>Cmd：提交/撤回交账并挂载/清挂载 dispense。</li>
 *   <li>Asm：经 {@link OutpatientPharmacyDailySettlementMapper} 与 {@link PharmacyDailySettlementPreviewAssembler} 装配；
 *       历史按 {@code submitted_at} 分页，且仅管辖药房。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/outpatientPharmacyDailySettlement/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientPharmacyDailySettlementAppService {

    /** 写路径操作人门控。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 发药候选读侧（B.6.4）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 交账写侧。 */
    private final CashierSettlementDomainService cashierSettlementDomainService;

    /** 交账头读侧。 */
    private final CashierSettlementQueryService cashierSettlementQueryService;

    /** 交账历史展示名 enrich。 */
    private final CashierSettlementPartyNamesEnricher cashierSettlementPartyNamesEnricher;

    /** 药房 location 读侧。 */
    private final LocationQueryService locationQueryService;

    /** 岗位管辖库房解析（药房下拉 / 查询·提交·历史 Gate）。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;

    /** 发药人主档读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 当前登录医务人员。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 岗位人员预解析（提交/撤回须药师岗）。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** Request/Response 映射。 */
    private final OutpatientPharmacyDailySettlementMapper outpatientPharmacyDailySettlementMapper;

    /** 主区预览装配（大类/品规/明细）。 */
    private final PharmacyDailySettlementPreviewAssembler pharmacyDailySettlementPreviewAssembler;

    /** 进/售读价与售价合计（头表 total_income 同口径）。 */
    private final PharmacyDailySettlementPricingSupport pharmacyDailySettlementPricingSupport;

    /** 漏交硬栏文案。 */
    private final DailySettlementOrgLocationScopeSupport dailySettlementOrgLocationScopeSupport;

    /** medication_category_code 术语读侧。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * IF-OP-PDC-01：门诊药房日结页初始化。
     *
     * @param request 可选 orgId 收窄药房下拉
     * @return 默认时间窗与药房选项
     * @throws BusinessException 未登录时抛出 EX-OP-PDC-101
     */
    public LoadOutpatientPharmacyDailySettlementPageInitResponse loadOutpatientPharmacyDailySettlementPageInit(
        LoadOutpatientPharmacyDailySettlementPageInitRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_101,
            "页面初始化失败，请稍后重试");
        LocalDate today = LocalDate.now();
        LocalDateTime defaultFrom = today.atStartOfDay();
        LocalDateTime defaultTo = LocalDateTime.now();
        // Pre：药房下拉 = 启用药房 ∩ 岗位管辖（有 orgId 时先按科室再管辖）
        List<Location> pharmacies = this.resolvePharmacyLocations(request);
        List<OptionDto> pharmacyOptions = this.pharmacyDailySettlementPreviewAssembler.toPharmacyOptions(pharmacies);
        // Pre：药师岗全员下拉（与收费日结财务岗同构，可多选软筛）
        List<OptionDto> practitionerOptions = this.buildPharmacistOptions();
        // Pre：重点药类型 + 药品性质下拉（分框；品规销售排名筛选，与 AS-RPT-011 同构）
        List<OptionDto> keyDrugTypeOptions =
            this.pharmacyDailySettlementPreviewAssembler.buildKeyDrugTypeOptions();
        List<OptionDto> medicinePropertyOptions =
            this.pharmacyDailySettlementPreviewAssembler.buildMedicinePropertyOptions();
        String defaultDateFrom = defaultFrom.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
        String defaultDateTo = defaultTo.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
        // Asm：只读初始化
        return this.outpatientPharmacyDailySettlementMapper.toLoadPageInitResponse(
            defaultDateFrom,
            defaultDateTo,
            pharmacyOptions,
            practitionerOptions,
            keyDrugTypeOptions,
            medicinePropertyOptions);
    }

    /**
     * IF-OP-PDC-02：门诊药房日结查询。
     *
     * @param request 时间窗与单药房
     * @return 大类/品规主区、待交明细与漏交提醒
     * @throws BusinessException 参数非法或查询失败
     */
    public PreviewOutpatientPharmacyDailySettlementResponse previewOutpatientPharmacyDailySettlement(
        PreviewOutpatientPharmacyDailySettlementRequest request) {
        try {
            // Gate：未登录拒绝查询
            this.currentOperatorResolver.requireOperatorId(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_103,
                "查询失败，请稍后重试");
            this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
            // Gate：药房必填且须在岗位管辖范围内（BR-OP-PDC-009）
            this.assertManagedPharmacyLocation(request.getLocationId());
            PreviewPharmacyCashierSettlementQuery previewQuery = this.outpatientPharmacyDailySettlementMapper.toPreviewQuery(
                request,
                CashierSettlementType.OP_PHARMACY.getCode());
            String typeEnum = CashierSettlementType.OP_PHARMACY.getCode();
            // Pre：漏交扫描（MIN 聚合，不拉历史全量实体）
            LocalDateTime gapHardBlockEarliestAt =
                this.medicationDispenseQueryService.findEarliestUnsettledDispenseTimeBeforeWindow(
                    typeEnum,
                    request.getLocationId(),
                    request.getDateFrom(),
                    request.getDateTo());
            long gapSoftWarnCount =
                this.medicationDispenseQueryService.countUnsettledDispensesAfterWindow(
                    typeEnum,
                    request.getLocationId(),
                    request.getDateTo());
            // Pre：窗内展示口径发药只查一次；未交账与发药人从中拆分，禁止重复 findAll
            List<MedicationDispense> summaryDispenses =
                this.medicationDispenseQueryService.findDispensesForPharmacyCashierSettlementSummary(previewQuery);
            List<MedicationDispense> unsettledDispenses =
                this.medicationDispenseQueryService.filterUnsettledDispenses(summaryDispenses);
            List<Long> practitionerIdsInWindow =
                this.medicationDispenseQueryService.extractDistinctPractitionerIds(summaryDispenses);
            List<OptionDto> practitionerOptions = this.buildPractitionerOptions(practitionerIdsInWindow);
            Map<String, String> categoryLabelByCode =
                this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
            Map<String, String> unitLabels =
                this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
            // Asm：主区进/售只读价一次，大类与品规共用快照
            PharmacyDailySettlementPricingSupport.PharmacyDailySettlementPurchaseSaleSnapshot purchaseSaleSnapshot =
                this.pharmacyDailySettlementPricingSupport.buildPurchaseSaleSnapshot(summaryDispenses, false);
            List<PharmacyDailySettlementCategoryRowDto> categoryRows =
                this.pharmacyDailySettlementPreviewAssembler.assembleCategoryRows(
                    purchaseSaleSnapshot, categoryLabelByCode);
            List<PharmacyDailySettlementProductRowDto> productRows =
                this.pharmacyDailySettlementPreviewAssembler.assembleProductRows(purchaseSaleSnapshot);
            List<PharmacyDailySettlementDispenseItemDto> items =
                this.pharmacyDailySettlementPreviewAssembler.toDispenseItems(
                    unsettledDispenses, false, unitLabels, purchaseSaleSnapshot);
            // Asm：待交笔数按源行条数计（BR-CS-011：一发一退=2）；头表金额=主区售价合计（复用进/售快照）
            BigDecimal totalIncome =
                this.pharmacyDailySettlementPricingSupport.sumSaleAmount(purchaseSaleSnapshot);
            PreviewOutpatientPharmacyDailySettlementResponse response = new PreviewOutpatientPharmacyDailySettlementResponse();
            response.setCategoryRows(categoryRows);
            response.setProductRows(productRows);
            response.setPendingCount(unsettledDispenses.size());
            response.setTotalIncome(totalIncome);
            response.setItems(items);
            response.setPractitionerIdsInWindow(practitionerIdsInWindow);
            response.setPractitionerOptions(practitionerOptions);
            response.setGapHardBlockEarliestAt(gapHardBlockEarliestAt);
            response.setGapHardBlockMessage(
                this.dailySettlementOrgLocationScopeSupport.buildGapHardBlockMessage(gapHardBlockEarliestAt));
            response.setGapSoftWarnCount(gapSoftWarnCount);
            response.setHandoverTotalQuantity(
                this.pharmacyDailySettlementPreviewAssembler.sumHandoverTotalQuantity(unsettledDispenses));
            return response;
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("门诊药房日结查询失败", runtimeException);
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-OP-PDC-03：确认门诊药房交账。
     *
     * <p>头表 {@code total_income} 为勾选发药<strong>售价合计</strong>（与主区大类售价之和同口径，见
     * {@link PharmacyDailySettlementPricingSupport#sumSaleAmountByDispenseIds}）。</p>
     *
     * @param request 与预览相同业务窗、药房与勾选发药行
     * @return 交账头标识、单号与挂载条数
     * @throws BusinessException 非药师、参数非法、漏交硬栏或并发冲突
     */
    public SubmitOutpatientPharmacyDailySettlementResponse submitOutpatientPharmacyDailySettlement(
        SubmitOutpatientPharmacyDailySettlementRequest request) {
        // Gate：登录、日期窗、药房与提交人
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
            "提交失败，请稍后重试");
        Long submitterId = this.currentPractitionerResolver.requirePractitionerId(
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
            "当前账号未绑定医务人员，无法提交交账");
        // Gate：交账提交只认药师岗；审批/驳回在交账审批，不在这里
        if (!this.holdsPharmacistRole(submitterId)) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
                "仅药师可提交交账");
        }
        this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
        // Gate：药房必填且须在岗位管辖范围内（BR-OP-PDC-009）
        this.assertManagedPharmacyLocation(request.getLocationId());
        PreviewOutpatientPharmacyDailySettlementRequest previewRequest = this.toPreviewRequestFromSubmit(request);
        PreviewPharmacyCashierSettlementQuery previewQuery = this.outpatientPharmacyDailySettlementMapper.toPreviewQuery(
            previewRequest,
            CashierSettlementType.OP_PHARMACY.getCode());
        String typeEnum = CashierSettlementType.OP_PHARMACY.getCode();
        // Gate：漏交硬栏（BR-CS-020）
        LocalDateTime gapHardBlockEarliestAt =
            this.medicationDispenseQueryService.findEarliestUnsettledDispenseTimeBeforeWindow(
                typeEnum,
                request.getLocationId(),
                request.getDateFrom(),
                request.getDateTo());
        if (gapHardBlockEarliestAt != null) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_202,
                this.dailySettlementOrgLocationScopeSupport.buildGapHardBlockMessage(gapHardBlockEarliestAt));
        }
        long gapSoftWarnCount =
            this.medicationDispenseQueryService.countUnsettledDispensesAfterWindow(
                typeEnum,
                request.getLocationId(),
                request.getDateTo());
        // Pre：提交前重新锁定候选集
        List<UnsettledDispensePreviewItemQueryModel> lockedItems =
            this.medicationDispenseQueryService.previewUnsettledDispensesForCashierSettlement(previewQuery);
        List<Long> selectedDispenseIds = request.getSelectedDispenseIds();
        this.assertDispenseIdsSubsetOfCandidates(
            selectedDispenseIds,
            lockedItems,
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_105,
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_201);
        Integer paymentCount = selectedDispenseIds.size();
        List<Long> practitionerIdsInWindow =
            this.medicationDispenseQueryService.listDistinctPractitionerIdsInPharmacyCashierSettlementWindow(
                typeEnum,
                request.getLocationId(),
                request.getDateFrom(),
                request.getDateTo());
        String practitionerIds =
            this.dailySettlementOrgLocationScopeSupport.joinPractitionerIds(practitionerIdsInWindow);
        // Cmd：勾选发药售价合计落头表 total_income（列表/审批金额同口径，非利润）
        BigDecimal totalIncome = this.pharmacyDailySettlementPricingSupport.sumSaleAmountByDispenseIds(
            selectedDispenseIds,
            false);
        SubmitCashierSettlementCommand command = this.outpatientPharmacyDailySettlementMapper.toSubmitCommand(
            request,
            CashierSettlementType.OP_PHARMACY.getCode(),
            submitterId,
            operatorId,
            selectedDispenseIds,
            practitionerIds,
            paymentCount,
            totalIncome);
        Long settlementId = this.cashierSettlementDomainService.submitCashierSettlement(command);
        String busNo = this.cashierSettlementQueryService.findBusNoBySettlementId(settlementId);
        // Asm：返回交账标识
        SubmitOutpatientPharmacyDailySettlementResponse response =
            this.outpatientPharmacyDailySettlementMapper.toSubmitResponse(settlementId, busNo, paymentCount);
        response.setStatusEnum(CashierSettlementStatus.SUBMITTED.getCode());
        response.setGapSoftWarnCount(gapSoftWarnCount);
        return response;
    }

    /**
     * IF-OP-PDC-04：撤回门诊药房待审交账。
     *
     * @param request 交账头主键
     * @return 交账标识与单号
     * @throws BusinessException 无权限、状态非法或记录不存在
     */
    public WithdrawOutpatientPharmacyDailySettlementResponse withdrawOutpatientPharmacyDailySettlement(
        WithdrawOutpatientPharmacyDailySettlementRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
            "撤回失败，请稍后重试");
        if (request == null || request.getSettlementId() == null) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
                "交账头不能为空");
        }
        CashierSettlement settlement = this.cashierSettlementQueryService.findEntityById(request.getSettlementId());
        if (settlement == null
            || !CashierSettlementType.OP_PHARMACY.getCode().equals(settlement.getTypeEnum())) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_103,
                "交账记录不存在");
        }
        // Gate：交账头药房须在岗位管辖范围内（禁止撤回他库交账）
        this.assertManagedPharmacyLocation(settlement.getLocationId());
        // Gate：仅待审可撤回；须为药师岗
        this.assertWithdrawPermission(settlement);
        WithdrawCashierSettlementCommand command = new WithdrawCashierSettlementCommand();
        command.setSettlementId(request.getSettlementId());
        command.setOperatorId(operatorId);
        // Cmd：清挂载并改态 WITHDRAWN
        this.cashierSettlementDomainService.withdrawCashierSettlement(command);
        WithdrawOutpatientPharmacyDailySettlementResponse response = new WithdrawOutpatientPharmacyDailySettlementResponse();
        response.setSettlementId(settlement.getId());
        response.setBusNo(settlement.getBusNo());
        return response;
    }

    /**
     * IF-OP-PDC-05：门诊药房交账历史分页（按交账提交时刻窗，类型 {@code OP_PHARMACY}）。
     *
     * <p>仅返回当前登录者岗位管辖药房下的交账单（BR-OP-PDC-009）。</p>
     *
     * @param request 交账时刻窗与分页
     * @return 历史页
     */
    public PageOutpatientPharmacyDailySettlementHistoryResponse pageOutpatientPharmacyDailySettlementHistory(
        PageOutpatientPharmacyDailySettlementHistoryRequest request) {
        // Gate：登录与交账时刻窗必填
        this.currentOperatorResolver.requireOperatorId(
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_103,
            "查询失败，请稍后重试");
        if (request == null) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
                "请选择有效的日结时间范围");
        }
        this.assertDateRangeValid(request.getSubmittedFrom(), request.getSubmittedTo());
        // Pre：分页缺省与窗口裁剪
        int pageIndex = 0;
        if (request.getPageIndex() != null) {
            pageIndex = Math.max(0, request.getPageIndex());
        }
        int pageSize = 20;
        if (request.getPageSize() != null) {
            pageSize = Math.min(200, Math.max(1, request.getPageSize()));
        }
        // Pre：历史仅管辖药房；空集合 → 空页（禁止退化为全院）
        Set<Long> gatedPharmacyIds = this.resolveGatedPharmacyLocationIds();
        Page<CashierSettlementHistoryItemQueryModel> page = this.cashierSettlementQueryService.pageHistory(
            CashierSettlementType.OP_PHARMACY.getCode(),
            request.getSubmittedFrom(),
            request.getSubmittedTo(),
            gatedPharmacyIds,
            PageRequest.of(pageIndex, pageSize));
        PageOutpatientPharmacyDailySettlementHistoryResponse response =
            new PageOutpatientPharmacyDailySettlementHistoryResponse();
        response.setPageIndex(pageIndex);
        response.setPageSize(pageSize);
        response.setTotal(page.getTotalElements());
        List<OutpatientPharmacyDailySettlementHistoryItemDto> items = new ArrayList<>();
        for (CashierSettlementHistoryItemQueryModel model : page.getContent()) {
            if (model == null) {
                continue;
            }
            // Asm：历史行
            OutpatientPharmacyDailySettlementHistoryItemDto dto =
                new OutpatientPharmacyDailySettlementHistoryItemDto();
            dto.setSettlementId(model.getSettlementId());
            dto.setBusNo(model.getBusNo());
            dto.setSubmittedAt(model.getSubmittedAt());
            dto.setDateFrom(model.getDateFrom());
            dto.setDateTo(model.getDateTo());
            dto.setPractitionerIds(model.getPractitionerIds());
            dto.setSubmitterId(model.getSubmitterId());
            dto.setApproverId(model.getApproverId());
            dto.setApprovedAt(model.getApprovedAt());
            dto.setOrgId(model.getOrgId());
            dto.setLocationId(model.getLocationId());
            dto.setPaymentCount(model.getPaymentCount());
            dto.setTotalIncome(model.getTotalIncome());
            dto.setStatusEnum(model.getStatusEnum());
            dto.setStatusEnumText(this.resolveCashierSettlementStatusDisplay(model.getStatusEnum()));
            items.add(dto);
        }
        // Asm：回填交账人/审批人/药房名
        this.cashierSettlementPartyNamesEnricher.enrich(items);
        response.setItems(items);
        return response;
    }

    /**
     * 交账状态 code → 展示名；未知 code 回落原值。
     *
     * @param statusEnum 状态 code
     * @return 展示名
     */
    private String resolveCashierSettlementStatusDisplay(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return statusEnum;
        }
        for (CashierSettlementStatus status : CashierSettlementStatus.values()) {
            if (status.getCode().equals(statusEnum.trim())) {
                return status.getDisplay();
            }
        }
        return statusEnum;
    }

    /**
     * 解析药房下拉数据源：启用药房（可选 org 收窄）∩ 岗位管辖库房。
     *
     * @param request 初始化请求
     * @return 当前登录者可见的启用药房列表
     */
    private List<Location> resolvePharmacyLocations(LoadOutpatientPharmacyDailySettlementPageInitRequest request) {
        Long orgId = null;
        if (request != null) {
            orgId = request.getOrgId();
        }
        List<Location> pharmacies;
        if (orgId != null) {
            pharmacies = this.locationQueryService.listPharmaciesByOrganization(orgId);
        } else {
            pharmacies = this.locationQueryService.listAllActivePharmacies();
        }
        // Gate：与请领/发药同构，禁止暴露非管辖药房
        return this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
    }

    /**
     * 岗位可见且启用中的药房主键集合（下拉 / 历史 IN / 写路径 Gate 同源）。
     *
     * @return 管辖收窄后的药房 id；无可见药房时 empty
     */
    private Set<Long> resolveGatedPharmacyLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActivePharmacies());
    }

    /**
     * 药房范围键 Gate：必填且须落在岗位管辖启用药房白名单内。
     *
     * @param locationId 发药库房
     * @throws BusinessException 空药房或越权时 EX-OP-PDC-102
     */
    private void assertManagedPharmacyLocation(Long locationId) {
        this.assertLocationIdRequired(locationId);
        Set<Long> allowed = this.resolveGatedPharmacyLocationIds();
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId,
            allowed,
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
            "所选药房不在当前用户管辖范围内");
    }

    /**
     * 提交请求转预览请求（复用日期窗与药房）。
     *
     * @param request 提交请求
     * @return 预览请求
     */
    private PreviewOutpatientPharmacyDailySettlementRequest toPreviewRequestFromSubmit(
        SubmitOutpatientPharmacyDailySettlementRequest request) {
        PreviewOutpatientPharmacyDailySettlementRequest previewRequest = new PreviewOutpatientPharmacyDailySettlementRequest();
        previewRequest.setDateFrom(request.getDateFrom());
        previewRequest.setDateTo(request.getDateTo());
        previewRequest.setLocationId(request.getLocationId());
        return previewRequest;
    }

    /**
     * 构造药师岗发药人下拉（仅平台角色 {@code ROLE_PHARMACIST}）。
     *
     * <p>不含管理员岗：admin 仅当挂了药师岗，或查询后作为窗内实际发药人合并进下拉。
     * 空选表示不按人过滤。</p>
     *
     * @return 发药人 OptionDto 列表
     */
    private List<OptionDto> buildPharmacistOptions() {
        Page<Practitioner> page = this.practitionerRoleScopeLoader.pageActivePractitionersForPlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_PHARMACIST,
            null,
            null,
            PageRequest.of(0, OutpatientPharmacyDailySettlementConstants.PRACTITIONER_OPTION_PAGE_SIZE),
            true);
        List<OptionDto> options = new ArrayList<>();
        for (Practitioner practitioner : page.getContent()) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(practitioner.getId()));
            if (practitioner.getName() != null) {
                option.setLabel(practitioner.getName());
            } else {
                option.setLabel(String.valueOf(practitioner.getId()));
            }
            options.add(option);
        }
        return options;
    }

    /**
     * 窗内发药人主键 → OptionDto 下拉。
     *
     * @param practitionerIdsInWindow 窗内发药人去重列表
     * @return 发药人选项
     */
    private List<OptionDto> buildPractitionerOptions(List<Long> practitionerIdsInWindow) {
        List<OptionDto> options = new ArrayList<>();
        if (practitionerIdsInWindow == null || practitionerIdsInWindow.isEmpty()) {
            return options;
        }
        List<Practitioner> practitioners =
            this.practitionerQueryService.findAllByIds(practitionerIdsInWindow);
        Map<Long, Practitioner> practitionerById = new LinkedHashMap<>();
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null) {
                practitionerById.put(practitioner.getId(), practitioner);
            }
        }
        for (Long practitionerId : practitionerIdsInWindow) {
            if (practitionerId == null) {
                continue;
            }
            Practitioner practitioner = practitionerById.get(practitionerId);
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(practitionerId));
            if (practitioner != null && practitioner.getName() != null) {
                option.setLabel(practitioner.getName());
            } else {
                option.setLabel(String.valueOf(practitionerId));
            }
            options.add(option);
        }
        return options;
    }

    /**
     * 撤回权限 Gate：须为药师岗，且头表须为 SUBMITTED。
     *
     * @param settlement 交账头
     */
    private void assertWithdrawPermission(CashierSettlement settlement) {
        // 无头不能撤回
        if (settlement == null) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_401,
                "交账记录不存在");
        }
        // 已审或已撤回不可再撤，只允许待审
        if (!CashierSettlementStatus.isSubmitted(settlement.getStatusEnum())) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_401,
                "仅待审交账可撤回");
        }
        Long currentPractitionerId = this.currentPractitionerResolver.findPractitionerIdOrNull();
        // 撤回只认药师岗，不认提交人、财务或管理员
        if (this.holdsPharmacistRole(currentPractitionerId)) {
            return;
        }
        throw new BusinessException(
            OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_401,
            "仅药师可撤回该交账");
    }

    /**
     * 当前医务人员是否挂了激活的药师岗（{@code ROLE_PHARMACIST} / {@code adm_pr_assign}）。
     *
     * @param practitionerId 当前经办人 Practitioner 主键
     * @return 是药师时为 true
     */
    private boolean holdsPharmacistRole(Long practitionerId) {
        // 未绑定医务人员没有岗位，不能当药师交账
        if (practitionerId == null) {
            return false;
        }
        Set<Long> pharmacistIds = this.practitionerRoleScopeLoader.resolvePractitionerIdsForActivePlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_PHARMACIST,
            null);
        return pharmacistIds.contains(practitionerId);
    }

    /**
     * 日结时间范围 Gate。
     *
     * @param dateFrom 起
     * @param dateTo   止
     */
    private void assertDateRangeValid(LocalDateTime dateFrom, LocalDateTime dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
                "请选择有效的日结时间范围");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
                "请选择有效的日结时间范围");
        }
    }

    /**
     * 药房范围键 Gate。
     *
     * @param locationId 发药库房
     */
    private void assertLocationIdRequired(Long locationId) {
        if (locationId == null) {
            throw new BusinessException(
                OutpatientPharmacyDailySettlementConstants.EX_OP_PDC_102,
                "请选择药房");
        }
    }

    /**
     * 校验勾选发药行均属于当前未交账候选集。
     *
     * @param dispenseIds     请求勾选列表
     * @param lockedItems     提交前锁定的未交账预览
     * @param invalidExCode   越权 ID 异常码
     * @param emptyExCode     空列表异常码
     */
    private void assertDispenseIdsSubsetOfCandidates(
        List<Long> dispenseIds,
        List<UnsettledDispensePreviewItemQueryModel> lockedItems,
        String invalidExCode,
        String emptyExCode) {
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            throw new BusinessException(emptyExCode, "请至少选择一笔待交账发药明细");
        }
        Set<Long> candidateIds = this.collectDispenseIdsFromPreviewItems(lockedItems);
        for (Long dispenseId : dispenseIds) {
            if (dispenseId == null || !candidateIds.contains(dispenseId)) {
                throw new BusinessException(
                    invalidExCode,
                    "所选发药明细不在待交账范围内或已变更，请刷新后重试");
            }
        }
    }

    /**
     * 从未交账发药实体收集 dispense 主键（去重保序）。
     *
     * @param dispenses 发药实体列表
     * @return dispense id 集合
     */
    private Set<Long> collectDispenseIdsFromEntities(List<MedicationDispense> dispenses) {
        Set<Long> dispenseIds = new LinkedHashSet<>();
        if (dispenses == null) {
            return dispenseIds;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense != null && dispense.getId() != null) {
                dispenseIds.add(dispense.getId());
            }
        }
        return dispenseIds;
    }

    /**
     * 从未交账预览行收集 dispense 主键（去重保序）。
     *
     * @param previewItems 预览行列表
     * @return dispense id 集合
     */
    private Set<Long> collectDispenseIdsFromPreviewItems(List<UnsettledDispensePreviewItemQueryModel> previewItems) {
        Set<Long> dispenseIds = new LinkedHashSet<>();
        if (previewItems == null) {
            return dispenseIds;
        }
        for (UnsettledDispensePreviewItemQueryModel item : previewItems) {
            if (item != null && item.getDispenseId() != null) {
                dispenseIds.add(item.getDispenseId());
            }
        }
        return dispenseIds;
    }
}
