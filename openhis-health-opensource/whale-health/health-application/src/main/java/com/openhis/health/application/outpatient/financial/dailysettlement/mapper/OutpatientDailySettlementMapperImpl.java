/*
 * Copyright © 2026 OpenHIS Contributors
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
import com.openhis.health.domain.financial.cashiersettlement.command.SubmitCashierSettlementCommand;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentStaticSnapshotQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientDailySettlementConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.module.common.assembler.financial.ContractExpenseNatureAssembler;
import com.openhis.health.module.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.module.querysupport.financial.DailySettlementFinTypeAmountSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
public class OutpatientDailySettlementMapperImpl implements OutpatientDailySettlementMapper {

    /** 自付子明细支付渠道 code → 展示名（微信/现金等）。 */
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    /** payment.contract_no → 是否医保费用性质（fin_contract 口径）。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    /** 上半区 fin_type_code 金额 enrich（快照优先，回落 charge_item）。 */
    private final DailySettlementFinTypeAmountSupport dailySettlementFinTypeAmountSupport;

    /** 支付统计快照读侧（mapPaymentRecStaticsByReconciliationIds）。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /**
     * @param paymentChannelDisplayAssembler        支付渠道展示名解析
     * @param contractExpenseNatureAssembler        费用性质（合同）判定
     * @param dailySettlementFinTypeAmountSupport   财务类别汇总 enrich
     * @param paymentReconciliationQueryService     支付读侧（统计快照）
     */
    public OutpatientDailySettlementMapperImpl(
        PaymentChannelDisplayAssembler paymentChannelDisplayAssembler,
        ContractExpenseNatureAssembler contractExpenseNatureAssembler,
        DailySettlementFinTypeAmountSupport dailySettlementFinTypeAmountSupport,
        PaymentReconciliationQueryService paymentReconciliationQueryService) {
        this.paymentChannelDisplayAssembler = paymentChannelDisplayAssembler;
        this.contractExpenseNatureAssembler = contractExpenseNatureAssembler;
        this.dailySettlementFinTypeAmountSupport = dailySettlementFinTypeAmountSupport;
        this.paymentReconciliationQueryService = paymentReconciliationQueryService;
    }

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
        String dateText = defaultDate.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        response.setDefaultDateFrom(dateText);
        response.setDefaultDateTo(dateText);
        response.setExpensePropertyOptions(expensePropertyOptions);
        response.setFinancialDelegateEnabled(financialDelegateEnabled);
        response.setCashierOptions(cashierOptions);
        response.setCurrentCashierId(currentCashierId);
        return response;
    }

    /**
     * 预览请求转领域查询条件（IF-OP-DCL-02 Pre）。
     *
     * @param request   HTTP 请求
     * @param typeEnum  交账类型 code（门诊固定 OUTPATIENT）
     * @param cashierId 已解析经办人 Practitioner 主键
     * @return 领域预览查询
     */
    @Override
    public PreviewCashierSettlementQuery toPreviewCashierSettlementQuery(
        PreviewOutpatientDailySettlementRequest request,
        String typeEnum,
        Long cashierId) {
        PreviewCashierSettlementQuery query = new PreviewCashierSettlementQuery();
        query.setTypeEnum(typeEnum);
        query.setCashierId(cashierId);
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
     * @param previewModel             候选 payment 预览（含 paymentCount）
     * @param paymentModels            同日窗 payment 读模型（含 ybSettlements）
     * @param recDetailsByPaymentId    paymentId → 自付子明细
     * @param expenseProperty          费用性质展示过滤，可空
     * @param insuranceByContractBusNo 合同 bus_no → 是否医保
     * @param finTypeOptions           fin_type_code 字典项（上半区展示顺序）
     * @return 日结查询响应；previewModel 为 null 时返回 null
     */
    @Override
    public PreviewOutpatientDailySettlementResponse toPreviewOutpatientDailySettlementResponse(
        PreviewCashierSettlementQueryModel previewModel,
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        String expenseProperty,
        Map<String, Boolean> insuranceByContractBusNo,
        List<OptionDto> finTypeOptions) {
        if (previewModel == null) {
            return null;
        }
        // Pre：候选行转 DTO，并挂载 fin_contract 医保判定
        List<DailySettlementPaymentItemDto> allItems =
            this.buildPaymentItems(previewModel.getItems(), insuranceByContractBusNo);
        Integer paymentCount = previewModel.getPaymentCount();
        if (paymentCount == null) {
            // 领域未回填条数时，以候选行数兜底（BR-OP-DCL-012：流水条数，含 0 元/抵净）
            paymentCount = allItems.size();
        }
        // Pre：费用性质仅收窄 rows/summary 用的展示子集（BR-OP-DCL-009）
        List<DailySettlementPaymentItemDto> displayItems =
            this.filterItemsByExpenseProperty(allItems, expenseProperty);
        Map<Long, PaymentReconciliationQueryModel> paymentById = this.buildPaymentById(paymentModels);
        List<DailySettlementPaymentItemDto> displayItemsWithPayment =
            this.filterDisplayItemsInPaymentScope(displayItems, paymentById.keySet());
        List<PaymentReconciliationQueryModel> displayPaymentModels =
            this.filterPaymentModelsForDisplayItems(displayItemsWithPayment, paymentById);
        // Pre：统计快照 + fin_type 上半区（挂号费 1011 特列在 Support 内）
        Map<Long, List<PaymentStaticSnapshotQueryModel>> staticRowsByPaymentId =
            this.paymentReconciliationQueryService.mapPaymentRecStaticsByReconciliationIds(
                this.collectPaymentIdsFromModels(displayPaymentModels));
        Map<String, BigDecimal> finTypeAmounts =
            this.dailySettlementFinTypeAmountSupport.aggregateFinTypeAmounts(
                displayPaymentModels,
                staticRowsByPaymentId);
        // Pre：自付渠道汇总（排除医保 fund/acct 枚举，BR-OP-DCL-008）
        Map<String, BigDecimal> channelAmounts =
            this.aggregateChannelAmounts(displayItemsWithPayment, recDetailsByPaymentId);
        List<DailySettlementChannelRowDto> rows =
            this.toFinTypeRows(finTypeOptions, finTypeAmounts);
        // Asm：下半区固定汇总项；总收入优先 fin_type 代数和（BR-OP-DCL-010/011）
        DailySettlementSummaryDto summary = this.buildSummary(
            displayItemsWithPayment,
            channelAmounts,
            recDetailsByPaymentId,
            finTypeAmounts);
        PreviewOutpatientDailySettlementResponse response = new PreviewOutpatientDailySettlementResponse();
        response.setPaymentCount(paymentCount);
        response.setItems(allItems);
        response.setRows(rows);
        response.setSummary(summary);
        return response;
    }

    /**
     * 提交请求转领域命令（IF-OP-DCL-03 Pre）。
     *
     * @param request    HTTP 请求
     * @param typeEnum   交账类型 code
     * @param cashierId  已解析经办人 Practitioner 主键
     * @param operatorId 当前操作员用户主键
     * @return 提交命令
     */
    @Override
    public SubmitCashierSettlementCommand toSubmitCashierSettlementCommand(
        SubmitOutpatientDailySettlementRequest request,
        String typeEnum,
        Long cashierId,
        Long operatorId) {
        SubmitCashierSettlementCommand command = new SubmitCashierSettlementCommand();
        command.setTypeEnum(typeEnum);
        command.setOrgId(request.getOrgId());
        command.setLocationId(request.getLocationId());
        command.setCashierId(cashierId);
        command.setDateFrom(request.getDateFrom());
        command.setDateTo(request.getDateTo());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 提交结果装配响应（IF-OP-DCL-03 Asm）。
     *
     * @param settlementId 交账头主键
     * @param busNo        交账单号
     * @param paymentCount 挂载条数
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
        if (insuranceByContractBusNo != null && StringUtils.isNotBlank(contractBusNo)) {
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
        if (StringUtils.isBlank(expenseProperty)) {
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
                if (StringUtils.isBlank(category)) {
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
        if (StringUtils.isBlank(payEnum)) {
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
     * 是否逆向退费 payment（relationId 指向原单或 payment_enum=UN_PAY）。
     *
     * @param item 候选 payment 行
     * @return 是否逆向退费单
     */
    private boolean isReversePayment(DailySettlementPaymentItemDto item) {
        if (item == null) {
            return false;
        }
        if (item.getRelationId() != null) {
            return true;
        }
        return PaymentType.UN_PAY.getCode().equals(item.getPaymentEnum());
    }

    /**
     * fin_type 字典顺序装配上半区展示行。
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
            if (option == null || StringUtils.isBlank(option.getValue())) {
                continue;
            }
            String typeCode = option.getValue().trim();
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
     * 装配下半区固定汇总项（对齐老日结单；BR-OP-DCL-010/011）。
     *
     * @param displayItems            展示过滤后的候选行
     * @param channelAmounts          自付渠道带符号合计
     * @param recDetailsByPaymentId   paymentId → 自付子明细
     * @param finTypeAmounts          fin_type_code → 带符号合计
     * @return 下半区汇总 DTO
     */
    private DailySettlementSummaryDto buildSummary(
        List<DailySettlementPaymentItemDto> displayItems,
        Map<String, BigDecimal> channelAmounts,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<String, BigDecimal> finTypeAmounts) {
        DailySettlementSummaryDto summary = new DailySettlementSummaryDto();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        int selfPayCount = 0;
        int insuranceCount = 0;
        int provincialInsuranceCount = 0;
        int municipalInsuranceCount = 0;
        ContractSummaryAccumulator provincialAccumulator = new ContractSummaryAccumulator();
        ContractSummaryAccumulator municipalAccumulator = new ContractSummaryAccumulator();
        ContractSummaryAccumulator selfPayAccumulator = new ContractSummaryAccumulator();
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
            refundTotal = refundTotal.add(returned);
            boolean hasInsurance = false;
            if (item.getHasInsurance() != null) {
                hasInsurance = item.getHasInsurance();
            }
            if (hasInsurance) {
                insuranceCount = insuranceCount + 1;
            } else {
                // 非医保判定流水计入自费人次
                selfPayCount = selfPayCount + 1;
            }
            String contractBusNo = item.getContractNo();
            if (contractBusNo != null) {
                contractBusNo = contractBusNo.trim();
            }
            if (DailySettlementSharedConstants.PROVINCIAL_INSURANCE_CONTRACT_BUS_NO.equals(contractBusNo)) {
                provincialInsuranceCount = provincialInsuranceCount + 1;
            } else if (DailySettlementSharedConstants.MUNICIPAL_INSURANCE_CONTRACT_BUS_NO.equals(contractBusNo)
                || DailySettlementSharedConstants.MUNICIPAL_INSURANCE_SUPPLEMENT_CONTRACT_BUS_NO.equals(contractBusNo)) {
                municipalInsuranceCount = municipalInsuranceCount + 1;
            }
            ContractSummaryAccumulator target = selfPayAccumulator;
            if (DailySettlementSharedConstants.PROVINCIAL_INSURANCE_CONTRACT_BUS_NO.equals(contractBusNo)) {
                // 省医保合同：现金/基金/个账归入省医保桶
                target = provincialAccumulator;
            } else if (DailySettlementSharedConstants.MUNICIPAL_INSURANCE_CONTRACT_BUS_NO.equals(contractBusNo)
                || DailySettlementSharedConstants.MUNICIPAL_INSURANCE_SUPPLEMENT_CONTRACT_BUS_NO.equals(contractBusNo)) {
                // 市医保（含补充）合同：归入市医保桶
                target = municipalAccumulator;
            } else if (hasInsurance) {
                // 其他医保合同无专用 bus_no 时，回落省医保桶展示
                target = provincialAccumulator;
            }
            this.accumulateContractPaymentSummary(
                target,
                item,
                recDetailsByPaymentId);
        }
        BigDecimal finTypeTotal = this.sumFinTypeAmounts(finTypeAmounts);
        if (finTypeAmounts != null && !finTypeAmounts.isEmpty()) {
            // 有财务分类汇总时，总收入与上半区代数和一致（含 0/负值，一收一退抵净）
            totalIncome = finTypeTotal;
        }
        String totalIncomeText = this.formatAmount(totalIncome);
        summary.setTotalIncome(totalIncomeText);
        summary.setReceivableAmount(totalIncomeText);
        summary.setActualAmount(totalIncomeText);
        summary.setDiscountAmount(DailySettlementSharedConstants.DISCOUNT_AMOUNT_ZERO);
        summary.setWechatAmount(this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.WECHAT.getDisplay())));
        summary.setAlipayAmount(this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.ALIPAY.getDisplay())));
        summary.setOnlineBankAmount(this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.BANK_CARD.getDisplay())));
        summary.setCashAmount(this.formatAmount(this.resolveChannelAmount(channelAmounts, PaymentChannel.CASH.getDisplay())));
        summary.setSelfPayCashTotal(this.formatAmount(selfPayAccumulator.cashAmount));
        summary.setSelfPayCount(selfPayCount);
        summary.setProvincialInsuranceCount(provincialInsuranceCount);
        summary.setMunicipalInsuranceCount(municipalInsuranceCount);
        summary.setProvincialInsuranceCash(this.formatAmount(provincialAccumulator.cashAmount));
        summary.setProvincialInsuranceFund(this.formatAmount(provincialAccumulator.fundAmount));
        summary.setProvincialInsuranceAccount(this.formatAmount(provincialAccumulator.accountAmount));
        summary.setProvincialInsuranceFundTotal(this.formatAmount(provincialAccumulator.fundTotalAmount));
        summary.setProvincialInsuranceTotal(this.formatAmount(
            provincialAccumulator.fundAmount.add(provincialAccumulator.accountAmount)));
        summary.setMunicipalInsuranceCash(this.formatAmount(municipalAccumulator.cashAmount));
        summary.setMunicipalInsuranceFund(this.formatAmount(municipalAccumulator.fundAmount));
        summary.setMunicipalInsuranceAccount(this.formatAmount(municipalAccumulator.accountAmount));
        summary.setMunicipalInsuranceFundTotal(this.formatAmount(municipalAccumulator.fundTotalAmount));
        summary.setMunicipalInsuranceTotal(this.formatAmount(
            municipalAccumulator.fundAmount.add(municipalAccumulator.accountAmount)));
        summary.setTotalCashIncome(this.formatAmount(
            selfPayAccumulator.cashAmount
                .add(provincialAccumulator.cashAmount)
                .add(municipalAccumulator.cashAmount)));
        summary.setPhysicalExamCount(this.resolvePhysicalExamCount(finTypeAmounts));
        summary.setInsuranceCount(insuranceCount);
        summary.setRefundTotal(this.formatAmount(refundTotal));
        return summary;
    }

    /**
     * 按合同桶累加现金/医保基金/个账；逆向 UN_PAY 子明细取负（BR-OP-DCL-011）。
     *
     * @param accumulator             合同维度累加器
     * @param item                    所属 payment 候选行
     * @param recDetailsByPaymentId   paymentId → 自付子明细
     */
    private void accumulateContractPaymentSummary(
        ContractSummaryAccumulator accumulator,
        DailySettlementPaymentItemDto item,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId) {
        if (accumulator == null || item == null || item.getPaymentId() == null || recDetailsByPaymentId == null) {
            return;
        }
        List<PaymentRecDetailQueryModel> details = recDetailsByPaymentId.get(item.getPaymentId());
        if (details == null) {
            return;
        }
        for (PaymentRecDetailQueryModel detail : details) {
            if (detail == null) {
                continue;
            }
            BigDecimal lineAmount = this.resolveSignedDetailNetAmount(detail, item);
            PaymentChannel channel = PaymentChannel.fromCode(detail.getPayEnum());
            if (channel == null) {
                continue;
            }
            if (PaymentChannel.CASH.equals(channel)) {
                accumulator.cashAmount = accumulator.cashAmount.add(lineAmount);
                continue;
            }
            if (PaymentChannel.INSURANCE_FUND.equals(channel)) {
                accumulator.fundAmount = accumulator.fundAmount.add(lineAmount);
                accumulator.fundTotalAmount = accumulator.fundTotalAmount.add(lineAmount);
                continue;
            }
            if (PaymentChannel.INSURANCE_ACCT.equals(channel)) {
                accumulator.accountAmount = accumulator.accountAmount.add(lineAmount);
            }
        }
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

    /** 按合同维度累加现金、统筹、个账等下半区分项。 */
    private static final class ContractSummaryAccumulator {

        /** 现金渠道合计。 */
        private BigDecimal cashAmount = BigDecimal.ZERO;

        /** 医保统筹基金合计。 */
        private BigDecimal fundAmount = BigDecimal.ZERO;

        /** 医保个人账户合计。 */
        private BigDecimal accountAmount = BigDecimal.ZERO;

        /** 医保基金总额（统筹口径）。 */
        private BigDecimal fundTotalAmount = BigDecimal.ZERO;
    }

    /**
     * 从渠道汇总 map 取指定展示名的金额。
     *
     * @param channelAmounts 渠道展示名 → 金额
     * @param displayName    渠道展示名
     * @return 金额；缺失时返回 0
     */
    private BigDecimal resolveChannelAmount(Map<String, BigDecimal> channelAmounts, String displayName) {
        if (channelAmounts == null || StringUtils.isBlank(displayName)) {
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
        return amount.setScale(OutpatientDailySettlementConstants.AMOUNT_DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
