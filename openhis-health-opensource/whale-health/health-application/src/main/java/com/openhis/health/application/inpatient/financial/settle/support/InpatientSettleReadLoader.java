/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support;

import com.openhis.health.application.shared.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayResult;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.financial.inpatientsettle.queries.model.InpatientSettleContextQueryModel;
import com.openhis.health.domain.financial.inpatientsettle.service.InpatientSettleContextQueryService;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleCategorySummaryDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientSettleChargeLineDto;
import com.openhis.health.application.inpatient.financial.settle.support.dto.SettledPaymentBriefDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentStaticSnapshotQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 住院结算读路径（IF-IP-STL-03/04/07），供 AppService 与 report-adapter 共用。
 */
@Component
@RequiredArgsConstructor
public class InpatientSettleReadLoader {

    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;
    private final EncounterQueryService encounterQueryService;
    private final InpatientSettleContextQueryService inpatientSettleContextQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InpatientSettleReadAssembler inpatientSettleReadAssembler;

    /**
     * 加载住院结算摘要（IF-IP-STL-03）：待结算合计、勾选合计、账户余额展示。
     *
     * @param query 须含 encounterId、patientId；可选 selectedChargeItemIds
     * @return 摘要读结果
     */
    public InpatientSettleSummaryResult loadInpatientSettleSummary(InpatientSettleSummaryQuery query) {
        // Gate：就诊与患者主键必填
        if (query == null || query.getEncounterId() == null || query.getPatientId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "刷新并重新选择患者");
        }
        // Gate：须为在院 IMP 且非 COMPLETED（已结算须切已结算页签）
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
        // Pre：L1 住院结算上下文（就诊展示 + 登记账户 + charge/payment 汇总）
        InpatientSettleContextQueryModel settleContext = inpatientSettleContextQueryService.mustLoadSettleContext(
            query.getEncounterId(),
            query.getPatientId());
        InpatientEncounterContextQueryModel inpatientContext = settleContext.getInpatientContext();
        Account account = settleContext.getRegistrationAccount();
        BigDecimal pendingTotal = settleContext.getPendingSettlementTotal();
        // Pre：待结算总额；有勾选 id 时按勾选子集重算
        BigDecimal selectedTotal = pendingTotal;
        if (query.getSelectedChargeItemIds() != null && !query.getSelectedChargeItemIds().isEmpty()) {
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(query.getSelectedChargeItemIds());
            List<ChargeItem> selectedItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            selectedTotal = sumChargeItemAmount(selectedItems);
        }
        BigDecimal settledTotal = settleContext.getSettledTotal();
        BigDecimal accountBalance = InpatientAccountBalanceDisplayAssembler.resolveAccountBalance(account);
        BigDecimal balanceHint = MonetaryAmountSupport.scaleDisplay(accountBalance.subtract(selectedTotal));
        InpatientAccountBalanceDisplayResult accountBalanceDisplay =
            InpatientAccountBalanceDisplayAssembler.assemble(
                account,
                pendingTotal,
                settledTotal,
                settleContext.isReversedPendingResettle());
        BigDecimal displayBalance = accountBalanceDisplay.getAvailableBalance();
        // Asm：摘要 DTO + 账户余额展示字段
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

    /**
     * 查询住院待结算费用明细或大类汇总（IF-IP-STL-04）。
     *
     * @param query 须含 encounterId、patientId、presentationMode
     * @return 费用读结果
     */
    public InpatientSettleChargesResult queryInpatientSettleCharges(InpatientSettleChargesQuery query) {
        // Gate：入参完整性 + presentationMode 合法值
        if (query == null
            || query.getEncounterId() == null
            || query.getPatientId() == null
            || StringUtils.isEmpty(query.getPresentationMode())) {
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
        // summary 模式：每条 charge 须能解析产品类目，否则拒绝（EX-IP-STL-104）
        if (InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode)) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem == null || chargeItem.getProductTable() == null || chargeItem.getProductId() == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    chargeItem.getProductTable(), chargeItem.getProductId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product == null || StringUtils.isEmpty(product.getCategoryCode())) {
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
        // Asm：summary 模式只返回 categories，明细行置 null
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

    /**
     * 加载已结算单查阅读视图（IF-IP-STL-07）：历史 payment 列表 + 指定单明细快照。
     *
     * @param query 须含 encounterId、patientId；paymentId 可空（默认最近一笔）
     * @return 已结算查阅读结果
     */
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
        // Pre：按 bill_date、id 倒序；未传 paymentId 时默认最近正向结单
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
            // Pre：未指定 paymentId 时默认最近正向结单（AS-IP-FIN-003 IF-IP-STL-07；反结后避免默认落到逆向条）
            activePaymentId = this.resolveDefaultActivePaymentId(historyPayments);
        }
        // Pre：加载 active 单绑定 chargeItem 与产品读模型（B.6.4 ProductDefinitionDisplayTerminologyAssembler）
        PaymentReconciliationQueryModel activePayment =
            paymentReconciliationQueryService.mustGetPayment(activePaymentId);
        List<Long> chargeItemIdList = parseIds(activePayment.getChargeItemIds());
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(chargeItemIdList);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        Map<String, ProductDefinitionDisplayDto> productByKey = buildProductMapByChargeItems(chargeItems);
        String mode = query.getPresentationMode();
        if (StringUtils.isEmpty(mode)) {
            mode = InpatientSettleConstants.PRESENTATION_MODE_DETAIL;
        }
        List<InpatientSettleChargeLineDto> lines = inpatientSettleReadAssembler.toInpatientSettleChargeLineDtoList(
            chargeItems, productByKey, false);
        // Pre：IF-IP-STL-07 历史以 payment 为主；本批合计真源 tendered_amount，明细行金额对齐 payment
        BigDecimal paymentBatchTotal = inpatientSettleReadAssembler.resolvePaymentBatchTotal(activePayment);
        // Pre：医保少补为 0 时 tendered/display 均为 0，回落收费项合计供开票勾稽与页头展示
        if (paymentBatchTotal == null || paymentBatchTotal.compareTo(BigDecimal.ZERO) <= 0) {
            paymentBatchTotal = sumChargeItemAmount(chargeItems);
        }
        inpatientSettleReadAssembler.alignChargeLineAmountsToPaymentMaster(lines, paymentBatchTotal);
        Map<Long, List<PaymentStaticSnapshotQueryModel>> staticRowsByPaymentId =
            paymentReconciliationQueryService.mapPaymentRecStaticsByReconciliationIds(
                Collections.singleton(activePaymentId));
        List<PaymentStaticSnapshotQueryModel> paymentStaticRows = null;
        if (staticRowsByPaymentId != null) {
            paymentStaticRows = staticRowsByPaymentId.get(activePaymentId);
        }
        List<InpatientSettleCategorySummaryDto> categories = null;
        List<InpatientSettleChargeLineDto> responseLines = lines;
        if (InpatientSettleConstants.PRESENTATION_MODE_SUMMARY.equals(mode.trim())) {
            categories = this.resolveSettledCategorySummary(lines, paymentStaticRows, paymentBatchTotal);
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
            paymentBatchTotal,
            payMethodText,
            responseLines,
            categories);
    }

    /**
     * 已结算大类汇总：优先 payment 统计快照，否则回落对齐后的 charge 行聚合。
     *
     * @param alignedLines       已按 payment 对齐金额的明细行
     * @param paymentStaticRows  fin_payment_rec_static 快照
     * @param paymentBatchTotal  payment 本批合计
     * @return 大类汇总
     */
    private List<InpatientSettleCategorySummaryDto> resolveSettledCategorySummary(
        List<InpatientSettleChargeLineDto> alignedLines,
        List<PaymentStaticSnapshotQueryModel> paymentStaticRows,
        BigDecimal paymentBatchTotal) {
        if (paymentStaticRows != null && !paymentStaticRows.isEmpty()) {
            List<BigDecimal> staticAmounts = new ArrayList<>();
            for (PaymentStaticSnapshotQueryModel staticRow : paymentStaticRows) {
                if (staticRow == null) {
                    continue;
                }
                staticAmounts.add(staticRow.getAmount());
            }
            BigDecimal staticTotal = MonetaryAmountSupport.sumThenSettle(staticAmounts);
            if (MonetaryAmountSupport.equalsAtSettleScale(staticTotal, paymentBatchTotal)) {
                return inpatientSettleReadAssembler.toInpatientSettleCategorySummaryFromPaymentStatic(
                    paymentStaticRows);
            }
        }
        return inpatientSettleReadAssembler.toInpatientSettleCategorySummaryDtoList(alignedLines);
    }

    /**
     * 未指定 paymentId 时解析默认展示单：取 bill_date 降序下首条正向结单（relation_id 为空）。
     *
     * @param historyPayments 同就诊 INPATIENT_CLINIC 流水（已按 bill_date、id 倒序）
     * @return 默认 activePaymentId；无正向条时回退列表首条
     */
    private Long resolveDefaultActivePaymentId(List<PaymentReconciliationQueryModel> historyPayments) {
        if (historyPayments == null || historyPayments.isEmpty()) {
            return null;
        }
        for (PaymentReconciliationQueryModel payment : historyPayments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            // 正向结单：relation_id 为空；反结后默认仍展示正向费用快照
            if (payment.getRelationId() == null) {
                return payment.getId();
            }
        }
        PaymentReconciliationQueryModel fallback = historyPayments.get(0);
        if (fallback == null) {
            return null;
        }
        return fallback.getId();
    }

    /**
     * 由收费明细批量加载产品展示 Map（productTable + productId 复合键）。
     *
     * @param chargeItems 收费行列表
     * @return 复合键 → 产品展示 DTO
     */
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
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 汇总收费行 totalPrice（跳过 null 行/金额）。
     *
     * @param chargeItems 收费行列表
     * @return 合计金额
     */
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

    /**
     * 解析逗号分隔 id 串为 Long 列表（跳过空白与非数字片段）。
     *
     * @param raw 逗号分隔串
     * @return id 列表；空串返回空列表
     */
    private static List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isEmpty(raw)) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }
}
