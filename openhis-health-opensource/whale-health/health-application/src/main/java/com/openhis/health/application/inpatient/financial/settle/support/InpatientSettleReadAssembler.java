/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleChargeLineDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.SettledPaymentBriefDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentStaticSnapshotQueryModel;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院结算读路径 DTO 装配（IF-IP-STL-03/04/07；AS-IP-FIN-004 IF-IP-STL-07 历史 NET 增量）。
 */
@Component
public class InpatientSettleReadAssembler {

    /**
     * 住院待结算 chargeItem 查询命令（排除挂号锚点、默认 PENDING_SETTLEMENT、业务日窗回落入院日～当日）。
     *
     * @param query         费用读查询
     * @param admissionDate 入院日；chargeDateFrom 空时用作下限
     * @return ChargeItem QS 查询命令
     */
    public ChargeItemQueryCommand toChargeItemQueryCommand(InpatientSettleChargesQuery query, LocalDate admissionDate) {
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        if (query == null) {
            return command;
        }
        command.setEncounterId(query.getEncounterId());
        List<String> statusIn = new ArrayList<>();
        statusIn.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        command.setStatusEnumIn(statusIn);
        command.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        LocalDate from = query.getChargeDateFrom();
        // 未传起始日：从入院日开始查待结算
        if (from == null) {
            from = admissionDate;
        }
        LocalDate to = query.getChargeDateTo();
        // 未传截止日：查到当日
        if (to == null) {
            to = LocalDate.now();
        }
        command.setBusinessDateFrom(from);
        command.setBusinessDateTo(to);
        command.setExcludeSupersededSettledLines(true);
        return command;
    }

    /**
     * chargeItem 实体列表 → 结算明细 DTO（挂接产品读模型与默认勾选态）。
     *
     * @param chargeItems      待结算收费明细
     * @param productByKey     复合键 → 产品读模型
     * @param defaultSelected  是否默认勾选
     * @return 明细 DTO 列表
     */
    public List<InpatientSettleChargeLineDto> toInpatientSettleChargeLineDtoList(
        List<ChargeItem> chargeItems,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        boolean defaultSelected) {
        List<InpatientSettleChargeLineDto> lines = new ArrayList<>();
        if (chargeItems == null) {
            return lines;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            InpatientSettleChargeLineDto dto = new InpatientSettleChargeLineDto();
            dto.setChargeItemId(chargeItem.getId());
            dto.setUnitPrice(chargeItem.getUnitPrice());
            dto.setQuantityValue(chargeItem.getQuantityValue());
            dto.setQuantityUnit(chargeItem.getQuantityUnit());
            dto.setLineAmount(chargeItem.getTotalPrice());
            dto.setStatusEnum(chargeItem.getStatusEnum());
            ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, chargeItem.getStatusEnum());
            if (status != null) {
                dto.setStatusEnumText(status.getDisplay());
            }
            dto.setOccurrenceTime(chargeItem.getOccurrenceTime());
            GenerateSource source = EnumUtil.fromCode(GenerateSource.class, chargeItem.getGenerateSourceEnum());
            if (source != null) {
                dto.setSourceText(source.getDisplay());
            }
            ProductDefinitionDisplayDto product = null;
            if (productByKey != null && chargeItem.getProductTable() != null && chargeItem.getProductId() != null) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    chargeItem.getProductTable(), chargeItem.getProductId());
                product = productByKey.get(key);
            }
            if (product != null) {
                dto.setItemName(product.getProductName());
                dto.setSpecText(product.getTotalVolume());
                dto.setCategoryCode(product.getCategoryCode());
                dto.setCategoryDisplay(product.getCategoryCodeDictText());
            }
            dto.setSelected(defaultSelected);
            lines.add(dto);
        }
        return lines;
    }

    /**
     * 明细行按 categoryCode 聚合为大类汇总（无法解析类目时归入 {@code _UNKNOWN}）。
     *
     * @param lines 明细 DTO 列表
     * @return 大类汇总列表（LinkedHashMap 保序）
     */
    public List<InpatientSettleCategorySummaryDto> toInpatientSettleCategorySummaryDtoList(
        List<InpatientSettleChargeLineDto> lines) {
        Map<String, InpatientSettleCategorySummaryDto> accumulator = new LinkedHashMap<>();
        if (lines == null) {
            return new ArrayList<>();
        }
        for (InpatientSettleChargeLineDto line : lines) {
            if (line == null) {
                continue;
            }
            String categoryCode = line.getCategoryCode();
            // 产品类目缺失时单独成组，避免 null key
            if (StringUtils.isEmpty(categoryCode)) {
                categoryCode = "_UNKNOWN";
            }
            InpatientSettleCategorySummaryDto summary = accumulator.get(categoryCode);
            if (summary == null) {
                summary = new InpatientSettleCategorySummaryDto();
                summary.setCategoryCode(categoryCode);
                summary.setCategoryDisplay(line.getCategoryDisplay());
                summary.setAmount(BigDecimal.ZERO);
                summary.setLineCount(0);
                accumulator.put(categoryCode, summary);
            }
            BigDecimal lineAmount = line.getLineAmount();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            summary.setAmount(summary.getAmount().add(lineAmount));
            summary.setLineCount(summary.getLineCount() + 1);
        }
        return new ArrayList<>(accumulator.values());
    }

    /**
     * IF-IP-STL-07：历史查阅以 payment 为主，解析本批费用合计真源（优先 {@code tendered_amount}）。
     *
     * @param payment 当前展示支付读模型
     * @return 结算刻度本批合计；payment 为空时 0
     */
    public BigDecimal resolvePaymentBatchTotal(PaymentReconciliationQueryModel payment) {
        if (payment == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal tendered = payment.getTenderedAmount();
        if (tendered != null && tendered.compareTo(BigDecimal.ZERO) > 0) {
            return MonetaryAmountSupport.scaleSettle(tendered);
        }
        return MonetaryAmountSupport.scaleSettle(payment.getDisplayAmount());
    }

    /**
     * IF-IP-STL-07：反结后 charge_item 现价可能漂移，将明细行金额按 payment 本批合计对齐（只读展示）。
     *
     * @param lines              payment.charge_item_ids 挂载的明细行
     * @param paymentBatchTotal  payment 本批费用合计
     */
    public void alignChargeLineAmountsToPaymentMaster(
        List<InpatientSettleChargeLineDto> lines,
        BigDecimal paymentBatchTotal) {
        if (lines == null || lines.isEmpty() || paymentBatchTotal == null) {
            return;
        }
        BigDecimal master = MonetaryAmountSupport.scaleSettle(paymentBatchTotal);
        if (master.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<BigDecimal> weights = new ArrayList<>();
        for (InpatientSettleChargeLineDto line : lines) {
            if (line == null) {
                weights.add(BigDecimal.ZERO);
                continue;
            }
            weights.add(MonetaryAmountSupport.nullToZero(line.getLineAmount()));
        }
        BigDecimal weightSum = MonetaryAmountSupport.sumThenSettle(weights);
        if (MonetaryAmountSupport.equalsAtSettleScale(weightSum, master)) {
            return;
        }
        if (weightSum.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal allocatedSettle = BigDecimal.ZERO;
        int lastIndex = lines.size() - 1;
        for (int index = 0; index < lines.size(); index++) {
            InpatientSettleChargeLineDto line = lines.get(index);
            if (line == null) {
                continue;
            }
            BigDecimal aligned;
            if (index == lastIndex) {
                aligned = master.subtract(allocatedSettle);
            } else {
                aligned = MonetaryAmountSupport.proportionAtCalcScale(
                    line.getLineAmount(),
                    master,
                    weightSum);
                aligned = MonetaryAmountSupport.scaleSettle(aligned);
                allocatedSettle = allocatedSettle.add(aligned);
            }
            line.setLineAmount(aligned);
        }
    }

    /**
     * IF-IP-STL-07：优先用 payment {@code fin_payment_rec_static} 快照组装大类汇总。
     *
     * @param staticRows payment 统计快照行
     * @return 大类汇总；staticRows 为空时返回空列表
     */
    public List<InpatientSettleCategorySummaryDto> toInpatientSettleCategorySummaryFromPaymentStatic(
        List<PaymentStaticSnapshotQueryModel> staticRows) {
        List<InpatientSettleCategorySummaryDto> categories = new ArrayList<>();
        if (staticRows == null) {
            return categories;
        }
        for (PaymentStaticSnapshotQueryModel staticRow : staticRows) {
            if (staticRow == null) {
                continue;
            }
            InpatientSettleCategorySummaryDto summary = new InpatientSettleCategorySummaryDto();
            summary.setCategoryCode(staticRow.getTypeCode());
            summary.setCategoryDisplay(staticRow.getTypeName());
            summary.setAmount(MonetaryAmountSupport.scaleSettle(staticRow.getAmount()));
            summary.setLineCount(this.countChargeItemIds(staticRow.getChargeItemIds()));
            categories.add(summary);
        }
        return categories;
    }

    /**
     * 统计逗号分隔 chargeItemIds 中非空片段个数。
     *
     * @param chargeItemIds 逗号分隔串
     * @return 有效 id 个数
     */
    private int countChargeItemIds(String chargeItemIds) {
        if (StringUtils.isEmpty(chargeItemIds)) {
            return 0;
        }
        int count = 0;
        String[] parts = chargeItemIds.split(",");
        for (String part : parts) {
            if (!StringUtils.isEmpty(part)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 住院结算摘要读结果装配（患者/床位/账户余额提示字段）。
     *
     * @param contextRow      住院就诊上下文
     * @param pendingTotal    待结算总额
     * @param selectedTotal   勾选合计
     * @param prepayNet       预交金净余额
     * @param balanceHint     余额提示
     * @param displayBalance  展示可用余额
     * @return 摘要读结果
     */
    public InpatientSettleSummaryResult toInpatientSettleSummaryResult(
        InpatientEncounterContextQueryModel contextRow,
        BigDecimal pendingTotal,
        BigDecimal selectedTotal,
        BigDecimal prepayNet,
        BigDecimal balanceHint,
        BigDecimal displayBalance) {
        InpatientSettleSummaryResult result = new InpatientSettleSummaryResult();
        if (contextRow != null) {
            result.setEncounterId(contextRow.getEncounterId());
            result.setPatientId(contextRow.getPatientId());
            result.setEncounterBusNo(contextRow.getEncounterBusNo());
            result.setPatientName(contextRow.getPatientName());
            result.setOrganizationName(contextRow.getOrganizationName());
            result.setWardName(contextRow.getWardName());
            result.setBedName(contextRow.getBedName());
            result.setStatusEnum(contextRow.getStatusEnum());
            String statusText = contextRow.getStatusEnumText();
            if (StringUtils.isEmpty(statusText)) {
                statusText = InpatientEncounterStatusDisplayUtil.resolveDisplayTextForReadModel(
                    contextRow.getStatusEnum(),
                    contextRow.getTransferOrgId(),
                    contextRow.getDischargeTypeEnum());
            }
            result.setStatusEnumText(statusText);
        }
        result.setPendingSettlementTotal(pendingTotal);
        result.setPrepayNetBalance(prepayNet);
        result.setSelectedTotal(selectedTotal);
        result.setBalanceHint(balanceHint);
        result.setDisplayAccountBalance(displayBalance);
        return result;
    }

    /**
     * 住院待结算费用读结果装配（detail/summary 双模式）。
     *
     * @param encounterId       就诊主键
     * @param mode              presentationMode
     * @param lines             明细行（summary 模式可为 null）
     * @param categories        大类汇总（detail 模式可为 null）
     * @param total             待结算总额
     * @param defaultSelectAll  是否默认全选
     * @return 费用读结果
     */
    public InpatientSettleChargesResult toInpatientSettleChargesResult(
        Long encounterId,
        String mode,
        List<InpatientSettleChargeLineDto> lines,
        List<InpatientSettleCategorySummaryDto> categories,
        BigDecimal total,
        boolean defaultSelectAll) {
        InpatientSettleChargesResult result = new InpatientSettleChargesResult();
        result.setEncounterId(encounterId);
        result.setPresentationMode(mode);
        result.setLines(lines);
        result.setCategories(categories);
        result.setPendingSettlementTotal(total);
        result.setDefaultSelectAll(defaultSelectAll);
        return result;
    }

    /**
     * 住院结算/反结历史 + 渠道展示 → 摘要 DTO（含 signedAmount、reverseEnabled）。
     *
     * @param payments                          同就诊 INPATIENT_CLINIC 流水（SUCCESS/REFUNDED）
     * @param payMethodTexts                    paymentId → 渠道展示
     * @param encounter                         就诊快照（DISCHARGED 门禁）
     * @param paymentReconciliationQueryService 支付读侧（逆向关联判定）
     * @return 历史摘要行；payments 为 null 时返回空列表
     */
    public List<SettledPaymentBriefDto> toSettledPaymentBriefDtoList(
        List<PaymentReconciliationQueryModel> payments,
        Map<Long, String> payMethodTexts,
        EncounterDetailQueryModel encounter,
        PaymentReconciliationQueryService paymentReconciliationQueryService) {
        List<SettledPaymentBriefDto> rows = new ArrayList<>();
        if (payments == null) {
            return rows;
        }
        // Pre：就诊态用于已清床患者禁止收银台反结（BR-IP-RVS-008）。
        String encounterStatusCode = null;
        if (encounter != null) {
            encounterStatusCode = encounter.getStatusEnum();
        }
        // Asm：逐条投影 FORWARD/REVERSE、带符号金额与可反结门禁。
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null) {
                continue;
            }
            SettledPaymentBriefDto dto = new SettledPaymentBriefDto();
            dto.setPaymentId(payment.getId());
            dto.setPaymentNo(payment.getPaymentNo());
            dto.setSettleTime(payment.getBillDate());
            dto.setReceivableTotal(payment.getDisplayAmount());
            if (payment.getId() != null && payMethodTexts != null) {
                dto.setPayMethodText(payMethodTexts.get(payment.getId()));
            }
            boolean reverseRecord = payment.getRelationId() != null;
            if (reverseRecord) {
                // 逆向反结单：历史条类型 REVERSE，signedAmount 取负
                dto.setRecordType(InpatientSettleConstants.SETTLED_PAYMENT_RECORD_TYPE_REVERSE);
            } else {
                dto.setRecordType(InpatientSettleConstants.SETTLED_PAYMENT_RECORD_TYPE_FORWARD);
            }
            String statusCode = null;
            if (payment.getStatusEnum() != null) {
                statusCode = payment.getStatusEnum().getCode();
            }
            dto.setStatusEnum(statusCode);
            dto.setRelationId(payment.getRelationId());
            BigDecimal displayAmount = payment.getDisplayAmount();
            if (displayAmount == null) {
                displayAmount = BigDecimal.ZERO;
            }
            if (reverseRecord) {
                dto.setSignedAmount(displayAmount.abs().negate());
            } else {
                dto.setSignedAmount(displayAmount);
            }
            boolean reverseEnabled = false;
            String reverseDisabledReason = null;
            if (reverseRecord) {
                reverseDisabledReason = InpatientSettleConstants.REVERSE_DISABLED_REASON_REVERSE_RECORD;
            } else if (!PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
                reverseDisabledReason = InpatientSettleConstants.REVERSE_DISABLED_REASON_NOT_SUCCESS;
            } else if (!PaymentKind.INPATIENT_CLINIC.equals(payment.getKindEnum())) {
                reverseDisabledReason = InpatientSettleConstants.REVERSE_DISABLED_REASON_NOT_SUCCESS;
            } else if (EncounterStatus.DISCHARGED.getCode().equals(encounterStatusCode)) {
                // 已清床离院：收银台禁止再发起反结（BR-IP-RVS-008）
                reverseDisabledReason = InpatientSettleConstants.REVERSE_DISABLED_REASON_DISCHARGED;
            } else if (payment.getId() != null
                && paymentReconciliationQueryService != null
                && Boolean.TRUE.equals(
                    paymentReconciliationQueryService.hasRefundByRelationId(payment.getId()).getExists())) {
                // 已有 relation 逆向单：禁止重复反结
                reverseDisabledReason = InpatientSettleConstants.REVERSE_DISABLED_REASON_ALREADY_REVERSED;
            } else {
                reverseEnabled = true;
            }
            dto.setReverseEnabled(reverseEnabled);
            dto.setReverseDisabledReason(reverseDisabledReason);
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 计算结算历史 signedAmount 代数和。
     *
     * @param payments 已装配摘要行
     * @return 净额；无行时为 0
     */
    public BigDecimal sumNetSettleTotal(List<SettledPaymentBriefDto> payments) {
        BigDecimal net = BigDecimal.ZERO;
        if (payments == null) {
            return net;
        }
        for (SettledPaymentBriefDto row : payments) {
            if (row == null || row.getSignedAmount() == null) {
                continue;
            }
            net = net.add(row.getSignedAmount());
        }
        return net;
    }

    /**
     * 已结算单查阅读结果装配（含 netSettleTotal 代数和）。
     *
     * @param encounterId     就诊 ID
     * @param payments        历史摘要行
     * @param activePaymentId 当前展示支付 ID
     * @param settleTime      当前单结算时间
     * @param operatorName    操作员展示名
     * @param receivableTotal 当前单应收合计
     * @param payMethodText   当前单支付方式展示
     * @param lines           费用明细快照
     * @param categories      大类汇总快照
     * @return 查阅读结果
     */
    public SettledInpatientPaymentViewResult toSettledInpatientPaymentViewResult(
        Long encounterId,
        List<SettledPaymentBriefDto> payments,
        Long activePaymentId,
        LocalDateTime settleTime,
        String operatorName,
        BigDecimal receivableTotal,
        String payMethodText,
        List<InpatientSettleChargeLineDto> lines,
        List<InpatientSettleCategorySummaryDto> categories) {
        SettledInpatientPaymentViewResult result = new SettledInpatientPaymentViewResult();
        result.setEncounterId(encounterId);
        result.setPayments(payments);
        result.setActivePaymentId(activePaymentId);
        result.setSettleTime(settleTime);
        result.setOperatorName(operatorName);
        result.setReceivableTotal(receivableTotal);
        result.setPayMethodText(payMethodText);
        result.setLines(lines);
        result.setCategories(categories);
        result.setNetSettleTotal(this.sumNetSettleTotal(payments));
        return result;
    }

    /**
     * 是否 summary 呈现模式。
     *
     * @param mode presentationMode 原文
     * @return true 表示大类汇总模式
     */
    public boolean isSummaryPresentationMode(String mode) {
        return InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(trimToEmpty(mode));
    }
}
