/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.mapper;

import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementChannelRowDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementPaymentItemDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementSummaryDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.PreviewInpatientDailySettlementRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.request.SubmitInpatientDailySettlementRequest;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.LoadInpatientDailySettlementPageInitResponse;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.PreviewInpatientDailySettlementResponse;
import com.openhis.health.application.inpatient.financial.dailysettlement.response.SubmitInpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.support.DailySettlementPaymentDetailLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentStaticSnapshotQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentYbSettlementQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientDailySettlementConstants;
import com.openhis.health.domain.share.financial.DailySettlementRefundPaymentRules;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.application.shared.financial.ContractExpenseNatureAssembler;
import com.openhis.health.application.shared.financial.DailySettlementExpenseNatureDetailAccumulator;
import com.openhis.health.application.shared.financial.DailySettlementExpenseNatureSummarySupport;
import com.openhis.health.application.shared.financial.DailySettlementExpenseNatureSummarySupport.NatureBucket;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.application.shared.financial.dto.DailySettlementExpenseNatureSummaryDto;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.infrastructure.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link InpatientDailySettlementMapper} 实现（AS-IP-FIN-005-SDD）。
 */
@Component
@RequiredArgsConstructor
public class InpatientDailySettlementMapperImpl implements InpatientDailySettlementMapper {

    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** payment 项目行与支付方式 enrichment。 */
    private final DailySettlementPaymentDetailLoader dailySettlementPaymentDetailLoader;

    /** 费用性质名称（bus_no → name）。 */
    private final ContractQueryService contractQueryService;

    /** 费用性质按明细账户分桶（混结：自费≠医保现金）。 */
    private final DailySettlementExpenseNatureDetailAccumulator expenseNatureDetailAccumulator;

    /** 结算回执 id → insutype。 */
    private final InsuranceSettlementRecordLookupExtension settlementRecordLookupExtension;

    /** insutype → 展示名。 */
    private final InsurancePlatformDictDisplayExtension platformDictDisplayExtension;

    /**
     * 装配住院日结页初始化响应。
     *
     * @param defaultDate              默认结账日
     * @param financialDelegateEnabled 是否允许财务代交账
     * @param expensePropertyOptions   费用性质下拉
     * @param cashierOptions           经办人下拉
     * @param currentCashierId         当前经办人
     * @return 初始化响应
     */
    @Override
    public LoadInpatientDailySettlementPageInitResponse toLoadInpatientDailySettlementPageInitResponse(
        LocalDate defaultDate,
        boolean financialDelegateEnabled,
        List<OptionDto> expensePropertyOptions,
        List<OptionDto> cashierOptions,
        Long currentCashierId) {
        LoadInpatientDailySettlementPageInitResponse response = new LoadInpatientDailySettlementPageInitResponse();
        String fromText = defaultDate.atStartOfDay().format(GeneralSharedConstants.DATE_TIME_FORMATTER);
        // DD §8.E 默认窗结束时刻 now
        String toText = LocalDateTime.now().format(GeneralSharedConstants.DATE_TIME_FORMATTER);
        response.setDefaultDateFrom(fromText);
        response.setDefaultDateTo(toText);
        response.setExpensePropertyOptions(expensePropertyOptions);
        response.setFinancialDelegateEnabled(financialDelegateEnabled);
        response.setCashierOptions(cashierOptions);
        response.setCurrentCashierId(currentCashierId);
        return response;
    }

    /**
     * 预览请求转领域查询条件。
     *
     * @param request     HTTP 请求
     * @param typeEnum    交账类型 code
     * @param locationIds 科室下 location 白名单
     * @return 领域预览查询
     */
    @Override
    public PreviewCashierSettlementQuery toPreviewCashierSettlementQuery(
        PreviewInpatientDailySettlementRequest request,
        String typeEnum,
        List<Long> locationIds) {
        PreviewCashierSettlementQuery query = new PreviewCashierSettlementQuery();
        query.setTypeEnum(typeEnum);
        query.setOrgId(request.getOrgId());
        query.setLocationIds(locationIds);
        query.setDateFrom(request.getDateFrom());
        query.setDateTo(request.getDateTo());
        return query;
    }

    /**
     * 预览结果与 enrichment 装配查询响应。
     *
     * @param previewModel             未交账候选 payment 预览（paymentCount / items）
     * @param summaryPreviewItems      日结展示口径 payment 行（含已交账，供 rows/summary）
     * @param paymentModels            展示口径 payment 读模型
     * @param recDetailsByPaymentId    子明细
     * @param encounterByEncounterId   就诊上下文
     * @param expenseProperty          费用性质展示过滤
     * @param insuranceByContractBusNo 合同 bus_no → 是否医保
     * @param finTypeOptions           fin_type_code 字典顺序
     * @return 查询响应
     */
    @Override
    public PreviewInpatientDailySettlementResponse toPreviewInpatientDailySettlementResponse(
        PreviewCashierSettlementQueryModel previewModel,
        List<UnsettledPaymentPreviewItemQueryModel> summaryPreviewItems,
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<Long, List<ChargeItem>> chargeItemsByPaymentId,
        Map<Long, InpatientEncounterContextQueryModel> encounterByEncounterId,
        String expenseProperty,
        Map<String, Boolean> insuranceByContractBusNo,
        List<OptionDto> finTypeOptions) {
        if (previewModel == null) {
            return null;
        }
        Map<Long, PaymentReconciliationQueryModel> paymentById = this.buildPaymentById(paymentModels);
        List<InpatientDailySettlementPaymentItemDto> unsettledItems =
            this.buildPaymentItems(
                previewModel.getItems(),
                insuranceByContractBusNo,
                paymentById,
                encounterByEncounterId);
        boolean chargeEmpty = chargeItemsByPaymentId == null || chargeItemsByPaymentId.isEmpty();
        if (!chargeEmpty) {
            this.dailySettlementPaymentDetailLoader.enrichInpatientPaymentItems(
                unsettledItems,
                chargeItemsByPaymentId,
                recDetailsByPaymentId);
        }
        Integer paymentCount = previewModel.getPaymentCount();
        if (paymentCount == null) {
            paymentCount = unsettledItems.size();
        }
        // Pre：展示汇总用全量口径（含已交账）；费用性质仅收窄 rows/summary
        boolean sameSource = summaryPreviewItems == previewModel.getItems();
        List<InpatientDailySettlementPaymentItemDto> summarySourceItems = sameSource
            ? unsettledItems
            : this.buildPaymentItems(
                summaryPreviewItems,
                insuranceByContractBusNo,
                paymentById,
                encounterByEncounterId);
        InpatientDisplayBundle mainDisplay = this.buildDisplayBundle(
            summarySourceItems,
            expenseProperty,
            paymentById,
            recDetailsByPaymentId,
            finTypeOptions);
        // 挂载现算 / 同源：main 与 handover 同口径，只算一遍
        InpatientDisplayBundle handoverDisplay = sameSource
            ? mainDisplay
            : this.buildDisplayBundle(
                unsettledItems,
                expenseProperty,
                paymentById,
                recDetailsByPaymentId,
                finTypeOptions);
        PreviewInpatientDailySettlementResponse response = new PreviewInpatientDailySettlementResponse();
        response.setPaymentCount(paymentCount);
        response.setItems(unsettledItems);
        response.setRows(mainDisplay.rows());
        response.setSummary(mainDisplay.summary());
        response.setHandoverRows(handoverDisplay.rows());
        response.setHandoverSummary(handoverDisplay.summary());
        return response;
    }

    /**
     * 按展示 payment 子集汇总上半区 rows 与下半区 summary（BR-IP-DCL-010/011/017）。
     *
     * @param sourceItems             汇总源 payment 行
     * @param expenseProperty         费用性质展示过滤
     * @param paymentById             payment 读模型索引
     * @param recDetailsByPaymentId   自付子明细
     * @param finTypeOptions          fin_type 字典
     * @return rows + summary 组合
     */
    private InpatientDisplayBundle buildDisplayBundle(
        List<InpatientDailySettlementPaymentItemDto> sourceItems,
        String expenseProperty,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        List<OptionDto> finTypeOptions) {
        List<InpatientDailySettlementPaymentItemDto> displayItems =
            this.filterItemsByExpenseProperty(sourceItems, expenseProperty);
        List<InpatientDailySettlementPaymentItemDto> displayItemsWithPayment =
            this.filterDisplayItemsInPaymentScope(displayItems, paymentById.keySet());
        // 工伤合同不进上半区 fin_type / 总收入 / 渠道 / 费用性质（与打印日结单口径对齐）
        List<InpatientDailySettlementPaymentItemDto> summaryItems =
            this.excludeWorkInjuryPaymentItems(displayItemsWithPayment);
        List<PaymentReconciliationQueryModel> finTypePaymentModels =
            this.resolveFinTypeAggregationPaymentModels(summaryItems, paymentById);
        List<PaymentReconciliationQueryModel> settlementPaymentModels =
            this.filterPaymentModelsExcludingDeposit(finTypePaymentModels);
        Map<Long, List<PaymentStaticSnapshotQueryModel>> staticRowsByPaymentId =
            this.paymentReconciliationQueryService.mapPaymentRecStaticsByReconciliationIds(
                this.collectPaymentIdsFromModels(settlementPaymentModels));
        Map<String, BigDecimal> finTypeAmounts =
            this.dailySettlementFinTypeAmountLoader.aggregateFinTypeAmounts(
                settlementPaymentModels,
                staticRowsByPaymentId);
        this.removeInpatientExcludedFinTypeAmounts(finTypeAmounts);
        Map<String, BigDecimal> channelAmounts =
            this.aggregateChannelAmounts(summaryItems, recDetailsByPaymentId);
        List<InpatientDailySettlementChannelRowDto> rows =
            this.toFinTypeRows(finTypeOptions, finTypeAmounts);
        InpatientDailySettlementSummaryDto summary = this.buildSummary(
            summaryItems,
            paymentById,
            channelAmounts,
            recDetailsByPaymentId,
            finTypeAmounts);
        return new InpatientDisplayBundle(rows, summary);
    }

    /**
     * 日结展示汇总结果（上半区 + 下半区）。
     *
     * @param rows    财务类别行
     * @param summary 固定汇总项
     */
    private record InpatientDisplayBundle(
        List<InpatientDailySettlementChannelRowDto> rows,
        InpatientDailySettlementSummaryDto summary) {
    }

    /**
     * 提交请求转领域命令。
     *
     * @param request            HTTP 请求
     * @param typeEnum           交账类型
     * @param submitterId        提交人 Practitioner 主键
     * @param operatorId         操作员用户主键
     * @param selectedPaymentIds 勾选挂载 payment 主键
     * @param locationIds        科室下 location 白名单
     * @param practitionerIds    窗内 enterer 快照
     * @param paymentCount       计入总收入笔数（不含工伤）
     * @param totalIncome        勾选集合日结「总收入」（与预览 handoverSummary 同管线）
     * @return 提交命令
     */
    @Override
    public SubmitCashierSettlementCommand toSubmitCashierSettlementCommand(
        SubmitInpatientDailySettlementRequest request,
        String typeEnum,
        Long submitterId,
        Long operatorId,
        List<Long> selectedPaymentIds,
        List<Long> locationIds,
        String practitionerIds,
        Integer paymentCount,
        BigDecimal totalIncome) {
        SubmitCashierSettlementCommand command = new SubmitCashierSettlementCommand();
        command.setTypeEnum(typeEnum);
        command.setOrgId(request.getOrgId());
        command.setLocationIds(locationIds);
        command.setLocationId(request.getLocationId());
        command.setSubmitterId(submitterId);
        command.setDateFrom(request.getDateFrom());
        command.setDateTo(request.getDateTo());
        command.setOperatorId(operatorId);
        command.setSelectedPaymentIds(selectedPaymentIds);
        command.setPractitionerIds(practitionerIds);
        command.setPaymentCount(paymentCount);
        command.setTotalIncome(totalIncome);
        return command;
    }

    /**
     * 按勾选 payment 现算日结「总收入」（与 IF-IP-DCL-02 {@code handoverSummary.totalIncome} 同管线）。
     *
     * <p>无费用性质筛：复用 {@link #buildDisplayBundle}（含预交金剔除与住院排除 fin_type），
     * 保证交账头表与日结预览「总收入」口径一致（**BR-IP-DCL-007/011**）。</p>
     *
     * @param lockedItems        提交前锁定的未交账预览行
     * @param selectedPaymentIds 本次勾选挂载 payment 主键
     * @return 展示刻度总收入；空选为 0
     */
    @Override
    public BigDecimal resolveSelectedSettlementTotalIncome(
        List<UnsettledPaymentPreviewItemQueryModel> lockedItems,
        List<Long> selectedPaymentIds) {
        if (selectedPaymentIds == null || selectedPaymentIds.isEmpty()) {
            return MonetaryAmountSupport.scaleDisplay(BigDecimal.ZERO);
        }
        // Pre：仅保留勾选行，与弹窗分批交账集合一致
        List<UnsettledPaymentPreviewItemQueryModel> selectedPreviewItems =
            this.filterLockedItemsBySelectedPaymentIds(lockedItems, selectedPaymentIds);
        List<PaymentReconciliationQueryModel> paymentModels =
            this.paymentReconciliationQueryService.findPaymentQueryModelsByIds(selectedPaymentIds);
        Map<Long, PaymentReconciliationQueryModel> paymentById = this.buildPaymentById(paymentModels);
        List<InpatientDailySettlementPaymentItemDto> selectedItems = this.buildPaymentItems(
            selectedPreviewItems,
            Map.of(),
            paymentById,
            Map.of());
        // Pre：expenseProperty=null、finTypeOptions 空列表——总收入不依赖字典行展示
        InpatientDisplayBundle displayBundle = this.buildDisplayBundle(
            selectedItems,
            null,
            paymentById,
            Map.of(),
            List.of());
        return this.parseSummaryTotalIncome(displayBundle.summary().getTotalIncome());
    }

    /**
     * 按勾选 payment 现算头表笔数（不含工伤，与总收入同口径）。
     *
     * @param lockedItems        提交前锁定的未交账预览行
     * @param selectedPaymentIds 本次勾选挂载 payment 主键
     * @return 不含工伤的勾选笔数；空选为 0
     */
    @Override
    public Integer resolveSelectedSettlementPaymentCount(
        List<UnsettledPaymentPreviewItemQueryModel> lockedItems,
        List<Long> selectedPaymentIds) {
        if (selectedPaymentIds == null || selectedPaymentIds.isEmpty()) {
            return 0;
        }
        List<UnsettledPaymentPreviewItemQueryModel> selectedPreviewItems =
            this.filterLockedItemsBySelectedPaymentIds(lockedItems, selectedPaymentIds);
        List<PaymentReconciliationQueryModel> paymentModels =
            this.paymentReconciliationQueryService.findPaymentQueryModelsByIds(selectedPaymentIds);
        Map<Long, PaymentReconciliationQueryModel> paymentById = this.buildPaymentById(paymentModels);
        List<InpatientDailySettlementPaymentItemDto> selectedItems = this.buildPaymentItems(
            selectedPreviewItems,
            Map.of(),
            paymentById,
            Map.of());
        return this.excludeWorkInjuryPaymentItems(selectedItems).size();
    }

    /**
     * 提交结果装配响应。
     *
     * @param settlementId 交账头主键
     * @param busNo        交账单号
     * @param paymentCount 计入总收入笔数（不含工伤）
     * @return 提交响应
     */
    @Override
    public SubmitInpatientDailySettlementResponse toSubmitInpatientDailySettlementResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount) {
        SubmitInpatientDailySettlementResponse response = new SubmitInpatientDailySettlementResponse();
        response.setSettlementId(settlementId);
        response.setBusNo(busNo);
        response.setPaymentCount(paymentCount);
        return response;
    }

    /**
     * 从锁定预览行中筛出本次勾选的 payment 行（保序）。
     *
     * @param lockedItems        提交前锁定的未交账预览行
     * @param selectedPaymentIds 勾选主键
     * @return 勾选子集；入参空时返回空列表
     */
    private List<UnsettledPaymentPreviewItemQueryModel> filterLockedItemsBySelectedPaymentIds(
        List<UnsettledPaymentPreviewItemQueryModel> lockedItems,
        List<Long> selectedPaymentIds) {
        List<UnsettledPaymentPreviewItemQueryModel> selected = new ArrayList<>();
        if (lockedItems == null || selectedPaymentIds == null || selectedPaymentIds.isEmpty()) {
            return selected;
        }
        Set<Long> selectedSet = new LinkedHashSet<>(selectedPaymentIds);
        for (UnsettledPaymentPreviewItemQueryModel item : lockedItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            if (!selectedSet.contains(item.getPaymentId())) {
                continue;
            }
            selected.add(item);
        }
        return selected;
    }

    /**
     * 解析日结 summary 总收入展示串为落库 BigDecimal（展示 2 位刻度）。
     *
     * @param totalIncomeText {@link InpatientDailySettlementSummaryDto#getTotalIncome()} 两位小数字符串
     * @return 展示刻度金额；空串为 0
     */
    private BigDecimal parseSummaryTotalIncome(String totalIncomeText) {
        if (StringUtils.isEmpty(totalIncomeText)) {
            return MonetaryAmountSupport.scaleDisplay(BigDecimal.ZERO);
        }
        return MonetaryAmountSupport.scaleDisplay(new BigDecimal(totalIncomeText.trim()));
    }

    /**
     * payment 读模型列表 → id 索引 Map（预览 enrichment 用）。
     *
     * @param paymentModels 同日窗 payment 读模型
     * @return paymentId → 读模型
     */
    private Map<Long, PaymentReconciliationQueryModel> buildPaymentById(List<PaymentReconciliationQueryModel> paymentModels) {
        Map<Long, PaymentReconciliationQueryModel> result = new HashMap<>();
        if (paymentModels == null) {
            return result;
        }
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel != null && paymentModel.getId() != null) {
                result.put(paymentModel.getId(), paymentModel);
            }
        }
        return result;
    }

    /**
     * 未交账/展示候选 preview 行 → 住院日结 payment 明细 DTO（含医保标记与病区名）。
     *
     * @param previewItems             领域 preview 行
     * @param insuranceByContractBusNo 合同 → 是否医保
     * @param paymentById              payment 读模型索引
     * @param encounterByEncounterId   就诊上下文
     * @return 明细 DTO 列表
     */
    private List<InpatientDailySettlementPaymentItemDto> buildPaymentItems(
        List<UnsettledPaymentPreviewItemQueryModel> previewItems,
        Map<String, Boolean> insuranceByContractBusNo,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<Long, InpatientEncounterContextQueryModel> encounterByEncounterId) {
        List<InpatientDailySettlementPaymentItemDto> items = new ArrayList<>();
        if (previewItems == null) {
            return items;
        }
        for (UnsettledPaymentPreviewItemQueryModel previewItem : previewItems) {
            if (previewItem == null) {
                continue;
            }
            InpatientDailySettlementPaymentItemDto item = new InpatientDailySettlementPaymentItemDto();
            item.setPaymentId(previewItem.getPaymentId());
            item.setPaymentNo(previewItem.getPaymentNo());
            item.setKindEnum(previewItem.getKindEnum());
            item.setStatusEnum(previewItem.getStatusEnum());
            item.setBillDate(previewItem.getBillDate());
            item.setPaymentEnum(previewItem.getPaymentEnum());
            item.setRelationId(previewItem.getRelationId());
            item.setTenderedAmount(previewItem.getTenderedAmount());
            item.setReturnedAmount(previewItem.getReturnedAmount());
            item.setNetAmount(previewItem.getNetAmount());
            item.setContractNo(previewItem.getContractNo());
            item.setHasInsurance(this.resolveInsuranceFlag(previewItem.getContractNo(), insuranceByContractBusNo));
            String wardName = this.resolveWardName(previewItem, paymentById, encounterByEncounterId);
            item.setWardName(wardName);
            item.setCategoryLabel(wardName);
            items.add(item);
        }
        return items;
    }

    /**
     * 解析 payment 是否医保合同（预加载 Map 优先，否则合同装配器判定）。
     *
     * @param contractBusNo              合同 bus_no
     * @param insuranceByContractBusNo   预加载医保标记
     * @return 是否医保
     */
    private boolean resolveInsuranceFlag(String contractBusNo, Map<String, Boolean> insuranceByContractBusNo) {
        if (insuranceByContractBusNo != null && StringUtils.isNotEmpty(contractBusNo)) {
            Boolean flag = insuranceByContractBusNo.get(contractBusNo.trim());
            if (flag != null) {
                return flag;
            }
        }
        return this.contractExpenseNatureAssembler.isInsuranceContractBusNo(contractBusNo);
    }

    /**
     * 解析 payment 行类别标签：预交金固定文案，否则取就诊病区名。
     *
     * @param previewItem            preview 行
     * @param paymentById            payment 读模型索引
     * @param encounterByEncounterId 就诊上下文
     * @return 病区/预交金类别展示名
     */
    private String resolveWardName(
        UnsettledPaymentPreviewItemQueryModel previewItem,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<Long, InpatientEncounterContextQueryModel> encounterByEncounterId) {
        if (this.isDepositPaymentKind(previewItem.getKindEnum())) {
            return InpatientDailySettlementConstants.DEPOSIT_CATEGORY_LABEL;
        }
        PaymentReconciliationQueryModel paymentModel = paymentById.get(previewItem.getPaymentId());
        if (paymentModel == null || paymentModel.getEncounterId() == null) {
            return InpatientDailySettlementConstants.WARD_NAME_FALLBACK;
        }
        InpatientEncounterContextQueryModel encounter = encounterByEncounterId.get(paymentModel.getEncounterId());
        if (encounter == null || StringUtils.isEmpty(encounter.getWardName())) {
            return InpatientDailySettlementConstants.WARD_NAME_FALLBACK;
        }
        return encounter.getWardName();
    }

    /**
     * 按费用性质展示过滤 payment 行（BR-IP-DCL-005；仅影响 rows/summary，不改 paymentCount）。
     *
     * @param items            候选 payment 行
     * @param expenseProperty  费用性质筛选项
     * @return 展示用子集
     */
    private List<InpatientDailySettlementPaymentItemDto> filterItemsByExpenseProperty(
        List<InpatientDailySettlementPaymentItemDto> items,
        String expenseProperty) {
        if (StringUtils.isEmpty(expenseProperty)) {
            return items;
        }
        String normalizedExpenseProperty = expenseProperty.trim();
        List<InpatientDailySettlementPaymentItemDto> filtered = new ArrayList<>();
        boolean selectedContractIsInsurance = false;
        boolean selectedContractResolved = false;
        if (!InpatientDailySettlementConstants.EXPENSE_SELF_PAY.equals(normalizedExpenseProperty)
            && !InpatientDailySettlementConstants.EXPENSE_INSURANCE.equals(normalizedExpenseProperty)) {
            selectedContractIsInsurance =
                this.contractExpenseNatureAssembler.isInsuranceContractBusNo(normalizedExpenseProperty);
            selectedContractResolved = true;
        }
        for (InpatientDailySettlementPaymentItemDto item : items) {
            if (item == null) {
                continue;
            }
            // 预交金不参与 fin_contract 费用性质过滤，始终在 summary.depositAmount 列示（BR-IP-DCL-007）
            if (this.isDepositPaymentKind(item.getKindEnum())) {
                filtered.add(item);
                continue;
            }
            boolean hasInsurance = false;
            if (item.getHasInsurance() != null) {
                hasInsurance = item.getHasInsurance();
            }
            if (InpatientDailySettlementConstants.EXPENSE_SELF_PAY.equals(normalizedExpenseProperty)) {
                if (!hasInsurance) {
                    filtered.add(item);
                }
                continue;
            }
            if (InpatientDailySettlementConstants.EXPENSE_INSURANCE.equals(normalizedExpenseProperty)) {
                if (hasInsurance) {
                    filtered.add(item);
                }
                continue;
            }
            if (selectedContractResolved && !selectedContractIsInsurance) {
                if (!hasInsurance) {
                    filtered.add(item);
                }
                continue;
            }
            String contractBusNo = item.getContractNo();
            if (contractBusNo != null && normalizedExpenseProperty.equals(contractBusNo.trim())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    /**
     * 将展示行限制在 enrichment 已加载的 paymentId 集合内（防越界汇总）。
     *
     * @param displayItems 费用性质过滤后的行
     * @param paymentIds   已加载 paymentId 集合
     * @return 交集子集
     */
    private List<InpatientDailySettlementPaymentItemDto> filterDisplayItemsInPaymentScope(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        java.util.Set<Long> paymentIds) {
        List<InpatientDailySettlementPaymentItemDto> filtered = new ArrayList<>();
        if (displayItems == null || paymentIds == null || paymentIds.isEmpty()) {
            return filtered;
        }
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            if (paymentIds.contains(item.getPaymentId())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    /**
     * 由展示行顺序提取对应 payment 读模型（去重保序）。
     *
     * @param displayItems 展示行
     * @param paymentById    payment 索引
     * @return 读模型列表
     */
    private List<PaymentReconciliationQueryModel> filterPaymentModelsForDisplayItems(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        if (displayItems == null || paymentById == null) {
            return result;
        }
        java.util.Set<Long> added = new java.util.LinkedHashSet<>();
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            if (added.contains(item.getPaymentId())) {
                continue;
            }
            PaymentReconciliationQueryModel paymentModel = paymentById.get(item.getPaymentId());
            if (paymentModel != null) {
                result.add(paymentModel);
                added.add(item.getPaymentId());
            }
        }
        return result;
    }

    /**
     * 日结汇总排除工伤合同 payment（总收入 / fin_type / 渠道 / 费用性质均不含）。
     *
     * @param items 展示行
     * @return 剔除工伤后的列表；入参空时原样或空列表
     */
    private List<InpatientDailySettlementPaymentItemDto> excludeWorkInjuryPaymentItems(
        List<InpatientDailySettlementPaymentItemDto> items) {
        if (items == null || items.isEmpty()) {
            return items == null ? List.of() : items;
        }
        Set<String> contractBusNos = this.collectContractBusNosFromPaymentItems(items);
        Map<String, Boolean> workInjuryByBusNo =
            this.contractExpenseNatureAssembler.mapWorkInjuryFlagByContractBusNoIn(contractBusNos);
        if (workInjuryByBusNo == null || workInjuryByBusNo.isEmpty()) {
            return items;
        }
        List<InpatientDailySettlementPaymentItemDto> filtered = new ArrayList<>(items.size());
        for (InpatientDailySettlementPaymentItemDto item : items) {
            if (item == null) {
                continue;
            }
            String contractBusNo = item.getContractNo();
            if (contractBusNo != null) {
                contractBusNo = contractBusNo.trim();
            }
            if (StringUtils.isNotEmpty(contractBusNo)
                && Boolean.TRUE.equals(workInjuryByBusNo.get(contractBusNo))) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    /**
     * 解析 fin_type 汇总用 payment 读模型：仅日窗内展示行本身（正负各自带快照，禁止跨窗拉逆向单）。
     *
     * @param displayItems 费用性质过滤后的展示行
     * @param paymentById  同日窗 payment 读模型索引
     * @return fin_type enrich 用 payment 列表
     */
    private List<PaymentReconciliationQueryModel> resolveFinTypeAggregationPaymentModels(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        LinkedHashMap<Long, PaymentReconciliationQueryModel> merged = new LinkedHashMap<>();
        if (displayItems != null) {
            for (InpatientDailySettlementPaymentItemDto item : displayItems) {
                if (item == null || item.getPaymentId() == null) {
                    continue;
                }
                PaymentReconciliationQueryModel paymentModel = paymentById.get(item.getPaymentId());
                if (paymentModel != null && paymentModel.getId() != null) {
                    merged.put(paymentModel.getId(), paymentModel);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 从 payment 读模型列表收集主键 id（批量查 static 快照用）。
     *
     * @param paymentModels 读模型列表
     * @return paymentId 列表
     */
    private List<Long> collectPaymentIdsFromModels(List<PaymentReconciliationQueryModel> paymentModels) {
        List<Long> paymentIds = new ArrayList<>();
        if (paymentModels == null) {
            return paymentIds;
        }
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel != null && paymentModel.getId() != null) {
                paymentIds.add(paymentModel.getId());
            }
        }
        return paymentIds;
    }

    /**
     * 按展示 payment 行汇总自付渠道金额（排除统筹/个账，带逆向符号）。
     *
     * @param displayItems          展示行
     * @param recDetailsByPaymentId payment → 子明细
     * @return 渠道展示名 → 带符号合计
     */
    private Map<String, BigDecimal> aggregateChannelAmounts(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId) {
        Map<String, BigDecimal> channelAmounts = new LinkedHashMap<>();
        if (displayItems == null || recDetailsByPaymentId == null) {
            return channelAmounts;
        }
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            // 预交金渠道独立归 depositAmount，不混入微信/现金等下半区渠道小计（BR-IP-DCL-007）
            if (this.isDepositPaymentKind(item.getKindEnum())) {
                continue;
            }
            List<PaymentRecDetailQueryModel> details = recDetailsByPaymentId.get(item.getPaymentId());
            if (details == null) {
                continue;
            }
            for (PaymentRecDetailQueryModel detail : details) {
                if (detail == null || this.isExcludedFromSelfPayChannelAggregate(detail.getPayEnum())) {
                    continue;
                }
                String category =
                    this.paymentChannelDisplayAssembler.resolveDailySettlementSelfPayCategory(detail.getPayEnum());
                if (StringUtils.isEmpty(category)) {
                    category = detail.getPayEnum();
                }
                BigDecimal lineAmount = this.resolveSignedDetailNetAmount(detail, item);
                BigDecimal existing = channelAmounts.get(category);
                if (existing == null) {
                    existing = BigDecimal.ZERO;
                }
                channelAmounts.put(category, existing.add(lineAmount));
            }
        }
        return channelAmounts;
    }

    /**
     * 住院日结上半区 rows：按 fin_type 字典顺序输出，排除门诊号源类（挂号费/诊察费）。
     *
     * @param finTypeOptions  fin_type_code 字典顺序
     * @param finTypeAmounts  已剔除预交金与门诊号源类后的合计
     * @return 上半区财务类别行
     */
    private List<InpatientDailySettlementChannelRowDto> toFinTypeRows(
        List<OptionDto> finTypeOptions,
        Map<String, BigDecimal> finTypeAmounts) {
        List<InpatientDailySettlementChannelRowDto> rows = new ArrayList<>();
        if (finTypeOptions == null) {
            return rows;
        }
        for (OptionDto option : finTypeOptions) {
            if (option == null || StringUtils.isEmpty(option.getValue())) {
                continue;
            }
            String typeCode = option.getValue().trim();
            if (this.isInpatientExcludedFinTypeCode(typeCode)) {
                continue;
            }
            BigDecimal amount = BigDecimal.ZERO;
            if (finTypeAmounts != null) {
                BigDecimal resolved = finTypeAmounts.get(typeCode);
                if (resolved != null) {
                    amount = resolved;
                }
            }
            InpatientDailySettlementChannelRowDto row = new InpatientDailySettlementChannelRowDto();
            row.setCategoryCode(typeCode);
            row.setCategory(option.getLabel());
            row.setAmount(this.formatAmount(amount));
            row.setUnit(InpatientDailySettlementConstants.AMOUNT_UNIT_YUAN);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 是否医保支付渠道（统筹基金或个账）。
     *
     * @param payEnum 支付渠道 code
     * @return 医保渠道为 true
     */
    private boolean isInsurancePayChannel(String payEnum) {
        if (StringUtils.isEmpty(payEnum)) {
            return false;
        }
        PaymentChannel channel = PaymentChannel.fromCode(payEnum.trim());
        if (channel == null) {
            return false;
        }
        if (PaymentChannel.INSURANCE_FUND.equals(channel)) {
            return true;
        }
        return PaymentChannel.INSURANCE_ACCT.equals(channel);
    }

    /**
     * 是否应从日结自付渠道小计排除（统筹/个账/预交金冲抵）。
     *
     * @param payEnum 支付渠道 code
     * @return 排除为 true
     */
    private boolean isExcludedFromSelfPayChannelAggregate(String payEnum) {
        if (this.isInsurancePayChannel(payEnum)) {
            return true;
        }
        if (StringUtils.isEmpty(payEnum)) {
            return false;
        }
        return PaymentChannel.DEPOSIT_OFFSET.getCode().equals(payEnum.trim());
    }

    /**
     * 单条支付子明细净额（amount − returnAmount）。
     *
     * @param detail 子明细
     * @return 行净额
     */
    private BigDecimal resolveDetailNetAmount(PaymentRecDetailQueryModel detail) {
        BigDecimal amount = detail.getAmount();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        BigDecimal returnAmount = detail.getReturnAmount();
        if (returnAmount == null) {
            returnAmount = BigDecimal.ZERO;
        }
        return amount.subtract(returnAmount);
    }

    /**
     * 子明细带符号净额：逆向反结/退费单（UN_PAY 或 relationId 指向原单）在渠道/现金汇总中取负。
     *
     * @param detail 自付子明细
     * @param item   所属 payment 候选行
     * @return 带符号行净额
     */
    private BigDecimal resolveSignedDetailNetAmount(
        PaymentRecDetailQueryModel detail,
        InpatientDailySettlementPaymentItemDto item) {
        BigDecimal lineAmount = this.resolveDetailNetAmount(detail);
        if (this.isReversePayment(item)) {
            // 逆向流水：现金/渠道流出，与正向结算代数和抵净
            return lineAmount.abs().negate();
        }
        return lineAmount;
    }

    /**
     * 是否逆向反结/退费 payment（relationId / UN_PAY / REFUNDED；**BR-IP-DCL-011**）。
     *
     * @param item 候选 payment 行
     * @return 是否逆向流水
     */
    private boolean isReversePayment(InpatientDailySettlementPaymentItemDto item) {
        if (item == null) {
            return false;
        }
        return DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            item.getPaymentEnum(),
            item.getRelationId(),
            item.getStatusEnum());
    }

    /**
     * 装配下半区固定汇总项；总收入 = fin_type 上半区代数和（**不含**预交金）。
     *
     * <p>预交金仅在 {@code depositAmount} 独立汇总；多退 {@code depositRefundAmount}、少补
     * {@code settleSupplementAmount} 同为独立展示项，均不参与总收入、渠道、人次、医保分项等下半区加和
     * （**BR-IP-DCL-007** / **BR-IP-DCL-029**）。</p>
     *
     * @param displayItems          展示过滤后的候选行
     * @param paymentById           paymentId → 支付读模型（人次去重取 patientId）
     * @param channelAmounts        自付渠道带符号合计（已剔除预交金）
     * @param recDetailsByPaymentId paymentId → 自付子明细
     * @param finTypeAmounts        fin_type_code → 带符号合计
     * @return 下半区汇总 DTO
     */
    private InpatientDailySettlementSummaryDto buildSummary(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<String, BigDecimal> channelAmounts,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<String, BigDecimal> finTypeAmounts) {
        InpatientDailySettlementSummaryDto summary = new InpatientDailySettlementSummaryDto();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        BigDecimal depositAmount = BigDecimal.ZERO;
        BigDecimal depositRefundAmount = BigDecimal.ZERO;
        BigDecimal settleSupplementAmount = BigDecimal.ZERO;
        Set<String> contractBusNos = this.collectContractBusNosFromPaymentItems(displayItems);
        DailySettlementExpenseNatureDetailAccumulator.PreparedMaps natureMaps =
            this.expenseNatureDetailAccumulator.prepare(contractBusNos, recDetailsByPaymentId);
        Map<Long, String> insutypeBySettlementRecordId =
            this.loadInsutypeBySettlementRecordId(displayItems, paymentById);
        Map<Long, String> insutypeByPaymentId =
            this.loadInsutypeByPaymentId(displayItems, paymentById);
        Map<Long, BigDecimal> retailUploadDiffBySettlementRecordId =
            this.loadRetailUploadDiffBySettlementRecordId(displayItems, paymentById);
        Map<Long, BigDecimal> retailUploadDiffByPaymentId =
            this.loadRetailUploadDiffByPaymentId(displayItems, paymentById);
        Map<String, String> insutypeLabelByCode = this.loadInsutypeLabels(
            this.mergeInsutypeCodes(insutypeBySettlementRecordId.values(), insutypeByPaymentId.values()));
        Map<String, NatureBucket> natureByContractNo = new LinkedHashMap<>();
        BigDecimal retailUploadDiffTotal = BigDecimal.ZERO;
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null) {
                continue;
            }
            // 预交金仅汇总至 depositAmount；逆向再计入退预交金（多退，BR-IP-DCL-029）
            if (this.isDepositPaymentKind(item.getKindEnum())) {
                BigDecimal signedDeposit = this.resolveSignedPaymentNetAmount(item);
                depositAmount = depositAmount.add(signedDeposit);
                if (this.isReversePayment(item)) {
                    // 退预交金列展示「退了多少」为正数；窗内各笔逆向预交金绝对值累加
                    depositRefundAmount = depositRefundAmount.add(signedDeposit.abs());
                }
                continue;
            }
            // 少补：结算单自付渠道实付代数和（现金/银联等；非补预交金；DD 的 C）
            settleSupplementAmount = settleSupplementAmount.add(
                this.resolveSettleSupplementLineAmount(item, recDetailsByPaymentId));
            BigDecimal netAmount = item.getNetAmount();
            if (netAmount == null) {
                netAmount = BigDecimal.ZERO;
            }
            totalIncome = totalIncome.add(netAmount);
            BigDecimal returned = item.getReturnedAmount();
            if (returned == null) {
                returned = BigDecimal.ZERO;
            }
            if (this.isReversePayment(item)) {
                refundTotal = refundTotal.add(netAmount.abs());
            } else {
                refundTotal = refundTotal.add(returned);
            }
            boolean hasInsurance = false;
            if (item.getHasInsurance() != null) {
                hasInsurance = item.getHasInsurance();
            }
            PaymentReconciliationQueryModel paymentModel = null;
            if (paymentById != null && item.getPaymentId() != null) {
                paymentModel = paymentById.get(item.getPaymentId());
            }
            boolean activeInsuranceSettlement = this.hasActiveInsuranceSettlement(paymentModel, paymentById);
            Long personKey = this.resolveSettlementPersonKey(item, paymentById);
            String contractBusNo = item.getContractNo();
            if (contractBusNo != null) {
                contractBusNo = contractBusNo.trim();
            }
            boolean workInjuryContract = natureMaps.isWorkInjuryContractNo(contractBusNo);
            String insutype = this.resolvePaymentInsutype(
                paymentModel, paymentById, insutypeBySettlementRecordId, insutypeByPaymentId);
            String insutypeName = DailySettlementExpenseNatureSummarySupport.resolveInsutypeDisplayName(
                insutype, insutypeLabelByCode);
            // Pre：零售−上传分差带符号累加（逆向 UN_PAY 取负，与 BR-IP-DCL-011 同精神）；工伤不计入
            if (hasInsurance && !workInjuryContract) {
                BigDecimal lineDiff = this.resolvePaymentRetailUploadDiff(
                    paymentModel,
                    paymentById,
                    retailUploadDiffBySettlementRecordId,
                    retailUploadDiffByPaymentId);
                if (lineDiff != null) {
                    if (this.isReversePayment(item)) {
                        retailUploadDiffTotal = retailUploadDiffTotal.add(lineDiff.negate());
                    } else {
                        retailUploadDiffTotal = retailUploadDiffTotal.add(lineDiff);
                    }
                }
            }
            // 费用性质：工伤合同整笔跳过（不进块、不进医保统筹）
            if (workInjuryContract) {
                continue;
            }
            // 费用性质：按明细账户分桶（混结自费→自费；医保账户 CASH→医保现金）
            this.accumulateExpenseNatureForPayment(
                natureByContractNo,
                natureMaps,
                item,
                paymentModel,
                paymentById,
                recDetailsByPaymentId,
                contractBusNo,
                hasInsurance,
                personKey,
                activeInsuranceSettlement,
                insutype,
                insutypeName,
                netAmount);
        }
        BigDecimal finTypeTotal = this.sumFinTypeAmounts(finTypeAmounts);
        if (finTypeAmounts != null && !finTypeAmounts.isEmpty()) {
            // 有 fin_type 上半区时，总收入与上半区代数和一致（不含预交金，BR-IP-DCL-007）
            totalIncome = finTypeTotal;
        }
        String totalIncomeText = this.formatAmount(totalIncome);
        String wechatText = this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.WECHAT.getDisplay()));
        String alipayText = this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.ALIPAY.getDisplay()));
        String onlineBankText = this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.BANK_CARD.getDisplay()));
        String cashText = this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.CASH.getDisplay()));
        String depositAmountText = this.formatAmount(depositAmount);
        String depositRefundAmountText = this.formatAmount(depositRefundAmount);
        String settleSupplementAmountText = this.formatAmount(settleSupplementAmount);
        summary.setTotalIncome(totalIncomeText);
        summary.setReceivableAmount(totalIncomeText);
        summary.setActualAmount(totalIncomeText);
        summary.setDiscountAmount(DailySettlementSharedConstants.DISCOUNT_AMOUNT_ZERO);
        summary.setWechatAmount(wechatText);
        summary.setAlipayAmount(alipayText);
        summary.setOnlineBankAmount(onlineBankText);
        summary.setCashAmount(cashText);
        String insuranceCompensationText =
            DailySettlementExpenseNatureSummarySupport.sumInsuranceCompensationAmount(natureByContractNo);
        String insuranceAccountText =
            DailySettlementExpenseNatureSummarySupport.sumInsuranceAccountAmount(natureByContractNo);
        summary.setCashDueAmount(cashText);
        summary.setInsuranceCompensationAmount(insuranceCompensationText);
        summary.setInsuranceAccountAmount(insuranceAccountText);
        String retailUploadDiffText = this.formatAmount(retailUploadDiffTotal);
        summary.setChsRetailUploadDiffAmount(retailUploadDiffText);
        summary.setDepositAmount(depositAmountText);
        summary.setDepositRefundAmount(depositRefundAmountText);
        summary.setSettleSupplementAmount(settleSupplementAmountText);
        summary.setRefundTotal(this.formatAmount(refundTotal));
        List<DailySettlementExpenseNatureSummaryDto> expenseNatureSummaries =
            DailySettlementExpenseNatureSummarySupport.toExpenseNatureSummaries(natureByContractNo);
        summary.setExpenseNatureSummaries(expenseNatureSummaries);
        summary.setSummaryRows(DailySettlementExpenseNatureSummarySupport.buildSummaryRows(
            totalIncomeText,
            wechatText,
            alipayText,
            onlineBankText,
            cashText,
            insuranceCompensationText,
            insuranceAccountText,
            retailUploadDiffText,
            depositAmountText,
            depositRefundAmountText,
            settleSupplementAmountText,
            expenseNatureSummaries,
            null));
        return summary;
    }

    /**
     * 结算补收行额（少补）：本笔住院结算单上自付渠道带符号净额合计（DD 的 C）。
     *
     * <p>少补不是补预交金，而是结算时按收银渠道实收（现金/微信/支付宝/银行卡等）；
     * 排除统筹、个账、预交金冲抵。R2/R3 无渠道实付时为 0；逆向反结取负。</p>
     *
     * @param item                  住院结算候选行（非预交金 kind）
     * @param recDetailsByPaymentId paymentId → 子明细
     * @return 本笔结算渠道实付；无明细为 0
     */
    private BigDecimal resolveSettleSupplementLineAmount(
        InpatientDailySettlementPaymentItemDto item,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId) {
        if (item == null || item.getPaymentId() == null || recDetailsByPaymentId == null) {
            return BigDecimal.ZERO;
        }
        List<PaymentRecDetailQueryModel> details = recDetailsByPaymentId.get(item.getPaymentId());
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal channelSum = BigDecimal.ZERO;
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail == null) {
                continue;
            }
            String payEnum = detail.getPayEnum();
            if (StringUtils.isEmpty(payEnum)) {
                continue;
            }
            String payCode = payEnum.trim();
            // 统筹/个账/冲抵不属于结算渠道实收（少补）
            if (this.isExcludedFromSelfPayChannelAggregate(payCode)) {
                continue;
            }
            channelSum = channelSum.add(this.resolveSignedDetailNetAmount(detail, item));
        }
        return channelSum;
    }

    /**
     * 解析日结人次去重键：优先 patientId，其次 encounterId，最后回落 paymentId。
     *
     * @param item          候选 payment 行
     * @param paymentById   paymentId → 支付读模型
     * @return 去重键；无法解析时返回 null
     */
    private Long resolveSettlementPersonKey(
        InpatientDailySettlementPaymentItemDto item,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        if (item == null || item.getPaymentId() == null) {
            return null;
        }
        PaymentReconciliationQueryModel paymentModel = null;
        if (paymentById != null) {
            paymentModel = paymentById.get(item.getPaymentId());
        }
        if (paymentModel != null && paymentModel.getPatientId() != null) {
            return paymentModel.getPatientId();
        }
        if (paymentModel != null && paymentModel.getEncounterId() != null) {
            return paymentModel.getEncounterId();
        }
        return item.getPaymentId();
    }

    /**
     * 是否存在有效（非全部撤销）的支付侧医保桥表行。
     *
     * <p>逆向单以原单桥表为准；逆向自身空桥表不再兼容为「可计」。
     * 全 REVOKED 时：窗内无对冲的正向 / 跨窗逆向仍按<strong>交账时刻口径</strong>视为有效
     * （交账后跨窗退费打成事后 REVOKED，不改已挂载交账单现算）。</p>
     */
    private boolean hasActiveInsuranceSettlement(
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        PaymentReconciliationQueryModel authority =
            this.resolveInsuranceAuthorityPayment(paymentModel, paymentById);
        if (authority == null) {
            return false;
        }
        List<PaymentYbSettlementQueryModel> ybRows = authority.getYbSettlements();
        if (ybRows == null || ybRows.isEmpty()) {
            return !this.isReversePaymentModel(paymentModel);
        }
        if (PaymentYbSettlementSupport.hasConfirmed(ybRows)) {
            return true;
        }
        boolean reverse = this.isReversePaymentModel(paymentModel);
        boolean originalOutside = this.isOriginalPaymentOutsideSettlementWindow(paymentModel, paymentById);
        return this.shouldCountWhenYbRevoked(reverse, originalOutside, paymentModel, paymentById);
    }

    /**
     * 医保基金/个账明细是否计入日结桶。
     *
     * <p>窗内原单 setl 已全 REVOKED 且存在对冲退费：正逆向成对跳过（净额 0）。
     * <b>原单不在本窗的 UN_PAY</b>：须计入负向统筹/个账（对齐门诊 **BR-OP-DCL-011** / **BR-IP-DCL-011**）。
     * <b>正向本窗、退费他窗</b>（交账后跨窗退费致回执事后 REVOKED）：本窗仍按<strong>交账时刻口径</strong>计入。</p>
     */
    private boolean shouldCountInsuranceDetail(
        PaymentRecDetailQueryModel detail,
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        boolean reverse = this.isReversePaymentModel(paymentModel);
        boolean originalOutside = this.isOriginalPaymentOutsideSettlementWindow(paymentModel, paymentById);
        PaymentReconciliationQueryModel authority =
            this.resolveInsuranceAuthorityPayment(paymentModel, paymentById);
        if (authority == null) {
            // 无桥表权威：历史正向兼容计入；跨窗逆向须计入负向统筹/个账
            return !reverse || originalOutside;
        }
        List<PaymentYbSettlementQueryModel> ybRows = authority.getYbSettlements();
        if (ybRows == null || ybRows.isEmpty()) {
            // 空桥表：历史正向兼容；跨窗逆向须计入
            return !reverse || originalOutside;
        }
        if (!PaymentYbSettlementSupport.hasConfirmed(ybRows)) {
            // 全 REVOKED：窗内成对跳过；跨窗逆向计负；正向无窗内对冲则交账时刻仍计
            return this.shouldCountWhenYbRevoked(reverse, originalOutside, paymentModel, paymentById);
        }
        if (detail == null || StringUtils.isEmpty(detail.getPayTransNo())) {
            return true;
        }
        String transNo = detail.getPayTransNo().trim();
        boolean matchedRevoked = false;
        boolean matchedActive = false;
        for (PaymentYbSettlementQueryModel ybRow : ybRows) {
            if (ybRow == null || StringUtils.isEmpty(ybRow.getSetlId())) {
                continue;
            }
            if (!transNo.equals(ybRow.getSetlId().trim())) {
                continue;
            }
            if (PaymentYbSettlementStatus.REVOKED.equals(ybRow.getStatus())) {
                matchedRevoked = true;
            } else {
                matchedActive = true;
            }
        }
        if (matchedRevoked && !matchedActive) {
            return this.shouldCountWhenYbRevoked(reverse, originalOutside, paymentModel, paymentById);
        }
        return true;
    }

    /**
     * 桥表已无 CONFIRMED（或本行 setl 仅 REVOKED）时，是否仍计入医保统筹/个账。
     */
    private boolean shouldCountWhenYbRevoked(
        boolean reverse,
        boolean originalOutside,
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        if (reverse) {
            return originalOutside;
        }
        return !this.hasInWindowRefundForPayment(paymentModel, paymentById);
    }

    /**
     * 本窗 / 挂载集合内是否存在指向该正向 payment 的逆向退费单。
     */
    private boolean hasInWindowRefundForPayment(
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        if (paymentModel == null || paymentModel.getId() == null
            || paymentById == null || paymentById.isEmpty()) {
            return false;
        }
        Long paymentId = paymentModel.getId();
        for (PaymentReconciliationQueryModel candidate : paymentById.values()) {
            if (candidate == null || !this.isReversePaymentModel(candidate)) {
                continue;
            }
            if (paymentId.equals(candidate.getRelationId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 逆向单的原支付是否不在本窗 payment 索引中（跨日/跨交账退费）。
     *
     * @param paymentModel 当前 payment（多为 UN_PAY）
     * @param paymentById  本窗 payment 索引
     * @return true=原单不在本窗或无 relationId，须本窗自计负向医保明细
     */
    private boolean isOriginalPaymentOutsideSettlementWindow(
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        if (paymentModel == null || !this.isReversePaymentModel(paymentModel)) {
            return false;
        }
        Long relationId = paymentModel.getRelationId();
        if (relationId == null) {
            return true;
        }
        if (paymentById == null) {
            return true;
        }
        return !paymentById.containsKey(relationId);
    }

    /**
     * 批量加载 settlementRecordId → insutype。
     */
    private Map<Long, String> loadInsutypeBySettlementRecordId(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        Set<Long> recordIds = new LinkedHashSet<>();
        if (displayItems == null || paymentById == null) {
            return this.settlementRecordLookupExtension.mapInsutypeBySettlementRecordIds(recordIds);
        }
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            PaymentReconciliationQueryModel paymentModel = paymentById.get(item.getPaymentId());
            PaymentReconciliationQueryModel authority =
                this.resolveInsuranceAuthorityPayment(paymentModel, paymentById);
            if (authority == null || authority.getYbSettlements() == null) {
                continue;
            }
            for (PaymentYbSettlementQueryModel ybRow : authority.getYbSettlements()) {
                if (ybRow == null || ybRow.getSettlementRecordId() == null) {
                    continue;
                }
                // 含 REVOKED：交账时刻口径现算仍可能读已撤销回执险种
                recordIds.add(ybRow.getSettlementRecordId());
            }
        }
        return this.settlementRecordLookupExtension.mapInsutypeBySettlementRecordIds(recordIds);
    }

    /**
     * 批量加载 paymentId → insutype（桥表缺 settlement_record_id 时回落）。
     */
    private Map<Long, String> loadInsutypeByPaymentId(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        Set<Long> paymentIds = new LinkedHashSet<>();
        if (displayItems == null) {
            return this.settlementRecordLookupExtension.mapInsutypeByPaymentIds(paymentIds);
        }
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            paymentIds.add(item.getPaymentId());
            if (paymentById != null) {
                PaymentReconciliationQueryModel paymentModel = paymentById.get(item.getPaymentId());
                PaymentReconciliationQueryModel authority =
                    this.resolveInsuranceAuthorityPayment(paymentModel, paymentById);
                if (authority != null && authority.getId() != null) {
                    paymentIds.add(authority.getId());
                }
            }
        }
        return this.settlementRecordLookupExtension.mapInsutypeByPaymentIds(paymentIds);
    }

    /**
     * 批量加载结算回执零售−上传分差（按桥表 settlement_record_id）。
     *
     * @param displayItems 日结展示行
     * @param paymentById  paymentId → 支付读模型
     * @return settlementRecordId → 分差
     */
    private Map<Long, BigDecimal> loadRetailUploadDiffBySettlementRecordId(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        Set<Long> recordIds = new LinkedHashSet<>();
        if (displayItems == null) {
            return this.settlementRecordLookupExtension.mapRetailUploadDiffBySettlementRecordIds(recordIds);
        }
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null || paymentById == null) {
                continue;
            }
            PaymentReconciliationQueryModel paymentModel = paymentById.get(item.getPaymentId());
            PaymentReconciliationQueryModel authority =
                this.resolveInsuranceAuthorityPayment(paymentModel, paymentById);
            if (authority == null || authority.getYbSettlements() == null) {
                continue;
            }
            for (PaymentYbSettlementQueryModel ybRow : authority.getYbSettlements()) {
                if (ybRow == null || ybRow.getSettlementRecordId() == null) {
                    continue;
                }
                recordIds.add(ybRow.getSettlementRecordId());
            }
        }
        return this.settlementRecordLookupExtension.mapRetailUploadDiffBySettlementRecordIds(recordIds);
    }

    /**
     * 批量加载结算回执零售−上传分差（按 paymentId 回落）。
     *
     * @param displayItems 日结展示行
     * @param paymentById  paymentId → 支付读模型
     * @return paymentId → 分差
     */
    private Map<Long, BigDecimal> loadRetailUploadDiffByPaymentId(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        Set<Long> paymentIds = new LinkedHashSet<>();
        if (displayItems == null) {
            return this.settlementRecordLookupExtension.mapRetailUploadDiffByPaymentIds(paymentIds);
        }
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            paymentIds.add(item.getPaymentId());
            if (paymentById != null) {
                PaymentReconciliationQueryModel paymentModel = paymentById.get(item.getPaymentId());
                PaymentReconciliationQueryModel authority =
                    this.resolveInsuranceAuthorityPayment(paymentModel, paymentById);
                if (authority != null && authority.getId() != null) {
                    paymentIds.add(authority.getId());
                }
            }
        }
        return this.settlementRecordLookupExtension.mapRetailUploadDiffByPaymentIds(paymentIds);
    }

    /**
     * 解析 payment 零售−上传分差：桥表 settlement_record_id 全部非撤销回执代数和 → 回落 paymentId。
     *
     * <p>一笔 payment 可挂多条 CONFIRMED 回执（如拆单结算）；**BR-IP-DCL-028** 要求带符号代数和，
     * 禁止只取首条导致上传差额与渠道勾稽差一截。</p>
     *
     * @param paymentModel                          当前支付读模型
     * @param paymentById                           paymentId → 支付读模型
     * @param retailUploadDiffBySettlementRecordId  回执分差表
     * @param retailUploadDiffByPaymentId           支付分差表
     * @return 分差；未命中 null
     */
    private BigDecimal resolvePaymentRetailUploadDiff(
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<Long, BigDecimal> retailUploadDiffBySettlementRecordId,
        Map<Long, BigDecimal> retailUploadDiffByPaymentId) {
        PaymentReconciliationQueryModel authority =
            this.resolveInsuranceAuthorityPayment(paymentModel, paymentById);
        // 交账后跨窗退费致桥表事后 REVOKED：仍按交账时刻计入上传差额（与 shouldCountInsuranceDetail 同口径）
        boolean includeRevokedUploadDiff = false;
        if (authority != null && authority.getYbSettlements() != null
            && !PaymentYbSettlementSupport.hasConfirmed(authority.getYbSettlements())) {
            boolean reverse = this.isReversePaymentModel(paymentModel);
            boolean originalOutside = this.isOriginalPaymentOutsideSettlementWindow(paymentModel, paymentById);
            includeRevokedUploadDiff =
                this.shouldCountWhenYbRevoked(reverse, originalOutside, paymentModel, paymentById);
        }
        if (authority != null && authority.getYbSettlements() != null
            && retailUploadDiffBySettlementRecordId != null) {
            BigDecimal sum = null;
            for (PaymentYbSettlementQueryModel ybRow : authority.getYbSettlements()) {
                if (ybRow == null || ybRow.getSettlementRecordId() == null) {
                    continue;
                }
                if (PaymentYbSettlementStatus.REVOKED.equals(ybRow.getStatus())
                    && !includeRevokedUploadDiff) {
                    continue;
                }
                BigDecimal diff = retailUploadDiffBySettlementRecordId.get(ybRow.getSettlementRecordId());
                if (diff == null) {
                    continue;
                }
                if (sum == null) {
                    sum = diff;
                } else {
                    sum = sum.add(diff);
                }
            }
            if (sum != null) {
                return sum;
            }
        }
        if (retailUploadDiffByPaymentId != null) {
            if (authority != null && authority.getId() != null) {
                BigDecimal byAuthority = retailUploadDiffByPaymentId.get(authority.getId());
                if (byAuthority != null) {
                    return byAuthority;
                }
            }
            if (paymentModel != null && paymentModel.getId() != null) {
                return retailUploadDiffByPaymentId.get(paymentModel.getId());
            }
        }
        return null;
    }

    /**
     * 合并多路险种码集合，供字典批量翻译。
     */
    private Iterable<String> mergeInsutypeCodes(Iterable<String> first, Iterable<String> second) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (first != null) {
            for (String code : first) {
                if (StringUtils.isNotEmpty(code)) {
                    codes.add(code.trim());
                }
            }
        }
        if (second != null) {
            for (String code : second) {
                if (StringUtils.isNotEmpty(code)) {
                    codes.add(code.trim());
                }
            }
        }
        return codes;
    }

    /**
     * 批量解析险种展示名。
     */
    private Map<String, String> loadInsutypeLabels(Iterable<String> insutypeCodes) {
        Set<String> codes = new LinkedHashSet<>();
        if (insutypeCodes != null) {
            for (String code : insutypeCodes) {
                if (StringUtils.isNotEmpty(code)) {
                    codes.add(code.trim());
                }
            }
        }
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> labels = this.platformDictDisplayExtension.lookupInsutypeLabels(codes);
        return labels == null ? Map.of() : labels;
    }

    /**
     * 解析 payment 主险种：桥表 settlement_record_id → 回落 paymentId 查回执。
     */
    private String resolvePaymentInsutype(
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<Long, String> insutypeBySettlementRecordId,
        Map<Long, String> insutypeByPaymentId) {
        PaymentReconciliationQueryModel authority =
            this.resolveInsuranceAuthorityPayment(paymentModel, paymentById);
        // 交账时刻口径：全 REVOKED 且仍应计时，允许从已撤销回执读险种
        boolean includeRevokedInsutype = false;
        if (authority != null && authority.getYbSettlements() != null
            && !PaymentYbSettlementSupport.hasConfirmed(authority.getYbSettlements())) {
            boolean reverse = this.isReversePaymentModel(paymentModel);
            boolean originalOutside = this.isOriginalPaymentOutsideSettlementWindow(paymentModel, paymentById);
            includeRevokedInsutype =
                this.shouldCountWhenYbRevoked(reverse, originalOutside, paymentModel, paymentById);
        }
        if (authority != null && authority.getYbSettlements() != null
            && insutypeBySettlementRecordId != null) {
            for (PaymentYbSettlementQueryModel ybRow : authority.getYbSettlements()) {
                if (ybRow == null || ybRow.getSettlementRecordId() == null) {
                    continue;
                }
                if (PaymentYbSettlementStatus.REVOKED.equals(ybRow.getStatus())
                    && !includeRevokedInsutype) {
                    continue;
                }
                String insutype = insutypeBySettlementRecordId.get(ybRow.getSettlementRecordId());
                // 本行未命中：继续试后续桥表行，禁止首 miss 即放弃
                if (StringUtils.isNotEmpty(insutype)) {
                    return insutype.trim();
                }
            }
        }
        if (insutypeByPaymentId != null) {
            if (authority != null && authority.getId() != null) {
                String byAuthority = insutypeByPaymentId.get(authority.getId());
                if (StringUtils.isNotEmpty(byAuthority)) {
                    return byAuthority.trim();
                }
            }
            if (paymentModel != null && paymentModel.getId() != null) {
                String byPayment = insutypeByPaymentId.get(paymentModel.getId());
                if (StringUtils.isNotEmpty(byPayment)) {
                    return byPayment.trim();
                }
            }
        }
        return "";
    }

    /**
     * 一笔 payment 的费用性质累加。
     *
     * <p>纯自费/纯医保：payment.contract_no 整笔归集（原医保现金不动）。
     * 自费+医保混结：按明细账户分桶（挂自费户→自费，挂医保户的现金补差→医保现金）。</p>
     *
     * @param natureByContractNo        费用性质桶
     * @param natureMaps                账户/合同预载
     * @param item                      展示行
     * @param paymentModel              支付读模型
     * @param paymentById               payment 索引
     * @param recDetailsByPaymentId     子明细
     * @param paymentContractNo         payment.contract_no
     * @param paymentHasInsurance       payment 级医保标记
     * @param personKey                 人次键
     * @param activeInsuranceSettlement 有效医保结算
     * @param insutype                  险种码
     * @param insutypeName              险种名
     * @param netAmount                 payment 净额
     */
    private void accumulateExpenseNatureForPayment(
        Map<String, NatureBucket> natureByContractNo,
        DailySettlementExpenseNatureDetailAccumulator.PreparedMaps natureMaps,
        InpatientDailySettlementPaymentItemDto item,
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        String paymentContractNo,
        boolean paymentHasInsurance,
        Long personKey,
        boolean activeInsuranceSettlement,
        String insutype,
        String insutypeName,
        BigDecimal netAmount
    ) {
        if (item == null || item.getPaymentId() == null) {
            return;
        }
        List<PaymentRecDetailQueryModel> details = null;
        if (recDetailsByPaymentId != null) {
            details = recDetailsByPaymentId.get(item.getPaymentId());
        }
        Map<Long, BigDecimal> signedByDetailId = new HashMap<>();
        if (details != null) {
            for (PaymentRecDetailQueryModel detail : details) {
                if (detail == null || detail.getId() == null) {
                    continue;
                }
                signedByDetailId.put(detail.getId(), this.resolveSignedDetailNetAmount(detail, item));
            }
        }
        // 仅混结按账户拆；非混结保持原 payment 级医保现金口径
        if (this.expenseNatureDetailAccumulator.isMixedSelfPayAndInsurancePayment(
            details, natureMaps, paymentContractNo)
        ) {
            boolean accumulated = this.expenseNatureDetailAccumulator.accumulateMixedPaymentByDetailAccounts(
                natureByContractNo,
                natureMaps,
                details,
                signedByDetailId,
                paymentContractNo,
                personKey,
                activeInsuranceSettlement,
                insutype,
                insutypeName,
                detail -> this.shouldCountInsuranceDetail(detail, paymentModel, paymentById));
            if (accumulated) {
                return;
            }
        }
        this.expenseNatureDetailAccumulator.accumulateSingleNaturePayment(
            natureByContractNo,
            natureMaps,
            paymentContractNo,
            paymentHasInsurance,
            netAmount,
            personKey,
            activeInsuranceSettlement,
            insutype,
            insutypeName,
            details,
            signedByDetailId,
            detail -> this.shouldCountInsuranceDetail(detail, paymentModel, paymentById));
    }

    private PaymentReconciliationQueryModel resolveInsuranceAuthorityPayment(
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        if (paymentModel == null) {
            return null;
        }
        if (this.isReversePaymentModel(paymentModel) && paymentModel.getRelationId() != null
            && paymentById != null) {
            PaymentReconciliationQueryModel original = paymentById.get(paymentModel.getRelationId());
            if (original != null) {
                return original;
            }
        }
        return paymentModel;
    }

    private boolean isReversePaymentModel(PaymentReconciliationQueryModel paymentModel) {
        if (paymentModel == null) {
            return false;
        }
        return DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            paymentModel.getPaymentEnum(),
            paymentModel.getRelationId(),
            paymentModel.getStatusEnum());
    }

    /**
     * 从展示 payment 行收集合同业务号（供归桶批量解析）。
     *
     * @param displayItems 日结展示行
     * @return 去重后的 contract_no
     */
    private Set<String> collectContractBusNosFromPaymentItems(
        List<InpatientDailySettlementPaymentItemDto> displayItems) {
        Set<String> busNos = new LinkedHashSet<>();
        if (displayItems == null) {
            return busNos;
        }
        // Loop：收集非空 contract_no
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null || StringUtils.isEmpty(item.getContractNo())) {
                continue;
            }
            busNos.add(item.getContractNo().trim());
        }
        return busNos;
    }

    /**
     * fin_type 合计 Map 求代数和（上半区总收入基数）。
     *
     * @param finTypeAmounts fin_type_code → 金额
     * @return 合计；空 Map 为 0
     */
    private BigDecimal sumFinTypeAmounts(Map<String, BigDecimal> finTypeAmounts) {
        BigDecimal total = BigDecimal.ZERO;
        if (finTypeAmounts == null) {
            return total;
        }
        for (BigDecimal amount : finTypeAmounts.values()) {
            if (amount == null) {
                continue;
            }
            total = total.add(amount);
        }
        return total;
    }

    /**
     * 从展示用 payment 读模型中剔除预交金 kind，供 fin_type 上半区汇总。
     *
     * @param paymentModels 展示过滤后的 payment 读模型
     * @return 仅含住院结算（INPATIENT_CLINIC）的读模型
     */
    private List<PaymentReconciliationQueryModel> filterPaymentModelsExcludingDeposit(
        List<PaymentReconciliationQueryModel> paymentModels) {
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        if (paymentModels == null) {
            return result;
        }
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel == null) {
                continue;
            }
            if (this.isDepositPaymentKind(paymentModel.getKindEnum())) {
                continue;
            }
            result.add(paymentModel);
        }
        return result;
    }

    /**
     * 剔除住院上半区不应展示的 fin_type 合计项（挂号费/诊察费为门诊号源口径；体检不计入日结）。
     *
     * @param finTypeAmounts fin_type_code → 合计（原地修改）
     */
    private void removeInpatientExcludedFinTypeAmounts(Map<String, BigDecimal> finTypeAmounts) {
        if (finTypeAmounts == null || finTypeAmounts.isEmpty()) {
            return;
        }
        finTypeAmounts.remove(DailySettlementSharedConstants.DEPRECATED_REGISTRATION_FEE_FIN_TYPE_CODE);
        finTypeAmounts.remove(InpatientDailySettlementConstants.INPATIENT_EXCLUDED_CONSULTATION_FEE_FIN_TYPE_CODE);
        finTypeAmounts.remove(DailySettlementSharedConstants.PHYSICAL_EXAM_FIN_TYPE_CODE);
    }

    /**
     * 是否住院日结上半区应排除的 fin_type_code（门诊号源类、体检）。
     *
     * @param finTypeCode fin_type_code 字典 value
     * @return 是否排除
     */
    private boolean isInpatientExcludedFinTypeCode(String finTypeCode) {
        if (StringUtils.isEmpty(finTypeCode)) {
            return false;
        }
        String normalized = finTypeCode.trim();
        if (DailySettlementSharedConstants.DEPRECATED_REGISTRATION_FEE_FIN_TYPE_CODE.equals(normalized)) {
            return true;
        }
        if (InpatientDailySettlementConstants.INPATIENT_EXCLUDED_CONSULTATION_FEE_FIN_TYPE_CODE.equals(normalized)) {
            return true;
        }
        return DailySettlementSharedConstants.PHYSICAL_EXAM_FIN_TYPE_CODE.equals(normalized);
    }

    /**
     * 是否预交金 payment kind（读模型枚举口径）。
     *
     * @param kindEnum payment kind 枚举
     * @return 是否预交金
     */
    private boolean isDepositPaymentKind(PaymentKind kindEnum) {
        return PaymentKind.HOSPITAL_DEPOSIT.equals(kindEnum);
    }

    /**
     * 是否预交金 payment kind（含历史 PRE_DEPOSIT 落库码）。
     *
     * @param kindEnum payment kind code
     * @return 是否预交金
     */
    private boolean isDepositPaymentKind(String kindEnum) {
        if (StringUtils.isEmpty(kindEnum)) {
            return false;
        }
        String normalized = kindEnum.trim();
        if (PaymentKind.HOSPITAL_DEPOSIT.getCode().equals(normalized)) {
            return true;
        }
        return InpatientDailySettlementConstants.LEGACY_HOSPITAL_DEPOSIT_KIND_CODE.equals(normalized);
    }

    /**
     * payment 主表带符号净额：逆向反结/退费单取负（BR-IP-DCL-011）。
     *
     * @param item 候选 payment 行
     * @return 带符号净额
     */
    private BigDecimal resolveSignedPaymentNetAmount(InpatientDailySettlementPaymentItemDto item) {
        BigDecimal netAmount = item.getNetAmount();
        if (netAmount == null) {
            netAmount = BigDecimal.ZERO;
        }
        if (this.isReversePayment(item)) {
            return netAmount.abs().negate();
        }
        return netAmount;
    }

    /**
     * 从渠道汇总 Map 按展示名取金额（缺失为 0）。
     *
     * @param channelAmounts 渠道 → 金额
     * @param displayName    渠道展示名
     * @return 金额
     */
    private BigDecimal resolveChannelAmount(Map<String, BigDecimal> channelAmounts, String displayName) {
        if (channelAmounts == null || StringUtils.isEmpty(displayName)) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = channelAmounts.get(displayName);
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount;
    }

    /**
     * 金额格式化为日结展示串（两位小数 plain string）。
     *
     * @param amount 金额
     * @return 展示串；null 按 0 处理
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        return MonetaryAmountSupport.scaleDisplay(amount).toPlainString();
    }
}
