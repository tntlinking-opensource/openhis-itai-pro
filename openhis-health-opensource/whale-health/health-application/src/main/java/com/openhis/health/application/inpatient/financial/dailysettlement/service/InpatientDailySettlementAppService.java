/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.service;

import com.openhis.health.application.inpatient.financial.dailysettlement.mapper.InpatientDailySettlementMapper;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.LoadInpatientDailySettlementPageInitRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.PreviewInpatientDailySettlementRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.SubmitInpatientDailySettlementRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.LoadInpatientDailySettlementPageInitResponse;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.PreviewInpatientDailySettlementResponse;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.SubmitInpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.support.DailySettlementPaymentDetailLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
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
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.financial.ContractExpenseNatureAssembler;
import com.openhis.health.application.shared.financial.PaymentEntererMatchResolver;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final EncounterContextReader EncounterContextReader;

    /** 经办人主数据读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 岗位人员预解析。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** Request/Response 映射。 */
    private final InpatientDailySettlementMapper inpatientDailySettlementMapper;

    /** 费用性质（合同）展示判定。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    /** payment enterer_id 匹配（Practitioner + 用户主键存量兼容）。 */
    private final PaymentEntererMatchResolver paymentEntererMatchResolver;

    /** fin_type_code 字典读侧。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** payment 项目行与支付方式 enrichment。 */
    private final DailySettlementPaymentDetailLoader dailySettlementPaymentDetailLoader;

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
            // Pre：展示汇总须含已交账流水，仅 paymentCount/items 保持未交账口径
            List<UnsettledPaymentPreviewItemQueryModel> summaryPreviewItems =
                this.paymentReconciliationQueryService.previewPaymentsForCashierSettlementSummary(previewQuery);
            Set<Long> displayPaymentIds = this.collectPaymentIdsFromPreviewItems(summaryPreviewItems);
            List<PaymentReconciliationQueryModel> displayPaymentModels =
                this.paymentReconciliationQueryService.findPaymentQueryModelsForCashierSettlementSummary(previewQuery);
            Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId =
                this.loadRecDetailsByPaymentIds(displayPaymentIds);
            List<Long> encounterIdsOrdered = this.collectEncounterIdsOrdered(displayPaymentModels);
            List<InpatientEncounterContextQueryModel> encounterContexts =
                this.EncounterContextReader.listInpatientContextsByEncounterIdsOrdered(encounterIdsOrdered);
            Map<Long, InpatientEncounterContextQueryModel> encounterByEncounterId =
                this.buildEncounterIndex(encounterContexts);
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
            // Asm：费用性质仅过滤展示，paymentCount 保持全量（按 fin_contract 判定）
            PreviewInpatientDailySettlementResponse response =
                this.inpatientDailySettlementMapper.toPreviewInpatientDailySettlementResponse(
                    previewModel,
                    summaryPreviewItems,
                    displayPaymentModels,
                    recDetailsByPaymentId,
                    chargeItemsByPaymentId,
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
        // Pre：提交前重新锁定候选集，paymentIds 须 ⊆ 未交账候选（BR-IP-DCL-018）
        PreviewCashierSettlementQueryModel locked =
            this.paymentReconciliationQueryService.previewUnsettledPaymentsForCashierSettlement(previewQuery);
        List<Long> selectedPaymentIds = request.getPaymentIds();
        this.assertPaymentIdsSubsetOfCandidates(
            selectedPaymentIds,
            locked,
            InpatientDailySettlementConstants.EX_IP_DCL_105,
            InpatientDailySettlementConstants.EX_IP_DCL_201);
        Integer paymentCount = selectedPaymentIds.size();
        // Pre：请求转领域命令
        SubmitCashierSettlementCommand command = this.inpatientDailySettlementMapper.toSubmitCashierSettlementCommand(
            request,
            CashierSettlementType.INPATIENT.getCode(),
            cashierId,
            operatorId,
            selectedPaymentIds);
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

    /**
     * 当前经办人是否具备财务代交账权限（会计或管理员岗位）。
     *
     * @param currentCashierId 当前收费员 Practitioner id
     * @return 可代交账为 true
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
     * 费用性质筛选项：自费 / 医保。
     *
     * @return 下拉选项
     */
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

    /**
     * 经办人下拉：代交账权限时加载在岗人员，否则仅本人。
     *
     * @param financialDelegateEnabled 是否可代交账
     * @param currentPractitioner      当前操作员
     * @return 经办人选项
     */
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

    /**
     * 解析日结经办人：缺省本人；指定他人须代交账权限。
     *
     * @param requestedCashierId 请求指定经办人
     * @param operatorId         登录用户 id
     * @return 有效 cashierId
     * @throws BusinessException 无法解析或越权
     */
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

    /**
     * 结账日起止必填且止日不早于起日。
     *
     * @param dateFrom 起日
     * @param dateTo   止日
     * @throws BusinessException 参数非法
     */
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

    /**
     * 从未交账预览行收集 paymentId（保序去重）。
     *
     * @param previewItems 预览行
     * @return paymentId 集合
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
     * 从预览行收集 fin_contract busNo（trim 后非空）。
     *
     * @param previewItems 预览行
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
     * 逐 payment 加载渠道明细子表。
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
     * 从 payment 读模型按出现顺序收集 encounterId（去重）。
     *
     * @param paymentModels payment 列表
     * @return 有序 encounterId
     */
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

    /**
     * 住院就诊上下文列表 → encounterId 索引。
     *
     * @param encounterContexts 就诊快照
     * @return encounterId → 上下文
     */
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
     * 将请求日期窗写入预览 summary.dateRange 展示串。
     *
     * @param response 预览响应
     * @param dateFrom 起日
     * @param dateTo   止日
     */
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
            this.paymentEntererMatchResolver.resolveEntererMatchIds(previewQuery.getCashierId()));
    }
}
