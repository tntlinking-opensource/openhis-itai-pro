/*
 * Copyright © 2026 OpenHIS Contributors
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
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
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
import com.openhis.health.domain.share.constants.InpatientDailySettlementConstants;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentKind;
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
import java.util.List;
import java.util.Map;

/**
 * {@link InpatientDailySettlementMapper} 实现（AS-IP-FIN-005-SDD）。
 */
@Component
public class InpatientDailySettlementMapperImpl implements InpatientDailySettlementMapper {

    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    private final DailySettlementFinTypeAmountSupport dailySettlementFinTypeAmountSupport;

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /**
     * @param paymentChannelDisplayAssembler 支付渠道展示名解析
     * @param contractExpenseNatureAssembler 费用性质（合同）判定
     * @param dailySettlementFinTypeAmountSupport 财务类别汇总 enrich
     * @param paymentReconciliationQueryService 支付读侧（统计快照）
     */
    public InpatientDailySettlementMapperImpl(
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
     * 预览请求转领域查询条件。
     *
     * @param request   HTTP 请求
     * @param typeEnum  交账类型 code
     * @param cashierId 已解析经办人
     * @return 领域预览查询
     */
    @Override
    public PreviewCashierSettlementQuery toPreviewCashierSettlementQuery(
        PreviewInpatientDailySettlementRequest request,
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
     * 预览结果与 enrichment 装配查询响应。
     *
     * @param previewModel             候选 payment 预览
     * @param paymentModels            payment 读模型
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
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<Long, InpatientEncounterContextQueryModel> encounterByEncounterId,
        String expenseProperty,
        Map<String, Boolean> insuranceByContractBusNo,
        List<OptionDto> finTypeOptions) {
        if (previewModel == null) {
            return null;
        }
        Map<Long, PaymentReconciliationQueryModel> paymentById = this.buildPaymentById(paymentModels);
        List<InpatientDailySettlementPaymentItemDto> allItems =
            this.buildPaymentItems(
                previewModel.getItems(),
                insuranceByContractBusNo,
                paymentById,
                encounterByEncounterId);
        Integer paymentCount = previewModel.getPaymentCount();
        if (paymentCount == null) {
            paymentCount = allItems.size();
        }
        List<InpatientDailySettlementPaymentItemDto> displayItems =
            this.filterItemsByExpenseProperty(allItems, expenseProperty);
        List<InpatientDailySettlementPaymentItemDto> displayItemsWithPayment =
            this.filterDisplayItemsInPaymentScope(displayItems, paymentById.keySet());
        List<PaymentReconciliationQueryModel> displayPaymentModels =
            this.filterPaymentModelsForDisplayItems(displayItemsWithPayment, paymentById);
        Map<Long, List<PaymentStaticSnapshotQueryModel>> staticRowsByPaymentId =
            this.paymentReconciliationQueryService.mapPaymentRecStaticsByReconciliationIds(
                this.collectPaymentIdsFromModels(displayPaymentModels));
        Map<String, BigDecimal> finTypeAmounts =
            this.dailySettlementFinTypeAmountSupport.aggregateFinTypeAmounts(
                displayPaymentModels,
                staticRowsByPaymentId);
        Map<String, BigDecimal> channelAmounts =
            this.aggregateChannelAmounts(displayItemsWithPayment, recDetailsByPaymentId);
        List<InpatientDailySettlementChannelRowDto> rows =
            this.toFinTypeRows(finTypeOptions, finTypeAmounts);
        InpatientDailySettlementSummaryDto summary = this.buildSummary(
            displayItemsWithPayment,
            channelAmounts,
            recDetailsByPaymentId,
            finTypeAmounts);
        PreviewInpatientDailySettlementResponse response = new PreviewInpatientDailySettlementResponse();
        response.setPaymentCount(paymentCount);
        response.setItems(allItems);
        response.setRows(rows);
        response.setSummary(summary);
        return response;
    }

    /**
     * 提交请求转领域命令。
     *
     * @param request    HTTP 请求
     * @param typeEnum   交账类型
     * @param cashierId  经办人
     * @param operatorId 操作员
     * @return 提交命令
     */
    @Override
    public SubmitCashierSettlementCommand toSubmitCashierSettlementCommand(
        SubmitInpatientDailySettlementRequest request,
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
     * 提交结果装配响应。
     *
     * @param settlementId 交账头主键
     * @param busNo        交账单号
     * @param paymentCount 挂载条数
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

    private boolean resolveInsuranceFlag(String contractBusNo, Map<String, Boolean> insuranceByContractBusNo) {
        if (insuranceByContractBusNo != null && StringUtils.isNotBlank(contractBusNo)) {
            Boolean flag = insuranceByContractBusNo.get(contractBusNo.trim());
            if (flag != null) {
                return flag;
            }
        }
        return this.contractExpenseNatureAssembler.isInsuranceContractBusNo(contractBusNo);
    }

    private String resolveWardName(
        UnsettledPaymentPreviewItemQueryModel previewItem,
        Map<Long, PaymentReconciliationQueryModel> paymentById,
        Map<Long, InpatientEncounterContextQueryModel> encounterByEncounterId) {
        if (PaymentKind.HOSPITAL_DEPOSIT.getCode().equals(previewItem.getKindEnum())) {
            return InpatientDailySettlementConstants.DEPOSIT_CATEGORY_LABEL;
        }
        PaymentReconciliationQueryModel paymentModel = paymentById.get(previewItem.getPaymentId());
        if (paymentModel == null || paymentModel.getEncounterId() == null) {
            return InpatientDailySettlementConstants.WARD_NAME_FALLBACK;
        }
        InpatientEncounterContextQueryModel encounter = encounterByEncounterId.get(paymentModel.getEncounterId());
        if (encounter == null || StringUtils.isBlank(encounter.getWardName())) {
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
        if (StringUtils.isBlank(expenseProperty)) {
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
            List<PaymentRecDetailQueryModel> details = recDetailsByPaymentId.get(item.getPaymentId());
            if (details == null) {
                continue;
            }
            for (PaymentRecDetailQueryModel detail : details) {
                if (detail == null || this.isInsurancePayChannel(detail.getPayEnum())) {
                    continue;
                }
                String category = this.paymentChannelDisplayAssembler.resolvePayChannelDisplay(detail.getPayEnum(), null);
                if (StringUtils.isBlank(category)) {
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

    private List<InpatientDailySettlementChannelRowDto> toFinTypeRows(
        List<OptionDto> finTypeOptions,
        Map<String, BigDecimal> finTypeAmounts) {
        List<InpatientDailySettlementChannelRowDto> rows = new ArrayList<>();
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
            InpatientDailySettlementChannelRowDto row = new InpatientDailySettlementChannelRowDto();
            row.setCategoryCode(typeCode);
            row.setCategory(option.getLabel());
            row.setAmount(this.formatAmount(amount));
            row.setUnit(InpatientDailySettlementConstants.AMOUNT_UNIT_YUAN);
            rows.add(row);
        }
        return rows;
    }

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
     * 是否逆向反结/退费 payment（relationId 指向原单或 payment_enum=UN_PAY）。
     *
     * @param item 候选 payment 行
     * @return 是否逆向流水
     */
    private boolean isReversePayment(InpatientDailySettlementPaymentItemDto item) {
        if (item == null) {
            return false;
        }
        if (item.getRelationId() != null) {
            return true;
        }
        return PaymentType.UN_PAY.getCode().equals(item.getPaymentEnum());
    }

    /**
     * 装配下半区固定汇总项；总收入优先 fin_type 代数和（含 0/负值，一结一反抵净）。
     *
     * @param displayItems          展示过滤后的候选行
     * @param channelAmounts        自付渠道带符号合计
     * @param recDetailsByPaymentId paymentId → 自付子明细
     * @param finTypeAmounts        fin_type_code → 带符号合计
     * @return 下半区汇总 DTO
     */
    private InpatientDailySettlementSummaryDto buildSummary(
        List<InpatientDailySettlementPaymentItemDto> displayItems,
        Map<String, BigDecimal> channelAmounts,
        Map<Long, List<PaymentRecDetailQueryModel>> recDetailsByPaymentId,
        Map<String, BigDecimal> finTypeAmounts) {
        InpatientDailySettlementSummaryDto summary = new InpatientDailySettlementSummaryDto();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        BigDecimal depositAmount = BigDecimal.ZERO;
        int selfPayCount = 0;
        int insuranceCount = 0;
        int provincialInsuranceCount = 0;
        int municipalInsuranceCount = 0;
        ContractSummaryAccumulator provincialAccumulator = new ContractSummaryAccumulator();
        ContractSummaryAccumulator municipalAccumulator = new ContractSummaryAccumulator();
        ContractSummaryAccumulator selfPayAccumulator = new ContractSummaryAccumulator();
        for (InpatientDailySettlementPaymentItemDto item : displayItems) {
            if (item == null) {
                continue;
            }
            BigDecimal netAmount = item.getNetAmount();
            if (netAmount == null) {
                netAmount = BigDecimal.ZERO;
            }
            totalIncome = totalIncome.add(netAmount);
            if (PaymentKind.HOSPITAL_DEPOSIT.getCode().equals(item.getKindEnum())) {
                depositAmount = depositAmount.add(netAmount);
            }
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
                target = provincialAccumulator;
            } else if (DailySettlementSharedConstants.MUNICIPAL_INSURANCE_CONTRACT_BUS_NO.equals(contractBusNo)
                || DailySettlementSharedConstants.MUNICIPAL_INSURANCE_SUPPLEMENT_CONTRACT_BUS_NO.equals(contractBusNo)) {
                target = municipalAccumulator;
            } else if (hasInsurance) {
                target = provincialAccumulator;
            }
            this.accumulateContractPaymentSummary(
                target,
                item,
                recDetailsByPaymentId);
        }
        BigDecimal finTypeTotal = this.sumFinTypeAmounts(finTypeAmounts);
        if (finTypeAmounts != null && !finTypeAmounts.isEmpty()) {
            // 有财务分类汇总时，总收入与上半区代数和一致（含 0/负值，一结一反抵净）
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
        summary.setDepositAmount(this.formatAmount(depositAmount));
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

    private void accumulateContractPaymentSummary(
        ContractSummaryAccumulator accumulator,
        InpatientDailySettlementPaymentItemDto item,
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

    private static final class ContractSummaryAccumulator {
        private BigDecimal cashAmount = BigDecimal.ZERO;
        private BigDecimal fundAmount = BigDecimal.ZERO;
        private BigDecimal accountAmount = BigDecimal.ZERO;
        private BigDecimal fundTotalAmount = BigDecimal.ZERO;
    }

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

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        return amount.setScale(InpatientDailySettlementConstants.AMOUNT_DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
