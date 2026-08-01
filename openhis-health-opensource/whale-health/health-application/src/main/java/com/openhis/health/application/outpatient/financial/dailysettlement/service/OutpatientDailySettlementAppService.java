/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.service;

import com.openhis.health.application.outpatient.financial.dailysettlement.mapper.OutpatientDailySettlementMapper;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.LoadOutpatientCashierSettlementVoucherRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.LoadOutpatientDailySettlementPageInitRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.PageOutpatientCashierSettlementHistoryRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.PreviewOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.SubmitOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.LoadOutpatientCashierSettlementVoucherResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.LoadOutpatientDailySettlementPageInitResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.OutpatientCashierSettlementHistoryItemDto;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.PageOutpatientCashierSettlementHistoryResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.PreviewOutpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.SubmitOutpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.WithdrawOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.WithdrawOutpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.support.DailySettlementPaymentDetailLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.command.WithdrawCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.service.CashierSettlementDomainService;
import com.openhis.health.domain.financial.cashiersettlement.service.CashierSettlementQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientDailySettlementConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.CashierSettlementStatus;
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.health.application.shared.financial.ContractExpenseNatureAssembler;
import com.openhis.health.application.shared.financial.DailySettlementCashierScope;
import com.openhis.health.application.shared.financial.DailySettlementCashierScopeResolver;
import com.openhis.health.application.shared.financial.DailySettlementOrgLocationScopeSupport;
import com.openhis.health.application.shared.financial.PaymentEntererMatchResolver;
import com.openhis.health.application.shared.financial.cashiersettlement.CashierSettlementMountedVoucherDto;
import com.openhis.health.application.shared.financial.cashiersettlement.CashierSettlementPartyNamesEnricher;
import com.openhis.health.application.shared.financial.medicationrevenue.DailySettlementMedicationRevenueAssembler;
import com.openhis.health.application.shared.financial.medicationrevenue.PreviewDailySettlementMedicationRevenueResponse;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.financial.cashiersettlement.entity.CashierSettlement;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.CashierSettlementHistoryItemQueryModel;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
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
 * 门诊日结交账应用服务（AS-OP-FIN-004，IF-OP-DCL-01～03）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、日期窗、经办人代交账权限。</li>
 *   <li>Pre：未交账候选集、医保关联与子明细渠道 enrichment。</li>
 *   <li>Cmd：提交交账并挂载 payment。</li>
 *   <li>Asm：经 {@link OutpatientDailySettlementMapper} 装配响应。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/outpatientDailySettlement/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientDailySettlementAppService {

    /** 写路径操作人门控（用户主键，审计字段）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 交账候选集读侧（未交账 payment 在 Payment 聚合 QueryService，B.6.4）。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 交账头读侧（busNo 等本聚合字段）。 */
    private final CashierSettlementQueryService cashierSettlementQueryService;

    /** 交账历史展示名 enrich。 */
    private final CashierSettlementPartyNamesEnricher cashierSettlementPartyNamesEnricher;

    /** 交账写侧。 */
    private final CashierSettlementDomainService cashierSettlementDomainService;

    /** 经办人主数据读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 当前登录医务人员（会话 practitionerId）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 费用性质（fin_contract）读侧。 */
    private final ContractQueryService contractQueryService;

    /** 岗位人员预解析（财务代交账权限与下拉）。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** Request/Response 映射。 */
    private final OutpatientDailySettlementMapper outpatientDailySettlementMapper;

    /** 费用性质（合同）展示判定。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    /** 收款人多选可选软筛解析。 */
    private final DailySettlementCashierScopeResolver dailySettlementCashierScopeResolver;

    /** 科室 → location 范围与交账头快照辅助。 */
    private final DailySettlementOrgLocationScopeSupport dailySettlementOrgLocationScopeSupport;

    /** 药品收入 TAB 组装。 */
    private final DailySettlementMedicationRevenueAssembler dailySettlementMedicationRevenueAssembler;

    /** fin_type_code 字典读侧。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** payment.enterer_id ↔ Practitioner 主键归一（窗内收费人下拉）。 */
    private final PaymentEntererMatchResolver paymentEntererMatchResolver;

    /** payment 项目行与支付方式 enrichment。 */
    private final DailySettlementPaymentDetailLoader dailySettlementPaymentDetailLoader;

    /**
     * IF-OP-DCL-01：门诊日结页初始化。
     *
     * @param request 占位请求
     * @return 默认结账日、费用性质（fin_contract）与收款人筛选项
     * @throws BusinessException 未登录时抛出 EX-OP-DCL-101
     */
    public LoadOutpatientDailySettlementPageInitResponse loadOutpatientDailySettlementPageInit(
        LoadOutpatientDailySettlementPageInitRequest request) {
        // Gate：须已登录；下拉主数据不因未绑定 Practitioner 而整页失败
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientDailySettlementConstants.EX_OP_DCL_101,
            "页面初始化失败，请稍后重试");
        // Pre：默认收款人优先会话 practitionerId，回落 userId→Practitioner 映射
        Long currentCashierId = this.resolveCurrentCashierPractitionerId(operatorId);
        Practitioner currentPractitioner = this.resolvePractitionerForCashierOptions(currentCashierId, operatorId);
        boolean financialDelegateEnabled = currentCashierId != null
            && this.isFinancialDelegateEnabled(currentCashierId);
        List<OptionDto> expensePropertyOptions = this.buildExpensePropertyOptions();
        List<OptionDto> cashierOptions = this.buildCashierOptions(financialDelegateEnabled, currentPractitioner);
        LocalDate today = LocalDate.now();
        // Asm：只读初始化数据装配（收款人默认勾选由前端按 preview 窗内有流水人驱动）
        return this.outpatientDailySettlementMapper.toLoadOutpatientDailySettlementPageInitResponse(
            today,
            financialDelegateEnabled,
            expensePropertyOptions,
            cashierOptions,
            currentCashierId);
    }

    /**
     * IF-OP-DCL-02：门诊日结查询（未交账预览）。
     *
     * @param request 日期窗、经办人与费用性质展示过滤
     * @return 候选明细、渠道汇总与收入汇总
     * @throws BusinessException 参数非法或查询失败
     */
    public PreviewOutpatientDailySettlementResponse previewOutpatientDailySettlement(
        PreviewOutpatientDailySettlementRequest request) {
        try {
            // Gate：未登录拒绝查询
            Long operatorId = this.currentOperatorResolver.requireOperatorId(
                OutpatientDailySettlementConstants.EX_OP_DCL_103,
                "查询失败，请稍后重试");
            this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
            this.assertOrgIdRequired(request.getOrgId(), OutpatientDailySettlementConstants.EX_OP_DCL_102);
            // Pre：解析科室 location 白名单并组装预览查询（默认不按人硬筛）
            PreviewCashierSettlementQuery previewQuery = this.buildPreviewQuery(
                request,
                CashierSettlementType.OP_PAYMENT.getCode(),
                operatorId);
            // Pre：漏交扫描与窗内 enterer 自动带出（DD §2.6.5）
            LocalDateTime gapHardBlockEarliestAt =
                this.paymentReconciliationQueryService.findEarliestUnsettledPaymentBillTimeBeforeWindow(previewQuery);
            long gapSoftWarnCount =
                this.paymentReconciliationQueryService.countUnsettledPaymentsAfterWindow(previewQuery);
            // Pre：窗内 enterer 可能混有 userId，归一为 Practitioner 主键后再装配下拉
            List<Long> practitionerIdsInWindow =
                this.paymentEntererMatchResolver.resolvePractitionerIdsFromEntererIds(
                    this.paymentReconciliationQueryService.listDistinctEntererIdsInCashierSettlementWindow(
                        previewQuery));
            List<OptionDto> practitionerOptions =
                this.buildPractitionerOptions(practitionerIdsInWindow);
            PreviewCashierSettlementQueryModel previewModel =
                this.paymentReconciliationQueryService.previewUnsettledPaymentsForCashierSettlement(previewQuery);
            // Pre：展示汇总须含已交账流水，仅 paymentCount/items 保持未交账口径（BR-OP-DCL-014）
            List<UnsettledPaymentPreviewItemQueryModel> summaryPreviewItems =
                this.paymentReconciliationQueryService.previewPaymentsForCashierSettlementSummary(previewQuery);
            Set<Long> displayPaymentIds = this.collectPaymentIdsFromPreviewItems(summaryPreviewItems);
            List<PaymentReconciliationQueryModel> displayPaymentModels =
                this.paymentReconciliationQueryService.findPaymentQueryModelsForCashierSettlementSummary(previewQuery);
            Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId =
                this.loadRecDetailsByPaymentIds(displayPaymentIds);
            Map<String, Boolean> insuranceByContractBusNo =
                this.contractExpenseNatureAssembler.mapInsuranceFlagByContractBusNoIn(
                    this.collectContractBusNosFromPreviewItems(summaryPreviewItems));
            List<OptionDto> finTypeOptions =
                this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            // Pre：未交账候选 payment 加载 charge_item（弹窗项目行 enrichment）
            Set<Long> unsettledPaymentIds = this.collectPaymentIdsFromPreviewItems(previewModel.getItems());
            List<PaymentReconciliationQueryModel> unsettledPaymentModels =
                this.filterPaymentModelsByIds(displayPaymentModels, unsettledPaymentIds);
            Map<Long, List<ChargeItem>> chargeItemsByPaymentId =
                this.dailySettlementPaymentDetailLoader.loadChargeItemsByPaymentModels(unsettledPaymentModels);
            // Asm：费用性质仅过滤 rows/summary 展示（按 fin_contract 判定）
            PreviewOutpatientDailySettlementResponse response =
                this.outpatientDailySettlementMapper.toPreviewOutpatientDailySettlementResponse(
                    previewModel,
                    summaryPreviewItems,
                    displayPaymentModels,
                    recDetailsByPaymentId,
                    chargeItemsByPaymentId,
                    request.getExpenseProperty(),
                    insuranceByContractBusNo,
                    finTypeOptions);
            this.applyDateRangeToSummary(response, request.getDateFrom(), request.getDateTo());
            response.setGapHardBlockEarliestAt(gapHardBlockEarliestAt);
            response.setGapHardBlockMessage(
                this.dailySettlementOrgLocationScopeSupport.buildGapHardBlockMessage(gapHardBlockEarliestAt));
            response.setGapSoftWarnCount(gapSoftWarnCount);
            response.setPractitionerIdsInWindow(practitionerIdsInWindow);
            response.setPractitionerOptions(practitionerOptions);
            return response;
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("门诊日结查询失败", runtimeException);
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-OP-DCL-03：确认门诊交账。
     *
     * @param request 与预览相同业务窗与经办人（不含 expenseProperty）
     * @return 交账头标识、单号与挂载条数
     * @throws BusinessException 参数非法、空候选或并发冲突
     */
    public SubmitOutpatientDailySettlementResponse submitOutpatientDailySettlement(
        SubmitOutpatientDailySettlementRequest request) {
        // Gate：登录、日期窗、科室与提交人必填
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientDailySettlementConstants.EX_OP_DCL_102,
            "请选择有效的结账日期范围");
        Long submitterId = this.currentPractitionerResolver.requirePractitionerId(
            OutpatientDailySettlementConstants.EX_OP_DCL_102,
            "当前账号未绑定医务人员，无法提交交账");
        this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
        this.assertOrgIdRequired(request.getOrgId(), OutpatientDailySettlementConstants.EX_OP_DCL_102);
        PreviewOutpatientDailySettlementRequest previewRequest = this.toPreviewRequestFromSubmit(request);
        PreviewCashierSettlementQuery previewQuery = this.buildPreviewQuery(
            previewRequest,
            CashierSettlementType.OP_PAYMENT.getCode(),
            operatorId);
        // Gate：漏交硬栏（DD §2.6.5）
        LocalDateTime gapHardBlockEarliestAt =
            this.paymentReconciliationQueryService.findEarliestUnsettledPaymentBillTimeBeforeWindow(previewQuery);
        if (gapHardBlockEarliestAt != null) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_106,
                this.dailySettlementOrgLocationScopeSupport.buildGapHardBlockMessage(gapHardBlockEarliestAt));
        }
        long gapSoftWarnCount =
            this.paymentReconciliationQueryService.countUnsettledPaymentsAfterWindow(previewQuery);
        // Pre：提交前重新锁定候选集，paymentIds 须 ⊆ 未交账候选（BR-OP-DCL-018）
        PreviewCashierSettlementQueryModel locked =
            this.paymentReconciliationQueryService.previewUnsettledPaymentsForCashierSettlement(previewQuery);
        List<Long> selectedPaymentIds = request.getPaymentIds();
        this.assertPaymentIdsSubsetOfCandidates(
            selectedPaymentIds,
            locked,
            OutpatientDailySettlementConstants.EX_OP_DCL_105,
            OutpatientDailySettlementConstants.EX_OP_DCL_201);
        Integer paymentCount = selectedPaymentIds.size();
        // Pre：头表 total_income = 勾选集合与 handoverSummary.totalIncome 同管线（DD §3.2.1）
        BigDecimal totalIncome =
            this.outpatientDailySettlementMapper.resolveSelectedSettlementTotalIncome(
                locked.getItems(),
                selectedPaymentIds);
        // Pre：提交快照 practitioner_ids 与下拉同口径（归一后的 Practitioner 主键）
        List<Long> practitionerIdsInWindow =
            this.paymentEntererMatchResolver.resolvePractitionerIdsFromEntererIds(
                this.paymentReconciliationQueryService.listDistinctEntererIdsInCashierSettlementWindow(previewQuery));
        String practitionerIds =
            this.dailySettlementOrgLocationScopeSupport.joinPractitionerIds(practitionerIdsInWindow);
        // Pre：请求转领域命令（不写 print_snapshot_json；收款不下推 locationIds）
        SubmitCashierSettlementCommand command = this.outpatientDailySettlementMapper.toSubmitCashierSettlementCommand(
            request,
            CashierSettlementType.OP_PAYMENT.getCode(),
            submitterId,
            operatorId,
            selectedPaymentIds,
            null,
            practitionerIds,
            paymentCount,
            totalIncome);
        this.applyOptionalEntererSoftFilterToCommand(command, previewQuery);
        // Cmd：单事务写交账头并挂载 payment
        Long settlementId = this.cashierSettlementDomainService.submitCashierSettlement(command);
        String busNo = this.cashierSettlementQueryService.findBusNoBySettlementId(settlementId);
        // Asm：返回交账标识与单号
        SubmitOutpatientDailySettlementResponse response =
            this.outpatientDailySettlementMapper.toSubmitOutpatientDailySettlementResponse(
                settlementId,
                busNo,
                paymentCount);
        response.setStatusEnum(CashierSettlementStatus.SUBMITTED.getCode());
        response.setGapSoftWarnCount(gapSoftWarnCount);
        return response;
    }

    /**
     * 门诊日结药品收入 TAB 预览（发药口径汇总 + 收费药品明细 + 未发提醒）。
     *
     * @param request 与日结查询相同筛选
     * @return 药品收入预览
     */
    public PreviewDailySettlementMedicationRevenueResponse previewOutpatientDailySettlementMedicationRevenue(
        PreviewOutpatientDailySettlementRequest request) {
        // Gate：登录、日期窗与科室必填
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientDailySettlementConstants.EX_OP_DCL_103,
            "查询失败，请稍后重试");
        this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
        this.assertOrgIdRequired(request.getOrgId(), OutpatientDailySettlementConstants.EX_OP_DCL_102);
        PreviewCashierSettlementQuery previewQuery = this.buildPreviewQuery(
            request,
            CashierSettlementType.OP_PAYMENT.getCode(),
            operatorId);
        // Pre：加载 payment 与 charge_item 明细
        List<PaymentReconciliationQueryModel> paymentModels =
            this.paymentReconciliationQueryService.findPaymentQueryModelsForCashierSettlementSummary(previewQuery);
        Map<Long, List<ChargeItem>> chargeItemsByPaymentId =
            this.dailySettlementPaymentDetailLoader.loadChargeItemsByPaymentModels(paymentModels);
        List<OptionDto> finTypeOptions =
            this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
        // Asm：组装药品收入 TAB 响应
        return this.dailySettlementMedicationRevenueAssembler.assemble(
            paymentModels,
            chargeItemsByPaymentId,
            request.getDateFrom(),
            request.getDateTo(),
            finTypeOptions);
    }

    /**
     * 门诊交账历史分页。
     *
     * @param request 交账时刻窗与分页
     * @return 历史页
     */
    public PageOutpatientCashierSettlementHistoryResponse pageOutpatientCashierSettlementHistory(
        PageOutpatientCashierSettlementHistoryRequest request) {
        // Gate：登录与交账时刻窗必填
        this.currentOperatorResolver.requireOperatorId(
            OutpatientDailySettlementConstants.EX_OP_DCL_103,
            "查询失败，请稍后重试");
        this.assertDateRangeValid(request.getSubmittedFrom(), request.getSubmittedTo());
        // Pre：分页缺省与窗口裁剪（禁三元）
        int pageIndex = 0;
        if (request.getPageIndex() != null) {
            pageIndex = Math.max(0, request.getPageIndex());
        }
        int pageSize = 20;
        if (request.getPageSize() != null) {
            pageSize = Math.min(200, Math.max(1, request.getPageSize()));
        }
        // Pre：按交账提交时刻窗分页历史
        Page<CashierSettlementHistoryItemQueryModel> page = this.cashierSettlementQueryService.pageHistory(
            CashierSettlementType.OP_PAYMENT.getCode(),
            request.getSubmittedFrom(),
            request.getSubmittedTo(),
            PageRequest.of(pageIndex, pageSize));
        PageOutpatientCashierSettlementHistoryResponse response = new PageOutpatientCashierSettlementHistoryResponse();
        response.setPageIndex(pageIndex);
        response.setPageSize(pageSize);
        response.setTotal(page.getTotalElements());
        List<OutpatientCashierSettlementHistoryItemDto> items = new ArrayList<>();
        for (CashierSettlementHistoryItemQueryModel model : page.getContent()) {
            if (model == null) {
                continue;
            }
            OutpatientCashierSettlementHistoryItemDto dto = new OutpatientCashierSettlementHistoryItemDto();
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
            dto.setCanReprintFromMounts(model.isCanReprintFromMounts());
            dto.setStatusEnum(model.getStatusEnum());
            dto.setStatusEnumText(this.resolveCashierSettlementStatusDisplay(model.getStatusEnum()));
            items.add(dto);
        }
        // Asm：回填交账人/审批人/科室名
        this.cashierSettlementPartyNamesEnricher.enrich(items);
        response.setItems(items);
        // Asm：历史分页响应
        return response;
    }

    /**
     * 加载门诊交账原单打印体（历史重打：按挂载 payment 现算，DD §8.G）。
     *
     * @param request 交账头主键
     * @return 现算打印体
     */
    public LoadOutpatientCashierSettlementVoucherResponse loadOutpatientCashierSettlementVoucher(
        LoadOutpatientCashierSettlementVoucherRequest request) {
        // Gate：须已登录
        this.currentOperatorResolver.requireOperatorId(
            OutpatientDailySettlementConstants.EX_OP_DCL_103,
            "查询失败，请稍后重试");
        // Gate：交账头必填
        if (request == null || request.getSettlementId() == null) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "交账头不能为空");
        }
        // Pre：校验交账头存在且为门诊收款类型
        CashierSettlement settlement = this.cashierSettlementQueryService.findEntityById(request.getSettlementId());
        if (settlement == null
            || !CashierSettlementType.OP_PAYMENT.getCode().equals(settlement.getTypeEnum())) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_103,
                "交账记录不存在");
        }
        // Pre：按挂载 payment 组装与预览同构的 rows/summary
        CashierSettlementMountedVoucherDto mountedVoucher =
            this.rebuildOutpatientMountedVoucherFromMounts(settlement);
        // Asm：返回打印体
        LoadOutpatientCashierSettlementVoucherResponse response = new LoadOutpatientCashierSettlementVoucherResponse();
        response.setSettlementId(settlement.getId());
        response.setBusNo(settlement.getBusNo());
        response.setMountedVoucher(mountedVoucher);
        return response;
    }

    /**
     * 按交账头挂载 payment 现算门诊打印体。
     *
     * @param settlement 交账头实体
     * @return 打印体；无挂载时 summary 为空对象结构仍可打印表头
     */
    private CashierSettlementMountedVoucherDto rebuildOutpatientMountedVoucherFromMounts(
        CashierSettlement settlement) {
        Long settlementId = settlement.getId();
        List<UnsettledPaymentPreviewItemQueryModel> mountedItems =
            this.paymentReconciliationQueryService.previewPaymentsByCashierSettlementId(settlementId);
        List<PaymentReconciliationQueryModel> paymentModels =
            this.paymentReconciliationQueryService.findPaymentQueryModelsByCashierSettlementId(settlementId);
        PreviewCashierSettlementQueryModel previewModel = new PreviewCashierSettlementQueryModel();
        previewModel.setItems(mountedItems);
        previewModel.setPaymentCount(mountedItems.size());
        Set<Long> paymentIds = this.collectPaymentIdsFromPreviewItems(mountedItems);
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId =
            this.loadRecDetailsByPaymentIds(paymentIds);
        Map<String, Boolean> insuranceByContractBusNo =
            this.contractExpenseNatureAssembler.mapInsuranceFlagByContractBusNoIn(
                this.collectContractBusNosFromPreviewItems(mountedItems));
        List<OptionDto> finTypeOptions =
            this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
        Map<Long, List<ChargeItem>> chargeItemsByPaymentId =
            this.dailySettlementPaymentDetailLoader.loadChargeItemsByPaymentModels(paymentModels);
        PreviewOutpatientDailySettlementResponse preview =
            this.outpatientDailySettlementMapper.toPreviewOutpatientDailySettlementResponse(
                previewModel,
                mountedItems,
                paymentModels,
                recDetailsByPaymentId,
                chargeItemsByPaymentId,
                null,
                insuranceByContractBusNo,
                finTypeOptions);
        this.applyDateRangeToSummary(preview, settlement.getDateFrom(), settlement.getDateTo());
        CashierSettlementMountedVoucherDto mountedVoucher = new CashierSettlementMountedVoucherDto();
        mountedVoucher.setSchemaVersion(1);
        mountedVoucher.setCashierName(null);
        mountedVoucher.setSettlementPeriodText(this.formatSettlementPeriodText(settlement));
        // 挂载集合即本批口径；用主区 rows/summary（与挂载集一致）
        if (preview != null) {
            mountedVoucher.setRows(preview.getRows());
            mountedVoucher.setSummary(preview.getSummary());
        }
        List<String> paymentIdTexts = new ArrayList<>();
        for (Long paymentId : paymentIds) {
            if (paymentId != null) {
                paymentIdTexts.add(String.valueOf(paymentId));
            }
        }
        mountedVoucher.setPaymentIds(paymentIdTexts);
        return mountedVoucher;
    }

    /**
     * 格式化交账业务窗文案（date_from ～ date_to）。
     *
     * @param settlement 交账头
     * @return 时间窗文案；缺窗时回落 bus_no
     */
    private String formatSettlementPeriodText(CashierSettlement settlement) {
        LocalDateTime dateFrom = settlement.getDateFrom();
        LocalDateTime dateTo = settlement.getDateTo();
        if (dateFrom == null || dateTo == null) {
            return settlement.getBusNo();
        }
        return dateFrom.format(GeneralSharedConstants.DATE_TIME_FORMATTER)
            + " ~ "
            + dateTo.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
    }

    /**
     * 撤回门诊待审交账（仅 SUBMITTED；提交人或会计）。
     *
     * @param request 交账头主键
     * @return 交账标识与单号
     * @throws BusinessException 无权限、状态非法或记录不存在
     */
    public WithdrawOutpatientDailySettlementResponse withdrawOutpatientDailySettlement(
        WithdrawOutpatientDailySettlementRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientDailySettlementConstants.EX_OP_DCL_102,
            "撤回失败，请稍后重试");
        if (request == null || request.getSettlementId() == null) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "交账头不能为空");
        }
        CashierSettlement settlement = this.cashierSettlementQueryService.findEntityById(request.getSettlementId());
        if (settlement == null
            || !CashierSettlementType.OP_PAYMENT.getCode().equals(settlement.getTypeEnum())) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_103,
                "交账记录不存在");
        }
        // Gate：仅待审可撤回；须为提交人或会计
        this.assertWithdrawPermission(
            settlement,
            OutpatientDailySettlementConstants.EX_OP_DCL_107);
        WithdrawCashierSettlementCommand command = new WithdrawCashierSettlementCommand();
        command.setSettlementId(request.getSettlementId());
        command.setOperatorId(operatorId);
        // Cmd：清挂载并改态 WITHDRAWN
        this.cashierSettlementDomainService.withdrawCashierSettlement(command);
        WithdrawOutpatientDailySettlementResponse response = new WithdrawOutpatientDailySettlementResponse();
        response.setSettlementId(settlement.getId());
        response.setBusNo(settlement.getBusNo());
        return response;
    }

    /**
     * 当前操作员是否具备财务代交账权限（会计或管理员岗位）。
     *
     * @param currentCashierId 当前经办人 Practitioner 主键
     * @return 可代指定收费员交账时为 true
     */
    private boolean isFinancialDelegateEnabled(Long currentCashierId) {
        Set<Long> accountantIds = this.practitionerRoleScopeLoader.resolvePractitionerIdsForActivePlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_ACCOUNTANT,
            null);
        // 会计岗位视为可代指定收费员交账
        if (accountantIds.contains(currentCashierId)) {
            return true;
        }
        Set<Long> adminIds = this.practitionerRoleScopeLoader.resolvePractitionerIdsForActivePlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_ADMIN,
            null);
        // 管理员岗位兜底具备代交账权限
        return adminIds.contains(currentCashierId);
    }

    /**
     * 构造费用性质下拉：启用中合同 busNo → 展示名。
     *
     * @return 费用性质 OptionDto 列表
     */
    private List<OptionDto> buildExpensePropertyOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<ContractListItemQueryModel> contracts = this.contractQueryService.listAllContracts();
        for (ContractListItemQueryModel contract : contracts) {
            if (contract == null) {
                continue;
            }
            if (Boolean.FALSE.equals(contract.getActiveFlag())) {
                continue;
            }
            if (StringUtils.isEmpty(contract.getBusNo()) || StringUtils.isEmpty(contract.getName())) {
                continue;
            }
            OptionDto option = new OptionDto();
            if (contract.getDisplayOrder() != null) {
                option.setSort(contract.getDisplayOrder().longValue());
            }
            option.setValue(contract.getBusNo().trim());
            option.setLabel(contract.getName().trim());
            options.add(option);
        }
        return options;
    }

    /**
     * 构造收款人下拉岗池：仅财务岗（{@code ROLE_ACCOUNTANT} / {@code adm_pr_assign}），供多选软筛。
     *
     * <p>不含管理员岗：admin 仅当挂了财务岗，或查询后作为窗内实际收费人合并进下拉。
     * 空选表示不按人过滤。岗池直出 Option，不做 identity 用户态二次过滤。</p>
     *
     * @param financialDelegateEnabled 保留入参兼容（岗池不再依赖代交账分支）
     * @param currentPractitioner      保留入参兼容
     * @return 收款人 OptionDto 列表；永不为 null
     */
    private List<OptionDto> buildCashierOptions(boolean financialDelegateEnabled, Practitioner currentPractitioner) {
        // Pre：仅财务岗在岗人员（adm_pr_assign + ROLE_ACCOUNTANT；不含 ROLE_ADMIN 单独入池）
        Page<Practitioner> page = this.practitionerRoleScopeLoader.pageActivePractitionersForPlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_ACCOUNTANT,
            null,
            null,
            PageRequest.of(0, OutpatientDailySettlementConstants.CASHIER_OPTION_PAGE_SIZE),
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
     * 解析当前经办人实体（供收款人下拉「仅本人」分支展示名）。
     *
     * @param currentCashierId 已解析 Practitioner 主键，可空
     * @param operatorUserId   当前登录用户主键
     * @return 经办人实体；无法解析时 {@code null}
     */
    private Practitioner resolvePractitionerForCashierOptions(Long currentCashierId, Long operatorUserId) {
        if (currentCashierId != null) {
            Practitioner practitioner = this.practitionerQueryService.findById(currentCashierId);
            if (practitioner != null && practitioner.getId() != null) {
                return practitioner;
            }
        }
        if (operatorUserId == null) {
            return null;
        }
        return this.practitionerQueryService.findByUserId(operatorUserId);
    }

    /**
     * 解析当前登录用户对应的收款人 Practitioner 主键。
     *
     * @param operatorUserId 当前登录用户主键
     * @return Practitioner 主键；无法解析则 null
     */
    private Long resolveCurrentCashierPractitionerId(Long operatorUserId) {
        Long practitionerId = this.currentPractitionerResolver.findPractitionerIdOrNull();
        if (practitionerId != null) {
            return practitionerId;
        }
        if (operatorUserId == null) {
            return null;
        }
        Practitioner practitioner = this.practitionerQueryService.findByUserId(operatorUserId);
        if (practitioner == null || practitioner.getId() == null) {
            return null;
        }
        return practitioner.getId();
    }

    /**
     * 解析收款人可选软筛（仅 cashierIds；空=不按人）。
     *
     * <p>人选软筛不按代交账权限拦截：岗池与窗内收费人均可勾选。</p>
     *
     * @param cashierIds 多选收款人
     * @param operatorId 当前登录用户
     * @return 筛选范围
     */
    private DailySettlementCashierScope resolveCashierScope(
        List<Long> cashierIds,
        Long operatorId) {
        Long currentCashierId = this.resolveCurrentCashierPractitionerId(operatorId);
        return this.dailySettlementCashierScopeResolver.resolve(
            cashierIds,
            currentCashierId,
            operatorId,
            OutpatientDailySettlementConstants.EX_OP_DCL_102);
    }

    /**
     * 组装预览查询：落 orgId（头表范围键）+ 可选 enterer 软筛。
     *
     * <p>收款不绑款台：{@code locationIds} 传 {@code null}，Payment 读侧<strong>不</strong>按
     * {@code location_id IN (科室下地点)} 过滤（payment 无科室列；误用会把候选扫空并把确认按钮灰掉）。</p>
     *
     * @param request    预览请求（含 orgId 与日期窗）
     * @param typeEnum   交账类型 code
     * @param operatorId 当前登录用户主键
     * @return 领域预览查询
     */
    private PreviewCashierSettlementQuery buildPreviewQuery(
        PreviewOutpatientDailySettlementRequest request,
        String typeEnum,
        Long operatorId) {
        // Pre：收款候选仅 type+时间窗（+可选人软筛）；不下推 org→location 白名单
        PreviewCashierSettlementQuery previewQuery = this.outpatientDailySettlementMapper.toPreviewCashierSettlementQuery(
            request,
            typeEnum,
            null);
        DailySettlementCashierScope cashierScope = this.resolveCashierScope(
            request.getCashierIds(),
            operatorId);
        this.applyCashierScopeToPreviewQuery(previewQuery, cashierScope);
        return previewQuery;
    }

    /**
     * 提交请求转预览请求（复用 org/日期窗与可选收款人软筛）。
     *
     * @param request 提交请求
     * @return 预览请求
     */
    private PreviewOutpatientDailySettlementRequest toPreviewRequestFromSubmit(
        SubmitOutpatientDailySettlementRequest request) {
        PreviewOutpatientDailySettlementRequest previewRequest = new PreviewOutpatientDailySettlementRequest();
        previewRequest.setDateFrom(request.getDateFrom());
        previewRequest.setDateTo(request.getDateTo());
        previewRequest.setOrgId(request.getOrgId());
        previewRequest.setCashierIds(request.getCashierIds());
        return previewRequest;
    }

    /**
     * 交账科室 Gate。
     *
     * @param orgId     科室主键
     * @param errorCode 异常码
     */
    private void assertOrgIdRequired(Long orgId, String errorCode) {
        if (orgId == null) {
            throw new BusinessException(errorCode, "请选择交账科室");
        }
    }

    /**
     * 窗内收费人 Practitioner 主键 → OptionDto 下拉（供前端合并进 cashierOptions）。
     *
     * <p>入参须已由 {@link PaymentEntererMatchResolver#resolvePractitionerIdsFromEntererIds} 归一。</p>
     *
     * @param practitionerIdsInWindow 窗内收费人 Practitioner 主键（去重）
     * @return 收费人选项
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
     * 可选软筛 entererMatchIds 写入提交命令（仅 previewQuery 已带软筛时）。
     *
     * @param command      提交命令
     * @param previewQuery 预览查询
     */
    private void applyOptionalEntererSoftFilterToCommand(
        SubmitCashierSettlementCommand command,
        PreviewCashierSettlementQuery previewQuery) {
        if (command == null || previewQuery == null) {
            return;
        }
        List<Long> entererMatchIds = previewQuery.getEntererMatchIds();
        if (entererMatchIds != null && !entererMatchIds.isEmpty()) {
            command.setEntererMatchIds(entererMatchIds);
        }
    }

    /**
     * 撤回权限 Gate：提交人或会计；且头表须为 SUBMITTED。
     *
     * @param settlement 交账头
     * @param errorCode  权限不足异常码
     */
    private void assertWithdrawPermission(CashierSettlement settlement, String errorCode) {
        if (settlement == null) {
            throw new BusinessException(errorCode, "交账记录不存在");
        }
        if (!CashierSettlementStatus.isSubmitted(settlement.getStatusEnum())) {
            throw new BusinessException(errorCode, "仅待审交账可撤回");
        }
        Long currentPractitionerId = this.currentPractitionerResolver.findPractitionerIdOrNull();
        Long submitterId = settlement.getSubmitterId();
        if (currentPractitionerId != null && currentPractitionerId.equals(submitterId)) {
            return;
        }
        if (currentPractitionerId != null && this.isFinancialDelegateEnabled(currentPractitionerId)) {
            return;
        }
        throw new BusinessException(errorCode, "仅提交人或会计可撤回该交账");
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
     * 日结时间范围 Gate：起止均须填写且止不早于起。
     *
     * @param dateFrom 日结时间起
     * @param dateTo   日结时间止
     * @throws BusinessException 范围缺失或不合法时
     */
    private void assertDateRangeValid(LocalDateTime dateFrom, LocalDateTime dateTo) {
        // Gate：日结时间起止均须填写
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "请选择有效的日结时间范围");
        }
        // Gate：止不得早于起
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "请选择有效的日结时间范围");
        }
    }

    /**
     * 从交账预览行收集 payment 主键（去重保序）。
     *
     * @param previewItems 预览行列表
     * @return payment id 集合
     */
    private Set<Long> collectPaymentIdsFromPreviewItems(List<UnsettledPaymentPreviewItemQueryModel> previewItems) {
        Set<Long> paymentIds = new LinkedHashSet<>();
        if (previewItems == null) {
            return paymentIds;
        }
        for (UnsettledPaymentPreviewItemQueryModel item : previewItems) {
            if (item != null && item.getPaymentId() != null) {
                paymentIds.add(item.getPaymentId());
            }
        }
        return paymentIds;
    }

    /**
     * 从交账预览行收集合同 busNo（去重保序）。
     *
     * @param previewItems 预览行列表
     * @return 合同 busNo 集合
     */
    private Set<String> collectContractBusNosFromPreviewItems(List<UnsettledPaymentPreviewItemQueryModel> previewItems) {
        Set<String> contractBusNos = new LinkedHashSet<>();
        if (previewItems == null) {
            return contractBusNos;
        }
        for (UnsettledPaymentPreviewItemQueryModel item : previewItems) {
            if (item == null || item.getContractNo() == null) {
                continue;
            }
            String busNo = item.getContractNo().trim();
            if (!busNo.isEmpty()) {
                contractBusNos.add(busNo);
            }
        }
        return contractBusNos;
    }

    /**
     * 按 payment id 批量加载支付明细读模型。
     *
     * @param paymentIds payment 主键集合
     * @return paymentId → 明细列表
     */
    private Map<Long, List<PaymentRecDetailQueryModel>> loadRecDetailsByPaymentIds(Set<Long> paymentIds) {
        Map<Long, List<PaymentRecDetailQueryModel>> result = new LinkedHashMap<>();
        for (Long paymentId : paymentIds) {
            List<PaymentRecDetailQueryModel> details =
                this.paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);
            result.put(paymentId, details);
        }
        return result;
    }

    /**
     * 将日结时间起止写入预览响应汇总块的 dateRange 展示文案。
     *
     * @param response 预览响应
     * @param dateFrom 日结时间起
     * @param dateTo   日结时间止
     */
    private void applyDateRangeToSummary(
        PreviewOutpatientDailySettlementResponse response,
        LocalDateTime dateFrom,
        LocalDateTime dateTo) {
        if (response == null || response.getSummary() == null) {
            // 无汇总块时不改写 dateRange
            return;
        }
        String rangeText = dateFrom.format(GeneralSharedConstants.DATE_TIME_FORMATTER)
            + " ~ " + dateTo.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
        response.getSummary().setDateRange(rangeText);
    }

    /**
     * 校验勾选 payment 主键均属于当前未交账候选集。
     *
     * @param paymentIds       请求勾选列表
     * @param lockedPreview    提交前锁定的未交账预览
     * @param invalidExCode    越权 ID 异常码
     * @param emptyExCode      空列表异常码
     */
    private void assertPaymentIdsSubsetOfCandidates(
        List<Long> paymentIds,
        PreviewCashierSettlementQueryModel lockedPreview,
        String invalidExCode,
        String emptyExCode) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new BusinessException(emptyExCode, "请至少选择一笔待交账流水");
        }
        Set<Long> candidateIds;
        // 无锁定预览则按空候选集校验勾选
        if (lockedPreview == null) {
            candidateIds = this.collectPaymentIdsFromPreviewItems(null);
        } else {
            candidateIds = this.collectPaymentIdsFromPreviewItems(lockedPreview.getItems());
        }
        for (Long paymentId : paymentIds) {
            // 勾选须 ⊆ 未交账候选，防并发交账越权
            if (paymentId == null || !candidateIds.contains(paymentId)) {
                throw new BusinessException(
                    invalidExCode,
                    "所选流水不在待交账范围内或已变更，请刷新后重试");
            }
        }
    }

    /**
     * 按 payment 主键过滤读模型（保序）。
     *
     * @param paymentModels 展示窗 payment 读模型
     * @param paymentIds    目标主键集合
     * @return 命中子集
     */
    private List<PaymentReconciliationQueryModel> filterPaymentModelsByIds(
        List<PaymentReconciliationQueryModel> paymentModels,
        Set<Long> paymentIds) {
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        if (paymentModels == null || paymentIds == null || paymentIds.isEmpty()) {
            return result;
        }
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel != null
                && paymentModel.getId() != null
                && paymentIds.contains(paymentModel.getId())) {
                result.add(paymentModel);
            }
        }
        return result;
    }

    /**
     * 将收款人筛选范围写入预览查询（entererMatchIds）。
     *
     * @param previewQuery 日结预览/提交共用查询
     * @param cashierScope 已解析范围
     */
    private void applyCashierScopeToPreviewQuery(
        PreviewCashierSettlementQuery previewQuery,
        DailySettlementCashierScope cashierScope) {
        if (previewQuery == null || cashierScope == null) {
            return;
        }
        List<Long> entererMatchIds = cashierScope.entererMatchIds();
        if (entererMatchIds == null || entererMatchIds.isEmpty()) {
            return;
        }
        previewQuery.setEntererMatchIds(entererMatchIds);
    }
}
