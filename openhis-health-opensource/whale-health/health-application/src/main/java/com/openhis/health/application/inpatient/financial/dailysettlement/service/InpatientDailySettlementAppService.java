/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.dailysettlement.service;

import com.openhis.health.application.inpatient.financial.dailysettlement.mapper.InpatientDailySettlementMapper;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.LoadInpatientDailySettlementPageInitRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.PreviewInpatientDailySettlementRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.SubmitInpatientDailySettlementRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.LoadInpatientDailySettlementPageInitResponse;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.PreviewInpatientDailySettlementResponse;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.SubmitInpatientDailySettlementResponse;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.service.CashierSettlementDomainService;
import com.openhis.health.domain.financial.cashiersettlement.service.CashierSettlementQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientDailySettlementConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.common.assembler.financial.ContractExpenseNatureAssembler;
import com.openhis.health.module.common.assembler.financial.PaymentEntererMatchSupport;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院日结交账应用服务（AS-IP-FIN-005，IF-IP-DCL-01～03）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、日期窗、经办人代交账权限。</li>
 *   <li>Pre：未交账候选、医保/子明细/病区 enrichment。</li>
 *   <li>Cmd：提交住院 type 交账。</li>
 *   <li>Asm：经 {@link InpatientDailySettlementMapper} 装配响应。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientDailySettlement/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientDailySettlementAppService {

    /** 写路径操作人门控。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 交账候选集读侧（未交账 payment 在 Payment 聚合 QueryService，B.6.4）。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 交账头读侧（busNo 等本聚合字段）。 */
    private final CashierSettlementQueryService cashierSettlementQueryService;

    /** 交账写侧。 */
    private final CashierSettlementDomainService cashierSettlementDomainService;

    /** 住院就诊上下文读侧（病区名）。 */
    private final EncounterContextFacade encounterContextFacade;

    /** 经办人主数据读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 岗位人员预解析。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** Request/Response 映射。 */
    private final InpatientDailySettlementMapper inpatientDailySettlementMapper;

    /** 费用性质（合同）展示判定。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    /** payment enterer_id 匹配（Practitioner + 用户主键存量兼容）。 */
    private final PaymentEntererMatchSupport paymentEntererMatchSupport;

    /** fin_type_code 字典读侧。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * IF-IP-DCL-01：住院日结页初始化。
     *
     * @param request 占位请求
     * @return 默认结账日与筛选项
     * @throws BusinessException 未登录或 Practitioner 解析失败
     */
    public LoadInpatientDailySettlementPageInitResponse loadInpatientDailySettlementPageInit(
        LoadInpatientDailySettlementPageInitRequest request) {
        // Gate：须已登录
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            InpatientDailySettlementConstants.EX_IP_DCL_101,
            "页面初始化失败，请稍后重试");
        // Pre：系统用户映射 Practitioner
        Practitioner currentPractitioner = this.practitionerQueryService.findByUserId(operatorId);
        if (currentPractitioner == null || currentPractitioner.getId() == null) {
            // Gate：系统用户须能映射为有效 Practitioner
            throw new BusinessException(
                InpatientDailySettlementConstants.EX_IP_DCL_101,
                "页面初始化失败，请稍后重试");
        }
        Long currentCashierId = currentPractitioner.getId();
        boolean financialDelegateEnabled = this.isFinancialDelegateEnabled(currentCashierId);
        List<OptionDto> expensePropertyOptions = this.buildExpensePropertyOptions();
        List<OptionDto> cashierOptions = this.buildCashierOptions(financialDelegateEnabled, currentPractitioner);
        LocalDate today = LocalDate.now();
        // Asm：只读初始化
        return this.inpatientDailySettlementMapper.toLoadInpatientDailySettlementPageInitResponse(
            today,
            financialDelegateEnabled,
            expensePropertyOptions,
            cashierOptions,
            currentCashierId);
    }

    /**
     * IF-IP-DCL-02：住院日结查询。
     *
     * @param request 日期窗、经办人与费用性质
     * @return 候选明细、渠道/病区汇总
     * @throws BusinessException 参数非法或查询失败
     */
    public PreviewInpatientDailySettlementResponse previewInpatientDailySettlement(
        PreviewInpatientDailySettlementRequest request) {
        try {
            // Gate：未登录拒绝查询
            Long operatorId = this.currentOperatorResolver.requireOperatorId(
                InpatientDailySettlementConstants.EX_IP_DCL_103,
                "查询失败，请稍后重试");
            Long cashierId = this.resolveCashierId(request.getCashierId(), operatorId);
            this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
            // Pre：组装住院 type 预览查询
            PreviewCashierSettlementQuery previewQuery = this.inpatientDailySettlementMapper.toPreviewCashierSettlementQuery(
                request,
                CashierSettlementType.INPATIENT.getCode(),
                cashierId);
            this.applyEntererMatchToPreviewQuery(previewQuery);
            PreviewCashierSettlementQueryModel previewModel =
                this.paymentReconciliationQueryService.previewUnsettledPaymentsForCashierSettlement(previewQuery);
            Set<Long> candidatePaymentIds = this.collectPaymentIds(previewModel);
            LocalDateTime windowStart = request.getDateFrom().atStartOfDay();
            LocalDateTime windowEnd = request.getDateTo().atTime(23, 59, 59);
            List<PaymentReconciliationQueryModel> paymentModels =
                this.paymentReconciliationQueryService.findPaymentByBillDateRange(windowStart, windowEnd);
            List<PaymentReconciliationQueryModel> filteredPayments =
                this.filterPaymentsByCandidateIds(paymentModels, candidatePaymentIds);
            Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId =
                this.loadRecDetailsByPaymentIds(candidatePaymentIds);
            List<Long> encounterIdsOrdered = this.collectEncounterIdsOrdered(filteredPayments);
            List<InpatientEncounterContextQueryModel> encounterContexts =
                this.encounterContextFacade.listInpatientContextsByEncounterIdsOrdered(encounterIdsOrdered);
            Map<Long, InpatientEncounterContextQueryModel> encounterByEncounterId =
                this.buildEncounterIndex(encounterContexts);
            Map<String, Boolean> insuranceByContractBusNo =
                this.contractExpenseNatureAssembler.mapInsuranceFlagByContractBusNoIn(
                    this.collectContractBusNos(previewModel));
            List<OptionDto> finTypeOptions =
                this.terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
            // Asm：费用性质仅过滤展示，paymentCount 保持全量（按 fin_contract 判定）
            PreviewInpatientDailySettlementResponse response =
                this.inpatientDailySettlementMapper.toPreviewInpatientDailySettlementResponse(
                    previewModel,
                    filteredPayments,
                    recDetailsByPaymentId,
                    encounterByEncounterId,
                    request.getExpenseProperty(),
                    insuranceByContractBusNo,
                    finTypeOptions);
            this.applyDateRangeToSummary(response, request.getDateFrom(), request.getDateTo());
            return response;
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException runtimeException) {
            log.error("住院日结查询失败", runtimeException);
            throw new BusinessException(
                InpatientDailySettlementConstants.EX_IP_DCL_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-IP-DCL-03：确认住院交账。
     *
     * @param request 业务窗与经办人
     * @return 交账结果
     * @throws BusinessException 参数非法、空候选或并发冲突
     */
    public SubmitInpatientDailySettlementResponse submitInpatientDailySettlement(
        SubmitInpatientDailySettlementRequest request) {
        // Gate：登录、日期窗与科室必填
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            InpatientDailySettlementConstants.EX_IP_DCL_102,
            "请选择有效的结账日期范围");
        Long cashierId = this.resolveCashierId(request.getCashierId(), operatorId);
        this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
        // Gate：交账科室为归档必填维度
        if (request.getOrgId() == null) {
            throw new BusinessException(
                InpatientDailySettlementConstants.EX_IP_DCL_102,
                "请选择有效的结账日期范围");
        }
        PreviewInpatientDailySettlementRequest previewRequest = new PreviewInpatientDailySettlementRequest();
        previewRequest.setDateFrom(request.getDateFrom());
        previewRequest.setDateTo(request.getDateTo());
        previewRequest.setCashierId(cashierId);
        PreviewCashierSettlementQuery previewQuery = this.inpatientDailySettlementMapper.toPreviewCashierSettlementQuery(
            previewRequest,
            CashierSettlementType.INPATIENT.getCode(),
            cashierId);
        this.applyEntererMatchToPreviewQuery(previewQuery);
        // Pre：提交前重新锁定候选集
        PreviewCashierSettlementQueryModel locked =
            this.paymentReconciliationQueryService.previewUnsettledPaymentsForCashierSettlement(previewQuery);
        Integer paymentCount = locked.getPaymentCount();
        if (paymentCount == null) {
            // 领域未返回条数时按 0 装配
            paymentCount = 0;
        }
        // Pre：请求转领域命令
        SubmitCashierSettlementCommand command = this.inpatientDailySettlementMapper.toSubmitCashierSettlementCommand(
            request,
            CashierSettlementType.INPATIENT.getCode(),
            cashierId,
            operatorId);
        command.setEntererMatchIds(previewQuery.getEntererMatchIds());
        // Cmd：单事务写交账头并挂载 payment（预交金与结算同批）
        Long settlementId = this.cashierSettlementDomainService.submitCashierSettlement(command);
        String busNo = this.cashierSettlementQueryService.findBusNoBySettlementId(settlementId);
        // Asm：返回交账标识与单号
        return this.inpatientDailySettlementMapper.toSubmitInpatientDailySettlementResponse(
            settlementId,
            busNo,
            paymentCount);
    }

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

    private List<OptionDto> buildExpensePropertyOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto selfPay = new OptionDto();
        selfPay.setValue(InpatientDailySettlementConstants.EXPENSE_SELF_PAY);
        selfPay.setLabel(InpatientDailySettlementConstants.EXPENSE_SELF_PAY_LABEL);
        options.add(selfPay);
        OptionDto insurance = new OptionDto();
        insurance.setValue(InpatientDailySettlementConstants.EXPENSE_INSURANCE);
        insurance.setLabel(InpatientDailySettlementConstants.EXPENSE_INSURANCE_LABEL);
        options.add(insurance);
        return options;
    }

    private List<OptionDto> buildCashierOptions(boolean financialDelegateEnabled, Practitioner currentPractitioner) {
        List<OptionDto> options = new ArrayList<>();
        // 财务代交账：加载在岗经办人；否则仅本人
        if (financialDelegateEnabled) {
            Page<Practitioner> page = this.practitionerQueryService.pageActivePractitionersForPosition(
                null,
                null,
                PageRequest.of(0, InpatientDailySettlementConstants.CASHIER_OPTION_PAGE_SIZE),
                true);
            for (Practitioner practitioner : page.getContent()) {
                // 跳过无有效主键的脏数据行
                if (practitioner == null || practitioner.getId() == null) {
                    continue;
                }
                OptionDto option = new OptionDto();
                option.setValue(String.valueOf(practitioner.getId()));
                option.setLabel(practitioner.getName());
                options.add(option);
            }
            return options;
        }
        OptionDto self = new OptionDto();
        self.setValue(String.valueOf(currentPractitioner.getId()));
        self.setLabel(currentPractitioner.getName());
        options.add(self);
        return options;
    }

    private Long resolveCashierId(Long requestedCashierId, Long operatorId) {
        Practitioner currentPractitioner = this.practitionerQueryService.findByUserId(operatorId);
        if (currentPractitioner == null || currentPractitioner.getId() == null) {
            // Gate：无法解析当前经办人则拒绝
            throw new BusinessException(
                InpatientDailySettlementConstants.EX_IP_DCL_102,
                "请选择有效的结账日期范围");
        }
        Long currentCashierId = currentPractitioner.getId();
        // 未指定经办人时默认当前操作员
        if (requestedCashierId == null) {
            return currentCashierId;
        }
        // 指定本人无需额外权限
        if (requestedCashierId.equals(currentCashierId)) {
            return requestedCashierId;
        }
        // Gate：非本人经办须具备财务代交账权限
        if (!this.isFinancialDelegateEnabled(currentCashierId)) {
            throw new BusinessException(
                InpatientDailySettlementConstants.EX_IP_DCL_102,
                "请选择有效的结账日期范围");
        }
        return requestedCashierId;
    }

    private void assertDateRangeValid(LocalDate dateFrom, LocalDate dateTo) {
        // Gate：结账日起止均须填写
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(
                InpatientDailySettlementConstants.EX_IP_DCL_102,
                "请选择有效的结账日期范围");
        }
        // Gate：止日不得早于起日
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(
                InpatientDailySettlementConstants.EX_IP_DCL_102,
                "请选择有效的结账日期范围");
        }
    }

    private Set<Long> collectPaymentIds(PreviewCashierSettlementQueryModel previewModel) {
        Set<Long> paymentIds = new LinkedHashSet<>();
        // 无候选时返回空集
        if (previewModel == null || previewModel.getItems() == null) {
            return paymentIds;
        }
        for (UnsettledPaymentPreviewItemQueryModel item : previewModel.getItems()) {
            if (item != null && item.getPaymentId() != null) {
                paymentIds.add(item.getPaymentId());
            }
        }
        return paymentIds;
    }

    private Set<String> collectContractBusNos(PreviewCashierSettlementQueryModel previewModel) {
        Set<String> contractBusNos = new LinkedHashSet<>();
        if (previewModel == null || previewModel.getItems() == null) {
            return contractBusNos;
        }
        for (UnsettledPaymentPreviewItemQueryModel item : previewModel.getItems()) {
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

    private List<PaymentReconciliationQueryModel> filterPaymentsByCandidateIds(
        List<PaymentReconciliationQueryModel> paymentModels,
        Set<Long> candidatePaymentIds) {
        List<PaymentReconciliationQueryModel> filtered = new ArrayList<>();
        // 无候选 payment 时跳过 enrichment
        if (paymentModels == null || candidatePaymentIds.isEmpty()) {
            return filtered;
        }
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel == null || paymentModel.getId() == null) {
                continue;
            }
            if (candidatePaymentIds.contains(paymentModel.getId())) {
                filtered.add(paymentModel);
            }
        }
        return filtered;
    }

    private Map<Long, List<PaymentRecDetailQueryModel>> loadRecDetailsByPaymentIds(Set<Long> paymentIds) {
        Map<Long, List<PaymentRecDetailQueryModel>> result = new LinkedHashMap<>();
        for (Long paymentId : paymentIds) {
            List<PaymentRecDetailQueryModel> details =
                this.paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);
            result.put(paymentId, details);
        }
        return result;
    }

    private List<Long> collectEncounterIdsOrdered(List<PaymentReconciliationQueryModel> paymentModels) {
        List<Long> encounterIds = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        if (paymentModels == null) {
            return encounterIds;
        }
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel == null || paymentModel.getEncounterId() == null) {
                continue;
            }
            if (!seen.contains(paymentModel.getEncounterId())) {
                seen.add(paymentModel.getEncounterId());
                encounterIds.add(paymentModel.getEncounterId());
            }
        }
        return encounterIds;
    }

    private Map<Long, InpatientEncounterContextQueryModel> buildEncounterIndex(
        List<InpatientEncounterContextQueryModel> encounterContexts) {
        Map<Long, InpatientEncounterContextQueryModel> index = new LinkedHashMap<>();
        if (encounterContexts == null) {
            return index;
        }
        for (InpatientEncounterContextQueryModel context : encounterContexts) {
            if (context != null && context.getEncounterId() != null) {
                index.put(context.getEncounterId(), context);
            }
        }
        return index;
    }

    private void applyDateRangeToSummary(
        PreviewInpatientDailySettlementResponse response,
        LocalDate dateFrom,
        LocalDate dateTo) {
        if (response == null || response.getSummary() == null) {
            // 无汇总块时不改写 dateRange
            return;
        }
        String rangeText = dateFrom.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
            + " ~ " + dateTo.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        response.getSummary().setDateRange(rangeText);
    }

    /**
     * 写入 payment {@code enterer_id} 匹配白名单，兼容 Practitioner 与用户主键存量。
     *
     * @param previewQuery 日结预览/提交共用查询
     */
    private void applyEntererMatchToPreviewQuery(PreviewCashierSettlementQuery previewQuery) {
        if (previewQuery == null || previewQuery.getCashierId() == null) {
            return;
        }
        previewQuery.setEntererMatchIds(
            this.paymentEntererMatchSupport.resolveEntererMatchIds(previewQuery.getCashierId()));
    }
}
