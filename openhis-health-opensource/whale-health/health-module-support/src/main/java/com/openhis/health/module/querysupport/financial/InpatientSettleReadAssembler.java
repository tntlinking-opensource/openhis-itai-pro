/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.module.querysupport.financial.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleChargeLineDto;
import com.openhis.health.module.querysupport.financial.dto.SettledPaymentBriefDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
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
import org.apache.commons.lang3.StringUtils;
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
        if (from == null) {
            from = admissionDate;
        }
        LocalDate to = query.getChargeDateTo();
        if (to == null) {
            to = LocalDate.now();
        }
        command.setBusinessDateFrom(from);
        command.setBusinessDateTo(to);
        return command;
    }

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
            if (StringUtils.isBlank(categoryCode)) {
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
            if (StringUtils.isBlank(statusText)) {
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
            dto.setSettleTime(payment.getBillDate());
            dto.setReceivableTotal(payment.getDisplayAmount());
            if (payment.getId() != null && payMethodTexts != null) {
                dto.setPayMethodText(payMethodTexts.get(payment.getId()));
            }
            boolean reverseRecord = payment.getRelationId() != null;
            if (reverseRecord) {
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
                reverseDisabledReason = InpatientSettleConstants.REVERSE_DISABLED_REASON_DISCHARGED;
            } else if (payment.getId() != null
                && paymentReconciliationQueryService != null
                && Boolean.TRUE.equals(
                    paymentReconciliationQueryService.hasRefundByRelationId(payment.getId()).getExists())) {
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

    public boolean isSummaryPresentationMode(String mode) {
        return InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(trimToEmpty(mode));
    }
}