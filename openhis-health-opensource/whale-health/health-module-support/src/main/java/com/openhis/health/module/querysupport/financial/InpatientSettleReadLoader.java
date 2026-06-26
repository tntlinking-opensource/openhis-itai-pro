/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import com.openhis.health.module.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientAccountBalanceDisplayResult;
import com.openhis.health.module.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler;
import com.openhis.health.module.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.module.querysupport.financial.dto.InpatientSettleChargeLineDto;
import com.openhis.health.module.querysupport.financial.dto.SettledPaymentBriefDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院结算读路径（IF-IP-STL-03/04/07），供 AppService 与 report-adapter 共用。
 */
@Component
@RequiredArgsConstructor
public class InpatientSettleReadLoader {

    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final AccountQueryService accountQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final InpatientSettleReadAssembler inpatientSettleReadAssembler;

    public InpatientSettleSummaryResult loadInpatientSettleSummary(InpatientSettleSummaryQuery query) {
        if (query == null || query.getEncounterId() == null || query.getPatientId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "刷新并重新选择患者");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            query.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "刷新并重新选择患者");
        if (EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "该患者已结算，请切换至已结算页签查阅");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            query.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_102,
            "患者标识与就诊不一致，请刷新并重新选择");
        InpatientEncounterContextQueryModel inpatientContext =
            encounterContextFacade.mustGetInpatientContextByEncounterId(query.getEncounterId());
        Account account = accountQueryService.getRegistrationAccount(
            query.getPatientId(),
            query.getEncounterId());
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        BigDecimal pendingTotal =
            chargeItemQueryService.sumPendingSettlementTotalPriceByEncounterId(query.getEncounterId());
        BigDecimal selectedTotal = pendingTotal;
        if (query.getSelectedChargeItemIds() != null && !query.getSelectedChargeItemIds().isEmpty()) {
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(query.getSelectedChargeItemIds());
            List<ChargeItem> selectedItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            selectedTotal = sumChargeItemAmount(selectedItems);
        }
        BigDecimal settledTotal =
            chargeItemQueryService.sumSettledTotalPriceByEncounterId(query.getEncounterId());
        BigDecimal accountBalance = InpatientDisplayAccountBalanceAssembler.resolveAccountBalance(account);
        BigDecimal balanceHint = accountBalance.subtract(selectedTotal)
            .setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        Set<Long> reversedIds = paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(
            Collections.singleton(query.getEncounterId()));
        boolean reversedPendingResettle = reversedIds.contains(query.getEncounterId());
        InpatientAccountBalanceDisplayResult accountBalanceDisplay =
            InpatientAccountBalanceDisplayAssembler.assemble(
                account,
                pendingTotal,
                settledTotal,
                reversedPendingResettle);
        BigDecimal displayBalance = accountBalanceDisplay.getAvailableBalance();
        InpatientSettleSummaryResult summaryResult = inpatientSettleReadAssembler.toInpatientSettleSummaryResult(
            inpatientContext,
            pendingTotal,
            selectedTotal,
            accountBalance,
            balanceHint,
            displayBalance);
        summaryResult.setAccountBalanceDisplayMode(accountBalanceDisplay.getAccountBalanceDisplayMode());
        summaryResult.setAvailableBalance(accountBalanceDisplay.getAvailableBalance());
        summaryResult.setAccountBalanceSummaryText(accountBalanceDisplay.getAccountBalanceSummaryText());
        summaryResult.setAccountBalanceSubText(accountBalanceDisplay.getAccountBalanceSubText());
        summaryResult.setLowBalanceWarning(accountBalanceDisplay.isLowBalanceWarning());
        return summaryResult;
    }

    public InpatientSettleChargesResult queryInpatientSettleCharges(InpatientSettleChargesQuery query) {
        if (query == null
            || query.getEncounterId() == null
            || query.getPatientId() == null
            || StringUtils.isBlank(query.getPresentationMode())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "重新选择患者");
        }
        String mode = query.getPresentationMode().trim();
        if (!InpatientSettleConstants.PRESENTATION_MODE_DETAIL.equals(mode)
            && !InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode)) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "费用呈现模式不合法");
        }
        EncounterDetailQueryModel encounterForGate = encounterQueryService.mustGetEncounterOfClass(
            query.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "重新选择患者");
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            query.getPatientId(),
            encounterForGate.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_102,
            "患者标识与就诊不一致");
        LocalDate admissionDate = null;
        if (encounterForGate.getStartTime() != null) {
            admissionDate = encounterForGate.getStartTime().toLocalDate();
        }
        ChargeItemQueryCommand queryCommand =
            inpatientSettleReadAssembler.toChargeItemQueryCommand(query, admissionDate);
        List<ChargeItem> chargeItems =
            chargeItemQueryService.listChargeItemsByEncounterId(queryCommand);
        Map<String, ProductDefinitionDisplayDto> productByKey = buildProductMapByChargeItems(chargeItems);
        if (InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode)) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem == null || chargeItem.getProductTable() == null || chargeItem.getProductId() == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    chargeItem.getProductTable(), chargeItem.getProductId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product == null || StringUtils.isBlank(product.getCategoryCode())) {
                    throw new BusinessException(
                        InpatientSettleConstants.EX_IP_STL_104,
                        "联系管理员维护产品类目");
                }
            }
        }
        List<InpatientSettleChargeLineDto> lines = inpatientSettleReadAssembler.toInpatientSettleChargeLineDtoList(
            chargeItems, productByKey, true);
        BigDecimal pendingTotal = sumChargeItemAmount(chargeItems);
        List<InpatientSettleCategorySummaryDto> categories = null;
        List<InpatientSettleChargeLineDto> responseLines = lines;
        if (InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode)) {
            categories = inpatientSettleReadAssembler.toInpatientSettleCategorySummaryDtoList(lines);
            responseLines = null;
        }
        return inpatientSettleReadAssembler.toInpatientSettleChargesResult(
            query.getEncounterId(),
            mode,
            responseLines,
            categories,
            pendingTotal,
            true);
    }

    public SettledInpatientPaymentViewResult loadSettledInpatientPaymentView(SettledInpatientPaymentViewQuery query) {
        if (query == null || query.getEncounterId() == null || query.getPatientId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "重新选择");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            query.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "重新选择");
        // Pre：反结后就诊回 PENDING 仍须只读展示一正一负历史；仅作废/预约态拒绝。
        this.assertEncounterEligibleForSettlementHistoryRead(encounter);
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            query.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_102,
            "患者标识与就诊不一致");
        List<PaymentReconciliationQueryModel> allPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(query.getEncounterId());
        // Pre：纳入 INPATIENT_CLINIC 且 SUCCESS/REFUNDED，展示一正一负历史（AS-IP-FIN-004 §3.2）。
        List<PaymentReconciliationQueryModel> historyPayments = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : allPayments) {
            if (payment == null) {
                continue;
            }
            if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC) {
                continue;
            }
            if (payment.getStatusEnum() != PaymentStatus.SUCCESS
                && payment.getStatusEnum() != PaymentStatus.REFUNDED) {
                continue;
            }
            historyPayments.add(payment);
        }
        if (historyPayments.isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_110,
                "无历史结算单");
        }
        historyPayments.sort(
            Comparator.comparing(
                PaymentReconciliationQueryModel::getBillDate,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    PaymentReconciliationQueryModel::getId,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        LinkedHashSet<Long> paymentIds = new LinkedHashSet<>();
        for (PaymentReconciliationQueryModel payment : historyPayments) {
            if (payment.getId() != null) {
                paymentIds.add(payment.getId());
            }
        }
        Map<Long, String> payMethodTextByPaymentId =
            paymentReconciliationQueryService.mapFirstPayEnumByPaymentIds(paymentIds);
        Map<Long, String> payMethodDisplayByPaymentId = new HashMap<>();
        for (Map.Entry<Long, String> entry : payMethodTextByPaymentId.entrySet()) {
            String display = paymentChannelDisplayAssembler.resolvePayChannelDisplay(entry.getValue(), null);
            payMethodDisplayByPaymentId.put(entry.getKey(), display);
        }
        List<SettledPaymentBriefDto> paymentBriefs =
            inpatientSettleReadAssembler.toSettledPaymentBriefDtoList(
                historyPayments, payMethodDisplayByPaymentId, encounter, paymentReconciliationQueryService);
        Long activePaymentId = query.getPaymentId();
        if (activePaymentId == null) {
            activePaymentId = historyPayments.get(0).getId();
        }
        PaymentReconciliationQueryModel activePayment =
            paymentReconciliationQueryService.mustGetPayment(activePaymentId);
        List<Long> chargeItemIdList = parseIds(activePayment.getChargeItemIds());
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(chargeItemIdList);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        Map<String, ProductDefinitionDisplayDto> productByKey = buildProductMapByChargeItems(chargeItems);
        String mode = query.getPresentationMode();
        if (StringUtils.isBlank(mode)) {
            mode = InpatientSettleConstants.PRESENTATION_MODE_DETAIL;
        }
        List<InpatientSettleChargeLineDto> lines = inpatientSettleReadAssembler.toInpatientSettleChargeLineDtoList(
            chargeItems, productByKey, false);
        List<InpatientSettleCategorySummaryDto> categories = null;
        List<InpatientSettleChargeLineDto> responseLines = lines;
        if (InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode.trim())) {
            categories = inpatientSettleReadAssembler.toInpatientSettleCategorySummaryDtoList(lines);
            responseLines = null;
        }
        Map<Long, String> operatorNameById = practitionerQueryService.mapDisplayNameByEntererIds(
            Collections.singleton(activePayment.getEntererId()));
        String operatorName = null;
        if (activePayment.getEntererId() != null && operatorNameById != null) {
            operatorName = operatorNameById.get(activePayment.getEntererId());
        }
        String payMethodText = payMethodDisplayByPaymentId.get(activePaymentId);
        return inpatientSettleReadAssembler.toSettledInpatientPaymentViewResult(
            query.getEncounterId(),
            paymentBriefs,
            activePaymentId,
            activePayment.getBillDate(),
            operatorName,
            activePayment.getDisplayAmount(),
            payMethodText,
            responseLines,
            categories);
    }

    private Map<String, ProductDefinitionDisplayDto> buildProductMapByChargeItems(List<ChargeItem> chargeItems) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        if (chargeItems != null) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem == null
                    || chargeItem.getProductTable() == null
                    || chargeItem.getProductId() == null) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(chargeItem.getProductTable());
                lookup.setProductDefinitionId(chargeItem.getProductId());
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        return productDefinitionBatchLoader.loadAsMap(lookups);
    }

    private static BigDecimal sumChargeItemAmount(List<ChargeItem> chargeItems) {
        BigDecimal sum = BigDecimal.ZERO;
        if (chargeItems == null) {
            return sum;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getTotalPrice() == null) {
                continue;
            }
            sum = sum.add(chargeItem.getTotalPrice());
        }
        return sum;
    }

    /**
     * 结算历史只读门禁：允许在院 IMP（含反结后 {@code PENDING}、已结算 {@code COMPLETED}）。
     *
     * @param encounter 住院就诊快照
     * @throws BusinessException {@code CANCELLED}/{@code PLANNED} 时拒绝
     */
    private void assertEncounterEligibleForSettlementHistoryRead(EncounterDetailQueryModel encounter) {
        if (encounter == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "重新选择");
        }
        String statusEnum = encounter.getStatusEnum();
        // Pre：作废/未入院预约态无有效结算上下文，禁止加载历史。
        if (EncounterStatus.CANCELLED.getCode().equals(statusEnum)) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "重新选择");
        }
        if (EncounterStatus.PLANNED.getCode().equals(statusEnum)) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "重新选择");
        }
    }

    private static List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }
}
