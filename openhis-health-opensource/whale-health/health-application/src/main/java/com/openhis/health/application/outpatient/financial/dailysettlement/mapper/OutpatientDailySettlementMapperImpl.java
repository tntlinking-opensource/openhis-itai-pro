/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.mapper;

import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementChannelRowDto;
import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementPaymentItemDto;
import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementSummaryDto;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.PreviewOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.request.SubmitOutpatientDailySettlementRequest;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.LoadOutpatientDailySettlementPageInitResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.PreviewOutpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.response.SubmitOutpatientDailySettlementResponse;
import com.openhis.health.application.outpatient.financial.dailysettlement.support.DailySettlementPaymentDetailLoader;
import com.openhis.health.application.shared.financial.ContractExpenseNatureAssembler;
import com.openhis.health.application.shared.financial.DailySettlementExpenseNatureDetailAccumulator;
import com.openhis.health.application.shared.financial.DailySettlementExpenseNatureSummarySupport;
import com.openhis.health.application.shared.financial.DailySettlementExpenseNatureSummarySupport.NatureBucket;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.application.shared.financial.dto.DailySettlementExpenseNatureSummaryDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentStaticSnapshotQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentYbSettlementQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientDailySettlementConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.financial.DailySettlementRefundPaymentRules;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.infrastructure.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
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
 * {@link OutpatientDailySettlementMapper} 实现（AS-OP-FIN-004-SDD IF-OP-DCL-01～03）。
 *
 * <p>职责：Request/Command/Response 字段映射；候选 payment 行装配；费用性质展示过滤；
 * fin_type 上半区与 summary 下半区汇总（含逆向退费 UN_PAY 带符号净额，**BR-OP-DCL-011**）。
 * 禁止承载 Gate/权限判断与领域写编排。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientDailySettlementMapperImpl implements OutpatientDailySettlementMapper {

    /** 自付子明细支付渠道 code → 展示名（微信/现金等）。 */
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    /** payment.contract_no → 是否医保费用性质（fin_contract 口径）。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    /** 上半区 fin_type_code 金额 enrich（快照优先，回落 charge_item）。 */
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 支付统计快照读侧（mapPaymentRecStaticsByReconciliationIds）。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** items 项目行与支付方式 enrichment。 */
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
     * 装配门诊日结页初始化响应（IF-OP-DCL-01）。
     *
     * @param defaultDate              默认结账日
     * @param financialDelegateEnabled 是否允许财务代交账
     * @param expensePropertyOptions   费用性质下拉
     * @param cashierOptions           经办人下拉
     * @param currentCashierId         当前经办人 Practitioner 主键
     * @return 初始化响应
     */
    @Override
    public LoadOutpatientDailySettlementPageInitResponse toLoadOutpatientDailySettlementPageInitResponse(
        LocalDate defaultDate,
        boolean financialDelegateEnabled,
        List<OptionDto> expensePropertyOptions,
        List<OptionDto> cashierOptions,
        Long currentCashierId) {
        LoadOutpatientDailySettlementPageInitResponse response = new LoadOutpatientDailySettlementPageInitResponse();
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
     * 预览请求转领域查询条件（IF-OP-DCL-02 Pre）。
     *
     * @param request     HTTP 请求
     * @param typeEnum    交账类型 code
     * @param locationIds 科室下 location 白名单
     * @return 领域预览查询
     */
    @Override
    public PreviewCashierSettlementQuery toPreviewCashierSettlementQuery(
        PreviewOutpatientDailySettlementRequest request,
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
     * 预览结果与 enrichment 数据装配日结查询响应（IF-OP-DCL-02 Asm）。
     *
     * <p>{@code expenseProperty} 仅过滤 rows/summary 展示，{@code paymentCount} 与 {@code items} 保持全量候选
     *（**BR-OP-DCL-009**）。{@code paymentCount} 为待归档 payment 流水条数，含 0 元与收支抵净流水（**BR-OP-DCL-012**）。
     * 汇总净额对逆向退费单取负，一收一退代数和为 0（**BR-OP-DCL-011**）。</p>
     *
     * @param previewModel             未交账候选 payment 预览（paymentCount / items）
     * @param summaryPreviewItems      日结展示口径 payment 行（含已交账，供 rows/summary）
     * @param paymentModels            展示口径 payment 读模型（含 ybSettlements）
     * @param recDetailsByPaymentId    paymentId → 自付子明细
     * @param expenseProperty          费用性质展示过滤，可空
     * @param insuranceByContractBusNo 合同 bus_no → 是否医保
     * @param finTypeOptions           fin_type_code 字典项（上半区展示顺序）
     * @return 日结查询响应；previewModel 为 null 时返回 null
     */
    @Override
    public PreviewOutpatientDailySettlementResponse toPreviewOutpatientDailySettlementResponse(
        PreviewCashierSettlementQueryModel previewModel,
        List<UnsettledPaymentPreviewItemQueryModel> summaryPreviewItems,
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<Long, List<ChargeItem>> chargeItemsByPaymentId,
        String expenseProperty,
        Map<String, Boolean> insuranceByContractBusNo,
        List<OptionDto> finTypeOptions) {
        if (previewModel == null) {
            return null;
        }
        Map<Long, PaymentReconciliationQueryModel> paymentById = this.buildPaymentById(paymentModels);
        // Pre：未交账候选行转 DTO（items / paymentCount 口径）
        List<DailySettlementPaymentItemDto> unsettledItems =
            this.buildPaymentItems(previewModel.getItems(), insuranceByContractBusNo);
        boolean chargeEmpty = chargeItemsByPaymentId == null || chargeItemsByPaymentId.isEmpty();
        if (!chargeEmpty) {
            this.dailySettlementPaymentDetailLoader.enrichOutpatientPaymentItems(
                unsettledItems,
                chargeItemsByPaymentId,
                recDetailsByPaymentId);
        }
        Integer paymentCount = previewModel.getPaymentCount();
        if (paymentCount == null) {
            paymentCount = unsettledItems.size();
        }
        // Pre：展示汇总用全量口径（含已交账）
        boolean sameSource = summaryPreviewItems == previewModel.getItems();
        List<DailySettlementPaymentItemDto> summarySourceItems = sameSource
            ? unsettledItems
            : this.buildPaymentItems(summaryPreviewItems, insuranceByContractBusNo);
        DailySettlementDisplayBundle mainDisplay = this.buildDisplayBundle(
            summarySourceItems,
            expenseProperty,
            paymentById,
            recDetailsByPaymentId,
            finTypeOptions);
        // 挂载现算 / 同源：main 与 handover 同口径，只算一遍
        DailySettlementDisplayBundle handoverDisplay = sameSource
            ? mainDisplay
            : this.buildDisplayBundle(
                unsettledItems,
                expenseProperty,
                paymentById,
                recDetailsByPaymentId,
                finTypeOptions);
        PreviewOutpatientDailySettlementResponse response = new PreviewOutpatientDailySettlementResponse();
        response.setPaymentCount(paymentCount);
        response.setItems(unsettledItems);
        response.setRows(mainDisplay.rows());
        response.setSummary(mainDisplay.summary());
        response.setHandoverRows(handoverDisplay.rows());
        response.setHandoverSummary(handoverDisplay.summary());
        return response;
    }

    /**
     * 按展示 payment 子集汇总上半区 rows 与下半区 summary（BR-OP-DCL-010/011/017）。
     *
     * @param sourceItems             汇总源 payment 行
     * @param expenseProperty         费用性质展示过滤
     * @param paymentById             payment 读模型索引
     * @param recDetailsByPaymentId   自付子明细
     * @param finTypeOptions          fin_type 字典
     * @return rows + summary 组合
     */
    private DailySettlementDisplayBundle buildDisplayBundle(
        List<DailySettlementPaymentItemDto> sourceItems,
        String expenseProperty,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        List<OptionDto> finTypeOptions) {
        List<DailySettlementPaymentItemDto> displayItems =
            this.filterItemsByExpenseProperty(sourceItems, expenseProperty);
        List<DailySettlementPaymentItemDto> displayItemsWithPayment =
            this.filterDisplayItemsInPaymentScope(displayItems, paymentById.keySet());
        // 工伤合同不进上半区 fin_type / 总收入 / 渠道 / 费用性质（与打印日结单口径对齐）
        List<DailySettlementPaymentItemDto> summaryItems =
            this.excludeWorkInjuryPaymentItems(displayItemsWithPayment);
        List<PaymentReconciliationQueryModel> finTypePaymentModels =
            this.resolveFinTypeAggregationPaymentModels(summaryItems, paymentById);
        Map<Long, List<PaymentStaticSnapshotQueryModel>> staticRowsByPaymentId =
            this.paymentReconciliationQueryService.mapPaymentRecStaticsByReconciliationIds(
                this.collectPaymentIdsFromModels(finTypePaymentModels));
        Map<String, BigDecimal> finTypeAmounts =
            this.dailySettlementFinTypeAmountLoader.aggregateFinTypeAmounts(
                finTypePaymentModels,
                staticRowsByPaymentId);
        this.mergeDeprecatedRegistrationIntoConsultation(finTypeAmounts);
        Map<String, BigDecimal> channelAmounts =
            this.aggregateChannelAmounts(summaryItems, recDetailsByPaymentId);
        List<DailySettlementChannelRowDto> rows =
            this.toFinTypeRows(finTypeOptions, finTypeAmounts);
        DailySettlementSummaryDto summary = this.buildSummary(
            summaryItems,
            paymentById,
            channelAmounts,
            recDetailsByPaymentId,
            finTypeAmounts);
        return new DailySettlementDisplayBundle(rows, summary);
    }

    /**
     * 日结展示汇总结果（上半区 + 下半区）。
     *
     * @param rows    财务类别行
     * @param summary 固定汇总项
     */
    private record DailySettlementDisplayBundle(
        List<DailySettlementChannelRowDto> rows,
        DailySettlementSummaryDto summary) {
    }

    /**
     * 提交请求转领域命令（IF-OP-DCL-03 Pre）。
     *
     * @param request            HTTP 请求
     * @param typeEnum           交账类型 code
     * @param submitterId        提交人 Practitioner 主键
     * @param operatorId         当前操作员用户主键
     * @param selectedPaymentIds 勾选挂载 payment 主键
     * @param locationIds        科室下 location 白名单
     * @param practitionerIds    窗内 enterer 快照
     * @param paymentCount       计入总收入笔数（不含工伤）
     * @param totalIncome        勾选集合日结「总收入」（与预览 handoverSummary 同管线）
     * @return 提交命令
     */
    @Override
    public SubmitCashierSettlementCommand toSubmitCashierSettlementCommand(
        SubmitOutpatientDailySettlementRequest request,
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
     * 按勾选 payment 现算日结「总收入」（与 IF-OP-DCL-02 {@code handoverSummary.totalIncome} 同管线）。
     *
     * <p>无费用性质筛、无自付渠道明细：仅复用 {@link #buildDisplayBundle} 产出 summary.totalIncome，
     * 保证交账头表与日结预览「总收入」口径一致（**BR-OP-DCL-011**）。</p>
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
        List<DailySettlementPaymentItemDto> selectedItems =
            this.buildPaymentItems(selectedPreviewItems, Map.of());
        List<PaymentReconciliationQueryModel> paymentModels =
            this.paymentReconciliationQueryService.findPaymentQueryModelsByIds(selectedPaymentIds);
        Map<Long, PaymentReconciliationQueryModel> paymentById = this.buildPaymentById(paymentModels);
        // Pre：expenseProperty=null、finTypeOptions 空列表——总收入不依赖字典行展示，仍走 fin_type 代数和/净额回落
        DailySettlementDisplayBundle displayBundle = this.buildDisplayBundle(
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
        List<DailySettlementPaymentItemDto> selectedItems =
            this.buildPaymentItems(selectedPreviewItems, Map.of());
        return this.excludeWorkInjuryPaymentItems(selectedItems).size();
    }

    /**
     * 提交结果装配响应（IF-OP-DCL-03 Asm）。
     *
     * @param settlementId 交账头主键
     * @param busNo        交账单号
     * @param paymentCount 计入总收入笔数（不含工伤）
     * @return 提交响应
     */
    @Override
    public SubmitOutpatientDailySettlementResponse toSubmitOutpatientDailySettlementResponse(
        Long settlementId,
        String busNo,
        Integer paymentCount) {
        SubmitOutpatientDailySettlementResponse response = new SubmitOutpatientDailySettlementResponse();
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
     * @param totalIncomeText {@link DailySettlementSummaryDto#getTotalIncome()} 两位小数字符串
     * @return 展示刻度金额；空串为 0
     */
    private BigDecimal parseSummaryTotalIncome(String totalIncomeText) {
        if (StringUtils.isEmpty(totalIncomeText)) {
            return MonetaryAmountSupport.scaleDisplay(BigDecimal.ZERO);
        }
        return MonetaryAmountSupport.scaleDisplay(new BigDecimal(totalIncomeText.trim()));
    }

    /**
     * 未交账预览行转日结候选 DTO，并解析医保费用性质标记。
     *
     * @param previewItems               领域预览行
     * @param insuranceByContractBusNo   合同 bus_no → 是否医保
     * @return 候选 payment 行；previewItems 为 null 时返回空列表
     */
    private List<DailySettlementPaymentItemDto> buildPaymentItems(
        List<UnsettledPaymentPreviewItemQueryModel> previewItems,
        Map<String, Boolean> insuranceByContractBusNo) {
        List<DailySettlementPaymentItemDto> items = new ArrayList<>();
        if (previewItems == null) {
            return items;
        }
        for (UnsettledPaymentPreviewItemQueryModel previewItem : previewItems) {
            if (previewItem == null) {
                continue;
            }
            DailySettlementPaymentItemDto item = new DailySettlementPaymentItemDto();
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
            boolean hasInsurance = this.resolveInsuranceFlag(previewItem.getContractNo(), insuranceByContractBusNo);
            item.setHasInsurance(hasInsurance);
            items.add(item);
        }
        return items;
    }

    /**
     * 解析 payment 是否医保费用性质：优先批量预载 map，否则回落 fin_contract 判定。
     *
     * @param contractBusNo              payment.contract_no
     * @param insuranceByContractBusNo   合同 bus_no → 是否医保
     * @return 是否医保口径
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
     * 按费用性质展示过滤 payment 行（BR-OP-DCL-009；仅影响 rows/summary，不改 paymentCount）。
     *
     * <p>下拉值为 {@code fin_contract.bus_no} 时：自费类合同展示全部非医保判定流水（含 {@code contract_no} 为空）；
     * 医保类合同仍按 payment {@code contract_no} 与所选 bus_no 精确匹配。</p>
     *
     * @param items            候选 payment 行
     * @param expenseProperty  费用性质筛选项（{@code selfPay}/{@code insurance} 或 fin_contract.bus_no）
     * @return 展示用子集
     */
    private List<DailySettlementPaymentItemDto> filterItemsByExpenseProperty(
        List<DailySettlementPaymentItemDto> items,
        String expenseProperty) {
        if (StringUtils.isEmpty(expenseProperty)) {
            // 未选费用性质：展示全量候选，与交账提交口径一致
            return items;
        }
        String normalizedExpenseProperty = expenseProperty.trim();
        List<DailySettlementPaymentItemDto> filtered = new ArrayList<>();
        boolean selectedContractIsInsurance = false;
        boolean selectedContractResolved = false;
        if (!OutpatientDailySettlementConstants.EXPENSE_SELF_PAY.equals(normalizedExpenseProperty)
            && !OutpatientDailySettlementConstants.EXPENSE_INSURANCE.equals(normalizedExpenseProperty)) {
            // 下拉选中 fin_contract.bus_no 时，先解析该合同是否医保口径
            selectedContractIsInsurance =
                this.contractExpenseNatureAssembler.isInsuranceContractBusNo(normalizedExpenseProperty);
            selectedContractResolved = true;
        }
        for (DailySettlementPaymentItemDto item : items) {
            if (item == null) {
                continue;
            }
            boolean hasInsurance = false;
            if (item.getHasInsurance() != null) {
                hasInsurance = item.getHasInsurance();
            }
            if (OutpatientDailySettlementConstants.EXPENSE_SELF_PAY.equals(normalizedExpenseProperty)) {
                // 自费筛选项：排除 fin_contract 判定为医保的 payment
                if (!hasInsurance) {
                    filtered.add(item);
                }
                continue;
            }
            if (OutpatientDailySettlementConstants.EXPENSE_INSURANCE.equals(normalizedExpenseProperty)) {
                // 医保筛选项：仅保留 fin_contract 判定为医保的 payment
                if (hasInsurance) {
                    filtered.add(item);
                }
                continue;
            }
            if (selectedContractResolved && !selectedContractIsInsurance) {
                // 自费类 fin_contract：展示全部非医保流水，避免 contract_no 为空时被误排除
                if (!hasInsurance) {
                    filtered.add(item);
                }
                continue;
            }
            String contractBusNo = item.getContractNo();
            if (contractBusNo != null && normalizedExpenseProperty.equals(contractBusNo.trim())) {
                // 医保类 fin_contract：payment.contract_no 须与所选 bus_no 一致
                filtered.add(item);
            }
        }
        return filtered;
    }

    /**
     * 按自付子明细汇总业务渠道金额（BR-OP-DCL-008）；逆向 UN_PAY 明细取负。
     *
     * @param displayItems            展示过滤后的候选行
     * @param recDetailsByPaymentId   paymentId → 自付子明细
     * @return 渠道展示名 → 带符号净额
     */
    private Map<String, BigDecimal> aggregateChannelAmounts(
        List<DailySettlementPaymentItemDto> displayItems,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId) {
        Map<String, BigDecimal> channelAmounts = new LinkedHashMap<>();
        if (displayItems == null || recDetailsByPaymentId == null) {
            return channelAmounts;
        }
        for (DailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            List<PaymentRecDetailQueryModel> details = recDetailsByPaymentId.get(item.getPaymentId());
            if (details == null) {
                continue;
            }
            for (PaymentRecDetailQueryModel detail : details) {
                if (detail == null || this.isInsurancePayChannel(detail.getPayEnum())) {
                    // 医保 fund/acct 渠道不在自付渠道区汇总，避免与 fin_contract 口径重复
                    continue;
                }
                String category = this.paymentChannelDisplayAssembler.resolvePayChannelDisplay(detail.getPayEnum(), null);
                if (StringUtils.isEmpty(category)) {
                    // 展示名缺失时回落原始 pay_enum，避免金额丢失
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
     * 判定 pay_enum 是否为医保基金/个账渠道（须从自付渠道汇总中排除）。
     *
     * @param payEnum 子明细支付渠道 code
     * @return 是否医保枚举渠道
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
     * 子明细净额：实收减已退（同条明细内的部分退还）。
     *
     * @param detail 自付子明细
     * @return 非负或部分退还后的净额
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
     * 子明细带符号净额：逆向退费单（UN_PAY）在渠道/现金汇总中取负（BR-OP-DCL-011）。
     *
     * @param detail 自付子明细
     * @param item   所属 payment 候选行
     * @return 带符号行净额
     */
    private BigDecimal resolveSignedDetailNetAmount(
        PaymentRecDetailQueryModel detail,
        DailySettlementPaymentItemDto item) {
        BigDecimal lineAmount = this.resolveDetailNetAmount(detail);
        if (this.isReversePayment(item)) {
            // 逆向退费：现金/渠道流出，与正向收费代数和抵净
            return lineAmount.abs().negate();
        }
        return lineAmount;
    }

    /**
     * 是否逆向退费 payment（relationId / UN_PAY / REFUNDED；与费用明细 **BR-OP-FDT-012** 一致）。
     *
     * @param item 候选 payment 行
     * @return 是否逆向退费单
     */
    private boolean isReversePayment(DailySettlementPaymentItemDto item) {
        if (item == null) {
            return false;
        }
        return DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            item.getPaymentEnum(),
            item.getRelationId(),
            item.getStatusEnum());
    }

    /**
     * fin_type 字典顺序装配上半区展示行（排除已废止挂号费 1011，号源金额已归并诊察费 1012）。
     *
     * @param finTypeOptions  fin_type_code 字典项
     * @param finTypeAmounts  typeCode → 带符号合计
     * @return 上半区行；finTypeOptions 为 null 时返回空列表
     */
    private List<DailySettlementChannelRowDto> toFinTypeRows(
        List<OptionDto> finTypeOptions,
        Map<String, BigDecimal> finTypeAmounts) {
        List<DailySettlementChannelRowDto> rows = new ArrayList<>();
        if (finTypeOptions == null) {
            return rows;
        }
        for (OptionDto option : finTypeOptions) {
            if (option == null || StringUtils.isEmpty(option.getValue())) {
                continue;
            }
            String typeCode = option.getValue().trim();
            if (DailySettlementSharedConstants.DEPRECATED_REGISTRATION_FEE_FIN_TYPE_CODE.equals(typeCode)) {
                continue;
            }
            BigDecimal amount = BigDecimal.ZERO;
            if (finTypeAmounts != null) {
                BigDecimal resolved = finTypeAmounts.get(typeCode);
                if (resolved != null) {
                    amount = resolved;
                }
            }
            DailySettlementChannelRowDto row = new DailySettlementChannelRowDto();
            row.setCategoryCode(typeCode);
            row.setCategory(option.getLabel());
            row.setAmount(this.formatAmount(amount));
            row.setUnit(OutpatientDailySettlementConstants.AMOUNT_UNIT_YUAN);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 历史挂号费 fin_type（1011）归并至诊察费（1012），并从 map 移除废止码（BR-OP-DCL-010）。
     *
     * @param finTypeAmounts fin_type_code → 合计（原地修改）
     */
    private void mergeDeprecatedRegistrationIntoConsultation(Map<String, BigDecimal> finTypeAmounts) {
        if (finTypeAmounts == null || finTypeAmounts.isEmpty()) {
            return;
        }
        BigDecimal legacyRegistrationAmount =
            finTypeAmounts.remove(DailySettlementSharedConstants.DEPRECATED_REGISTRATION_FEE_FIN_TYPE_CODE);
        if (legacyRegistrationAmount == null) {
            return;
        }
        BigDecimal consultationAmount =
            finTypeAmounts.get(DailySettlementSharedConstants.CONSULTATION_FEE_FIN_TYPE_CODE);
        if (consultationAmount == null) {
            consultationAmount = BigDecimal.ZERO;
        }
        finTypeAmounts.put(
            DailySettlementSharedConstants.CONSULTATION_FEE_FIN_TYPE_CODE,
            consultationAmount.add(legacyRegistrationAmount));
    }

    /**
     * payment 读模型列表转 id 索引，供展示子集与 enrichment 对齐。
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
     * 将费用性质过滤后的行收窄至同日窗 payment 读模型范围内，避免 enrichment 脏 id。
     *
     * @param displayItems 费用性质过滤后的候选行
     * @param paymentIds   同日窗 payment 主键集合
     * @return 可 enrich 的展示行
     */
    private List<DailySettlementPaymentItemDto> filterDisplayItemsInPaymentScope(
        List<DailySettlementPaymentItemDto> displayItems,
        Set<Long> paymentIds) {
        List<DailySettlementPaymentItemDto> filtered = new ArrayList<>();
        if (displayItems == null || paymentIds == null || paymentIds.isEmpty()) {
            return filtered;
        }
        for (DailySettlementPaymentItemDto item : displayItems) {
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
     * 按展示行顺序提取去重后的 payment 读模型，供 fin_type enrich 与快照批量加载。
     *
     * @param displayItems 展示用候选行
     * @param paymentById  paymentId → 读模型
     * @return 与展示行对应的读模型列表（顺序与首次出现一致）
     */
    private List<PaymentReconciliationQueryModel> filterPaymentModelsForDisplayItems(
        List<DailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        if (displayItems == null || paymentById == null) {
            return result;
        }
        Set<Long> added = new LinkedHashSet<>();
        for (DailySettlementPaymentItemDto item : displayItems) {
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
    private List<DailySettlementPaymentItemDto> excludeWorkInjuryPaymentItems(
        List<DailySettlementPaymentItemDto> items) {
        if (items == null || items.isEmpty()) {
            return items == null ? List.of() : items;
        }
        Set<String> contractBusNos = this.collectContractBusNosFromPaymentItems(items);
        Map<String, Boolean> workInjuryByBusNo =
            this.contractExpenseNatureAssembler.mapWorkInjuryFlagByContractBusNoIn(contractBusNos);
        if (workInjuryByBusNo == null || workInjuryByBusNo.isEmpty()) {
            return items;
        }
        List<DailySettlementPaymentItemDto> filtered = new ArrayList<>(items.size());
        for (DailySettlementPaymentItemDto item : items) {
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
        List<DailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        LinkedHashMap<Long, PaymentReconciliationQueryModel> merged = new LinkedHashMap<>();
        if (displayItems != null) {
            for (DailySettlementPaymentItemDto item : displayItems) {
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
     * 从 payment 读模型列表收集主键，供统计快照批量查询。
     *
     * @param paymentModels 展示范围内的读模型
     * @return payment 主键列表
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
     * 装配下半区汇总（公共头 + 费用性质动态块；BR-OP-DCL-010/011）。
     *
     * @param displayItems            展示过滤后的候选行
     * @param paymentById             paymentId → 支付读模型（人次去重取 patientId）
     * @param channelAmounts          自付渠道带符号合计
     * @param recDetailsByPaymentId   paymentId → 自付子明细
     * @param finTypeAmounts          fin_type_code → 带符号合计
     * @return 下半区汇总 DTO
     */
    private DailySettlementSummaryDto buildSummary(
        List<DailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<String, BigDecimal> channelAmounts,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<String, BigDecimal> finTypeAmounts) {
        DailySettlementSummaryDto summary = new DailySettlementSummaryDto();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
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
        for (DailySettlementPaymentItemDto item : displayItems) {
            if (item == null) {
                continue;
            }
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
                // 逆向 UN_PAY/REFUNDED 流水：退费合计取绝对值（returnedAmount 对逆向单通常为 0）
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
            // Pre：零售−上传分差带符号累加（逆向 UN_PAY 取负，与 BR-OP-DCL-011 同精神）；工伤不计入
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
            // 有财务分类汇总时，总收入与上半区代数和一致（含 0/负值，一收一退抵净）
            totalIncome = finTypeTotal;
        }
        String totalIncomeText = this.formatAmount(totalIncome);
        String wechatText = this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.WECHAT.getDisplay()));
        String alipayText = this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.ALIPAY.getDisplay()));
        String onlineBankText = this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.BANK_CARD.getDisplay()));
        String cashText = this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.CASH.getDisplay()));
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
        Integer physicalExamCount = this.resolvePhysicalExamCount(finTypeAmounts);
        summary.setPhysicalExamCount(physicalExamCount);
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
            null,
            expenseNatureSummaries,
            physicalExamCount));
        return summary;
    }

    /**
     * 解析日结人次去重键：优先 patientId，其次 encounterId，最后回落 paymentId。
     *
     * @param item          候选 payment 行
     * @param paymentById   paymentId → 支付读模型
     * @return 去重键；无法解析时返回 null
     */
    private Long resolveSettlementPersonKey(
        DailySettlementPaymentItemDto item,
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
     * <p>逆向单以原单桥表为准；逆向自身空桥表不再兼容为「可计」（避免成对退费虚增人次/个账）。
     * 全 REVOKED 时：窗内无对冲的正向 / 跨窗逆向仍按<strong>交账时刻口径</strong>视为有效
     * （交账后跨窗退费打成事后 REVOKED，不改已挂载交账单现算）。</p>
     *
     * @param paymentModel 支付读模型
     * @param paymentById  同日窗 / 挂载集合 payment 索引（解析 relation_id）
     * @return 有 CONFIRMED，或交账时刻口径仍应计时 true
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
            // 空桥表：仅正向历史兼容为可计；逆向空桥表不计
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
     * <b>原单不在本窗的 UN_PAY</b>：须计入负向统筹/个账（**BR-OP-DCL-011**）。
     * <b>正向本窗、退费他窗</b>（交账后跨窗退费致回执事后 REVOKED）：本窗仍按<strong>交账时刻口径</strong>计入，
     * 禁止跟事后 REVOKED 把已挂载交账单统筹/个账抹掉，否则与头表/fin_type 总收入勾稽不上。</p>
     *
     * @param detail       支付子明细
     * @param paymentModel 支付读模型
     * @param paymentById  同日窗 / 挂载集合 payment 索引
     * @return false=窗内成对撤销跳过；跨窗逆向、或交账时刻正向仍为 true
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
        // Loop：本行交易号若仅对应已撤销 setl，则按交账时刻/跨窗规则判定
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
     *
     * @param reverse          当前是否逆向退费单
     * @param originalOutside  逆向原单是否不在本窗索引
     * @param paymentModel     当前 payment
     * @param paymentById      本窗 / 挂载集合索引
     * @return true=计入（跨窗负向，或交账时刻正向）
     */
    private boolean shouldCountWhenYbRevoked(
        boolean reverse,
        boolean originalOutside,
        PaymentReconciliationQueryModel paymentModel,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        if (reverse) {
            // 跨窗退费：本窗计负向统筹/个账
            return originalOutside;
        }
        // 正向：窗内无对冲退费 → 交账后跨窗退费导致事后 REVOKED，仍按交账时刻计
        return !this.hasInWindowRefundForPayment(paymentModel, paymentById);
    }

    /**
     * 本窗 / 挂载集合内是否存在指向该正向 payment 的逆向退费单。
     *
     * @param paymentModel 正向 payment
     * @param paymentById  索引
     * @return true=窗内成对，正逆向医保明细均应跳过
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
        List<DailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        Set<Long> recordIds = new LinkedHashSet<>();
        if (displayItems == null || paymentById == null) {
            return this.settlementRecordLookupExtension.mapInsutypeBySettlementRecordIds(recordIds);
        }
        for (DailySettlementPaymentItemDto item : displayItems) {
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
        List<DailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        Set<Long> paymentIds = new LinkedHashSet<>();
        if (displayItems == null) {
            return this.settlementRecordLookupExtension.mapInsutypeByPaymentIds(paymentIds);
        }
        for (DailySettlementPaymentItemDto item : displayItems) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            paymentIds.add(item.getPaymentId());
            // 逆向单以原单权威 payment 再试一次
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
        List<DailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        Set<Long> recordIds = new LinkedHashSet<>();
        if (displayItems == null) {
            return this.settlementRecordLookupExtension.mapRetailUploadDiffBySettlementRecordIds(recordIds);
        }
        for (DailySettlementPaymentItemDto item : displayItems) {
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
        List<DailySettlementPaymentItemDto> displayItems,
        Map<Long, PaymentReconciliationQueryModel> paymentById) {
        Set<Long> paymentIds = new LinkedHashSet<>();
        if (displayItems == null) {
            return this.settlementRecordLookupExtension.mapRetailUploadDiffByPaymentIds(paymentIds);
        }
        for (DailySettlementPaymentItemDto item : displayItems) {
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
     * <p>一笔 payment 可挂多条 CONFIRMED 回执（如拆单结算）；**BR-OP-DCL-028** 要求带符号代数和，
     * 禁止只取首条导致上传差额与渠道勾稽差一截（本单典型漏计 0.27）。</p>
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
        // 交账时刻口径：全 REVOKED 且仍应计时，允许从已撤销回执读险种（否则仅能靠 paymentId 回落）
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
        // 桥表缺 settlement_record_id / 未命中：按支付单回落 yb_settlement_record
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
        DailySettlementPaymentItemDto item,
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

    /**
     * 解析医保桥表权威 payment：逆向取原单；原单不在索引时回落自身。
     */
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

    /**
     * 读模型是否逆向退费单。
     */
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
     * 从展示 payment 行收集合同业务号（供费用性质名称批量解析）。
     *
     * @param displayItems 日结展示行
     * @return 去重后的 contract_no
     */
    private Set<String> collectContractBusNosFromPaymentItems(List<DailySettlementPaymentItemDto> displayItems) {
        Set<String> busNos = new LinkedHashSet<>();
        if (displayItems == null) {
            return busNos;
        }
        // Loop：收集非空 contract_no
        for (DailySettlementPaymentItemDto item : displayItems) {
            if (item == null || StringUtils.isEmpty(item.getContractNo())) {
                continue;
            }
            busNos.add(item.getContractNo().trim());
        }
        return busNos;
    }

    /**
     * fin_type 各字典码金额代数和。
     *
     * @param finTypeAmounts typeCode → 金额
     * @return 合计；map 为 null 时返回 0
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
     * 体检人次：取 fin_type 体检码对应金额的正整数部分（无金额或≤0 时为 0）。
     *
     * @param finTypeAmounts fin_type 汇总
     * @return 体检人次
     */
    private Integer resolvePhysicalExamCount(Map<String, BigDecimal> finTypeAmounts) {
        if (finTypeAmounts == null) {
            return 0;
        }
        BigDecimal amount = finTypeAmounts.get(DailySettlementSharedConstants.PHYSICAL_EXAM_FIN_TYPE_CODE);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return amount.intValue();
    }

    /**
     * 从渠道汇总 map 取指定展示名的金额。
     *
     * @param channelAmounts 渠道展示名 → 金额
     * @param displayName    渠道展示名
     * @return 金额；缺失时返回 0
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
     * 金额格式化为两位小数字符串（日结单展示口径）。
     *
     * @param amount 金额
     * @return 两位小数字符串
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        return MonetaryAmountSupport.scaleDisplay(amount).toPlainString();
    }
}
