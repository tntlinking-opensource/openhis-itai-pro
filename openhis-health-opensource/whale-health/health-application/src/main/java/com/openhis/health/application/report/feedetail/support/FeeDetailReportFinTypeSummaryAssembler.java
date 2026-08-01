/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.support;

import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryFooterDto;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryRowDto;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.FeeDetailFinTypeSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 费用明细报表 fin_type 净汇总装配（AS-RPT-008/009；definition → fin_type enrich + 合并）。
 *
 * <p>与 {@link com.openhis.health.application.report.practitionerworkload.support.PractitionerWorkloadFinTypeEnricher}
 * 同口径：委托 {@link DailySettlementFinTypeAmountLoader#mapFinTypeCodeByChargeItemId}。
 * 金额列：进价合计（历史定价）/ 售价合计（{@code total_price}）/ 实收合计（与售价同口径）。</p>
 */
@Component
@RequiredArgsConstructor
public class FeeDetailReportFinTypeSummaryAssembler {

    /** 收费明细批量读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 定价行 fin_type 解析（与日结口径一致）。 */
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 历史进价解析（BR-PRICE-SRC-001）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /**
     * 将域层 definition 汇总 enrich 为 fin_type 行并合并同码金额。
     *
     * @param query              已预解析 scope 的查询
     * @param domainRows         域层按 definition 汇总行
     * @param finTypeLabelByCode fin_type_code → 展示名
     * @param incompleteExCode   定价锚点无法解析时的业务异常码
     * @param incompleteMessage  异常提示
     * @return 按 fin_type 合并后的汇总行（行金额 6 位）
     */
    public List<FeeDetailReportSummaryRowDto> assembleMergedFinTypeRows(
        OutpatientFeeDetailPageQuery query,
        List<FeeDetailFinTypeSummaryQueryModel> domainRows,
        Map<String, String> finTypeLabelByCode,
        String incompleteExCode,
        String incompleteMessage) {
        List<FeeDetailReportSummaryRowDto> result = new ArrayList<>();
        // 无 definition 汇总行 → 空表（页脚由调用方收 0）
        if (domainRows == null || domainRows.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        // 收集待 enrich 的定价锚点，供批量解析 fin_type
        for (FeeDetailFinTypeSummaryQueryModel domainRow : domainRows) {
            if (domainRow != null && domainRow.getDefinitionId() != null) {
                definitionIds.add(domainRow.getDefinitionId());
            }
        }
        FinTypeResolveBundle resolveBundle =
            this.resolveFinTypeAndPurchaseByDefinition(
                query, definitionIds, incompleteExCode, incompleteMessage);
        Map<String, FeeDetailReportSummaryRowDto> mergedByFinType = new LinkedHashMap<>();
        // 按 fin_type 分桶：多 definition 同码合并金额与行数
        for (FeeDetailFinTypeSummaryQueryModel domainRow : domainRows) {
            if (domainRow == null || domainRow.getDefinitionId() == null) {
                continue;
            }
            String finTypeCode = resolveBundle.finTypeByDefinitionId.get(domainRow.getDefinitionId());
            // 汇总行存在 definition 但无法解析 fin_type → 整单失败，避免静默丢行
            if (StringUtils.isEmpty(finTypeCode)) {
                throw new BusinessException(incompleteExCode, incompleteMessage);
            }
            String key = finTypeCode.trim();
            FeeDetailReportSummaryRowDto row = mergedByFinType.get(key);
            if (row == null) {
                // 首次命中该 fin_type：建行并回落展示名
                row = new FeeDetailReportSummaryRowDto();
                row.setFinTypeCode(key);
                String display = null;
                if (finTypeLabelByCode != null) {
                    display = finTypeLabelByCode.get(key);
                }
                // 字典未命中时展示码本身，保证表格非空
                if (StringUtils.isEmpty(display)) {
                    display = key;
                }
                row.setFinTypeDisplay(display);
                row.setPurchaseAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
                row.setSaleAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
                row.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
                row.setReceivableAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
                row.setDiscountedAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
                row.setLineAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
                row.setLineCount(0L);
                mergedByFinType.put(key, row);
            }
            BigDecimal saleTotal = MonetaryAmountSupport.nullToZero(domainRow.getLineAmountTotal());
            BigDecimal purchaseTotal = MonetaryAmountSupport.nullToZero(
                resolveBundle.purchaseByDefinitionId.get(domainRow.getDefinitionId()));
            row.setSaleAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getSaleAmountTotal()).add(saleTotal)));
            row.setPurchaseAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getPurchaseAmountTotal()).add(purchaseTotal)));
            // 实收与售价同口径（对齐明细 Assembler：actualReceived = total_price 带符号）
            row.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getActualReceivedAmountTotal()).add(saleTotal)));
            row.setReceivableAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getReceivableAmountTotal())
                    .add(MonetaryAmountSupport.nullToZero(domainRow.getReceivableAmountTotal()))));
            row.setDiscountedAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getDiscountedAmountTotal())
                    .add(MonetaryAmountSupport.nullToZero(domainRow.getDiscountedAmountTotal()))));
            row.setLineAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getLineAmountTotal()).add(saleTotal)));
            long lineCount = 0L;
            if (row.getLineCount() != null) {
                lineCount = row.getLineCount();
            }
            row.setLineCount(lineCount + domainRow.getLineCount());
        }
        result.addAll(mergedByFinType.values());
        return result;
    }

    /**
     * 汇总行 → 页脚合计（结算刻度 2 位）。
     *
     * @param rows fin_type 汇总行
     * @return 页脚合计
     */
    public FeeDetailReportSummaryFooterDto assembleFooter(
        List<FeeDetailReportSummaryRowDto> rows) {
        FeeDetailReportSummaryFooterDto footer = new FeeDetailReportSummaryFooterDto();
        BigDecimal purchase = BigDecimal.ZERO;
        BigDecimal sale = BigDecimal.ZERO;
        BigDecimal actualReceived = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal discounted = BigDecimal.ZERO;
        BigDecimal lineAmount = BigDecimal.ZERO;
        long lineCountTotal = 0L;
        // 先按行级 6 位代数加总，再统一 scaleSettle（禁止逐行先 2 位再加）
        if (rows != null) {
            for (FeeDetailReportSummaryRowDto row : rows) {
                if (row == null) {
                    continue;
                }
                purchase = purchase.add(MonetaryAmountSupport.nullToZero(row.getPurchaseAmountTotal()));
                sale = sale.add(MonetaryAmountSupport.nullToZero(row.getSaleAmountTotal()));
                actualReceived =
                    actualReceived.add(MonetaryAmountSupport.nullToZero(row.getActualReceivedAmountTotal()));
                receivable = receivable.add(MonetaryAmountSupport.nullToZero(row.getReceivableAmountTotal()));
                discounted = discounted.add(MonetaryAmountSupport.nullToZero(row.getDiscountedAmountTotal()));
                lineAmount = lineAmount.add(MonetaryAmountSupport.nullToZero(row.getLineAmountTotal()));
                if (row.getLineCount() != null) {
                    lineCountTotal = lineCountTotal + row.getLineCount();
                }
            }
        }
        footer.setPurchaseAmountTotal(MonetaryAmountSupport.scaleSettle(purchase));
        footer.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(sale));
        footer.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleSettle(actualReceived));
        footer.setReceivableAmountTotal(MonetaryAmountSupport.scaleSettle(receivable));
        footer.setDiscountedAmountTotal(MonetaryAmountSupport.scaleSettle(discounted));
        footer.setLineAmountTotal(MonetaryAmountSupport.scaleSettle(lineAmount));
        footer.setLineCountTotal(lineCountTotal);
        return footer;
    }

    /**
     * 在 resolvedChargeItemScope 内解析 definitionId → fin_type_code，并累计带符号进价。
     *
     * @param query              预解析后的读查询
     * @param definitionIds      待解析 definition 集合
     * @param incompleteExCode   无法解析时异常码
     * @param incompleteMessage  异常提示
     * @return fin_type 与进价分桶
     */
    private FinTypeResolveBundle resolveFinTypeAndPurchaseByDefinition(
        OutpatientFeeDetailPageQuery query,
        LinkedHashSet<Long> definitionIds,
        String incompleteExCode,
        String incompleteMessage) {
        FinTypeResolveBundle bundle = new FinTypeResolveBundle();
        if (definitionIds == null || definitionIds.isEmpty()) {
            return bundle;
        }
        LinkedHashSet<Long> scopedChargeItemIds = new LinkedHashSet<>();
        if (query != null && query.getResolvedChargeItemScope() != null) {
            for (Long chargeItemId : query.getResolvedChargeItemScope()) {
                if (chargeItemId != null) {
                    scopedChargeItemIds.add(chargeItemId);
                }
            }
        }
        if (scopedChargeItemIds.isEmpty()) {
            throw new BusinessException(incompleteExCode, incompleteMessage);
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(scopedChargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        Map<Long, String> finTypeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItems);
        Map<Long, BigDecimal> purchaseByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountByChargeItemId(chargeItems);
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> paymentContextByChargeItemId = Map.of();
        if (query != null && query.getPaymentContextByChargeItemId() != null) {
            paymentContextByChargeItemId = query.getPaymentContextByChargeItemId();
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || chargeItem.getDefinitionId() == null) {
                continue;
            }
            if (!definitionIds.contains(chargeItem.getDefinitionId())) {
                continue;
            }
            String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
            if (StringUtils.isNotEmpty(finTypeCode)) {
                bundle.finTypeByDefinitionId.put(chargeItem.getDefinitionId(), finTypeCode.trim());
            }
            BigDecimal purchaseRaw = purchaseByChargeItemId.get(chargeItem.getId());
            if (purchaseRaw == null) {
                purchaseRaw = BigDecimal.ZERO;
            }
            BigDecimal signedPurchase = this.applyPaymentSign(purchaseRaw, paymentContextByChargeItemId.get(chargeItem.getId()));
            BigDecimal existing = bundle.purchaseByDefinitionId.get(chargeItem.getDefinitionId());
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            bundle.purchaseByDefinitionId.put(
                chargeItem.getDefinitionId(),
                MonetaryAmountSupport.scaleCalc(existing.add(signedPurchase)));
        }
        for (Long definitionId : definitionIds) {
            if (!bundle.finTypeByDefinitionId.containsKey(definitionId)) {
                throw new BusinessException(incompleteExCode, incompleteMessage);
            }
            if (!bundle.purchaseByDefinitionId.containsKey(definitionId)) {
                bundle.purchaseByDefinitionId.put(definitionId, MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
            }
        }
        return bundle;
    }

    /**
     * 退费 payment 链路进价取负（与域层汇总售价符号一致）。
     *
     * @param amount  未带符号进价
     * @param context payment 上下文
     * @return 带符号进价
     */
    private BigDecimal applyPaymentSign(
        BigDecimal amount,
        OutpatientFeeDetailPaymentContextQueryModel context) {
        BigDecimal raw = MonetaryAmountSupport.nullToZero(amount);
        if (context != null && Boolean.TRUE.equals(context.getRefundPayment())) {
            return MonetaryAmountSupport.applySignAtCalcScale(raw.abs(), new BigDecimal("-1"));
        }
        return MonetaryAmountSupport.scaleCalc(raw);
    }

    /**
     * fin_type 与进价按 definition 分桶中间态。
     */
    private static final class FinTypeResolveBundle {

        /** definitionId → fin_type_code。 */
        private final Map<Long, String> finTypeByDefinitionId = new LinkedHashMap<>();

        /** definitionId → 带符号进价合计。 */
        private final Map<Long, BigDecimal> purchaseByDefinitionId = new LinkedHashMap<>();
    }
}
