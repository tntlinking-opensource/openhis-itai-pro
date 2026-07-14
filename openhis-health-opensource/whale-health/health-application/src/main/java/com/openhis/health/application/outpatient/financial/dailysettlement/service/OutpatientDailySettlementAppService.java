/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.service;

import com.openhis.health.application.outpatient.financial.dailysettlement.mapper.OutpatientDailySettlementMapper;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.LoadOutpatientDailySettlementPageInitRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.PreviewOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.SubmitOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.LoadOutpatientDailySettlementPageInitResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.PreviewOutpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.SubmitOutpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.support.DailySettlementPaymentDetailLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
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
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.health.application.basic.administration.support.PractitionerCashierOptionProvider;
import com.openhis.health.application.shared.financial.ContractExpenseNatureAssembler;
import com.openhis.health.application.shared.financial.PaymentEntererMatchResolver;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
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

import java.time.LocalDate;
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

    /** payment enterer_id 匹配（Practitioner + 用户主键存量兼容）。 */
    private final PaymentEntererMatchResolver paymentEntererMatchResolver;

    /** fin_type_code 字典读侧。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 经办人下拉：排除绑定用户已删/未启用的 practitioner。 */
    private final PractitionerCashierOptionProvider practitionerCashierOptionProvider;

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
        // Asm：只读初始化数据装配
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
            Long cashierId = this.resolveCashierId(request.getCashierId(), operatorId);
            this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
            // Pre：组装门诊 type 预览查询
            PreviewCashierSettlementQuery previewQuery = this.outpatientDailySettlementMapper.toPreviewCashierSettlementQuery(
                request,
                CashierSettlementType.OUTPATIENT.getCode(),
                cashierId);
            this.applyEntererMatchToPreviewQuery(previewQuery, operatorId);
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
        // Gate：登录、日期窗与科室必填
        Long operatorId = this.currentOperatorResolver.requireOperatorId(
            OutpatientDailySettlementConstants.EX_OP_DCL_102,
            "请选择有效的结账日期范围");
        Long cashierId = this.resolveCashierId(request.getCashierId(), operatorId);
        this.assertDateRangeValid(request.getDateFrom(), request.getDateTo());
        // Gate：交账科室为归档必填维度
        if (request.getOrgId() == null) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "请选择有效的结账日期范围");
        }
        PreviewOutpatientDailySettlementRequest previewRequest = new PreviewOutpatientDailySettlementRequest();
        previewRequest.setDateFrom(request.getDateFrom());
        previewRequest.setDateTo(request.getDateTo());
        previewRequest.setCashierId(cashierId);
        PreviewCashierSettlementQuery previewQuery = this.outpatientDailySettlementMapper.toPreviewCashierSettlementQuery(
            previewRequest,
            CashierSettlementType.OUTPATIENT.getCode(),
            cashierId);
        this.applyEntererMatchToPreviewQuery(previewQuery, operatorId);
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
        // Pre：请求转领域命令（不含 expenseProperty）
        SubmitCashierSettlementCommand command = this.outpatientDailySettlementMapper.toSubmitCashierSettlementCommand(
            request,
            CashierSettlementType.OUTPATIENT.getCode(),
            cashierId,
            operatorId,
            selectedPaymentIds);
        command.setEntererMatchIds(previewQuery.getEntererMatchIds());
        // Cmd：单事务写交账头并挂载 payment
        Long settlementId = this.cashierSettlementDomainService.submitCashierSettlement(command);
        String busNo = this.cashierSettlementQueryService.findBusNoBySettlementId(settlementId);
        // Asm：返回交账标识与单号
        return this.outpatientDailySettlementMapper.toSubmitOutpatientDailySettlementResponse(
            settlementId,
            busNo,
            paymentCount);
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
     * 构造收款人下拉（BR-OP-DCL-003）：非财务代交账仅本人；会计/管理员可见全员。
     *
     * @param financialDelegateEnabled 当前操作员是否具备代交账权限
     * @param currentPractitioner      当前经办人；非代交账时用于装配唯一选项
     * @return 收款人 OptionDto 列表
     */
    private List<OptionDto> buildCashierOptions(boolean financialDelegateEnabled, Practitioner currentPractitioner) {
        List<OptionDto> options = new ArrayList<>();
        // 财务代交账：加载在岗且绑定系统用户的经办人；否则仅返回本人
        if (financialDelegateEnabled) {
            Page<Practitioner> page = this.practitionerQueryService.pageActivePractitionersForPosition(
                null,
                null,
                PageRequest.of(0, OutpatientDailySettlementConstants.CASHIER_OPTION_PAGE_SIZE),
                true);
            List<Practitioner> eligiblePractitioners =
                this.practitionerCashierOptionProvider.filterWithActiveBoundSystemUser(page.getContent());
            return this.practitionerCashierOptionProvider.toCashierOptions(eligiblePractitioners);
        }
        if (currentPractitioner == null || currentPractitioner.getId() == null) {
            return options;
        }
        if (!this.practitionerCashierOptionProvider.hasActiveBoundSystemUser(currentPractitioner)) {
            return options;
        }
        return this.practitionerCashierOptionProvider.toCashierOptions(List.of(currentPractitioner));
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
     * 解析本次日结/交账经办人：默认本人，非本人须具备代交账权限。
     *
     * @param requestedCashierId 请求指定经办人；可空
     * @param operatorId         当前操作员用户主键
     * @return 生效经办人 Practitioner 主键
     * @throws BusinessException 经办人缺失或越权时
     */
    private Long resolveCashierId(Long requestedCashierId, Long operatorId) {
        Long currentCashierId = this.resolveCurrentCashierPractitionerId(operatorId);
        if (currentCashierId == null && requestedCashierId == null) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "请选择收款人");
        }
        if (currentCashierId == null) {
            currentCashierId = requestedCashierId;
        }
        // 未指定经办人时默认当前操作员对应 Practitioner
        if (requestedCashierId == null) {
            return currentCashierId;
        }
        // 指定本人无需额外权限校验
        if (requestedCashierId.equals(currentCashierId)) {
            return requestedCashierId;
        }
        // Gate：非本人经办须具备财务代交账权限
        if (!this.isFinancialDelegateEnabled(currentCashierId)) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "请选择有效的结账日期范围");
        }
        return requestedCashierId;
    }

    /**
     * 结账日期范围 Gate：起止均须填写且止日不早于起日。
     *
     * @param dateFrom 结账日起
     * @param dateTo   结账日止
     * @throws BusinessException 范围缺失或不合法时
     */
    private void assertDateRangeValid(LocalDate dateFrom, LocalDate dateTo) {
        // Gate：结账日起止均须填写
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "请选择有效的结账日期范围");
        }
        // Gate：止日不得早于起日
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(
                OutpatientDailySettlementConstants.EX_OP_DCL_102,
                "请选择有效的结账日期范围");
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
     * 将结账日起止写入预览响应汇总块的 dateRange 展示文案。
     *
     * @param response 预览响应
     * @param dateFrom 结账日起
     * @param dateTo   结账日止
     */
    private void applyDateRangeToSummary(PreviewOutpatientDailySettlementResponse response, LocalDate dateFrom, LocalDate dateTo) {
        if (response == null || response.getSummary() == null) {
            // 无汇总块时不改写 dateRange
            return;
        }
        String rangeText = dateFrom.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER)
            + " ~ " + dateTo.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
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
        Set<Long> candidateIds = this.collectPaymentIdsFromPreviewItems(
            lockedPreview == null ? null : lockedPreview.getItems());
        for (Long paymentId : paymentIds) {
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
     * 写入 payment {@code enterer_id} 匹配白名单，兼容 Practitioner 与用户主键存量。
     *
     * @param previewQuery  日结预览/提交共用查询
     * @param sessionUserId 当前登录用户主键（存量 enterer 匹配）
     */
    private void applyEntererMatchToPreviewQuery(
        PreviewCashierSettlementQuery previewQuery,
        Long sessionUserId) {
        if (previewQuery == null || previewQuery.getCashierId() == null) {
            return;
        }
        previewQuery.setEntererMatchIds(
            this.paymentEntererMatchResolver.resolveEntererMatchIds(
                previewQuery.getCashierId(),
                sessionUserId));
    }
}
