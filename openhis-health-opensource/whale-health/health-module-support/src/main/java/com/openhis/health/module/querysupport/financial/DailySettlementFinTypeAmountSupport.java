/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentStaticSnapshotQueryModel;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门/住院日结：按 {@code fin_type_code} 汇总展示金额（只读 enrich，B.6.1 querysupport）。
 *
 * <p>优先读取 {@code fin_payment_rec_static} 快照；快照未覆盖的收费项回落 payment {@code charge_item_ids}。
 * fin_type 解析：先 {@link ChargeItem#getDefinitionId()} 对应定价行 {@code type_code}；仍缺失时按
 * {@link ChargeItem#getProductTable()} + {@link ChargeItem#getProductId()} 批量加载目录实例定价行，
 * 统一取 {@code adm_charge_item_definition.type_code}（目录维护页「财务类型」，药/诊疗/耗材口径一致）。
 * 挂号费锚点强制归入 {@link DailySettlementSharedConstants#REGISTRATION_FEE_FIN_TYPE_CODE}。</p>
 */
@Component
@RequiredArgsConstructor
public class DailySettlementFinTypeAmountSupport {

    private final ChargeItemQueryService chargeItemQueryService;

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 汇总展示用 payment 在 fin_type_code 各字典码下的金额。
     *
     * @param paymentModels           展示过滤后的 payment 读模型
     * @param staticRowsByPaymentId   paymentId → 统计快照（可空 map）
     * @return fin_type_code value → 合计金额
     */
    public Map<String, BigDecimal> aggregateFinTypeAmounts(
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentStaticSnapshotQueryModel>> staticRowsByPaymentId) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        if (paymentModels == null || paymentModels.isEmpty()) {
            return totals;
        }
        Map<Long, Set<Long>> fallbackChargeItemIdsByPaymentId = new LinkedHashMap<>();
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel == null || paymentModel.getId() == null) {
                continue;
            }
            List<PaymentStaticSnapshotQueryModel> staticRows = null;
            if (staticRowsByPaymentId != null) {
                staticRows = staticRowsByPaymentId.get(paymentModel.getId());
            }
            Set<Long> staticCoveredChargeItemIds = new LinkedHashSet<>();
            if (staticRows != null && !staticRows.isEmpty()) {
                // Pre：快照行先入账；同时收集快照已覆盖的 charge_item，避免回落重复计数
                this.mergeStaticRows(totals, staticRows, this.isRefundPayment(paymentModel));
                for (PaymentStaticSnapshotQueryModel staticRow : staticRows) {
                    staticCoveredChargeItemIds.addAll(this.parseChargeItemIds(staticRow.getChargeItemIds()));
                }
            }
            LinkedHashSet<Long> fallbackChargeItemIds = new LinkedHashSet<>(
                this.parseChargeItemIds(paymentModel.getChargeItemIds()));
            fallbackChargeItemIds.removeAll(staticCoveredChargeItemIds);
            if (!fallbackChargeItemIds.isEmpty()) {
                fallbackChargeItemIdsByPaymentId.put(paymentModel.getId(), fallbackChargeItemIds);
            }
        }
        if (!fallbackChargeItemIdsByPaymentId.isEmpty()) {
            this.mergeChargeItemFallback(totals, paymentModels, fallbackChargeItemIdsByPaymentId);
        }
        return totals;
    }

    /**
     * 批量解析收费明细 fin_type_code（与日结汇总、费用明细细分类口径一致）。
     *
     * @param chargeItems 收费明细列表；空时返回空 Map
     * @return chargeItemId → fin_type_code
     */
    public Map<Long, String> mapFinTypeCodeByChargeItemId(List<ChargeItem> chargeItems) {
        Map<Long, String> finTypeByChargeItemId = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return finTypeByChargeItemId;
        }
        Set<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getDefinitionId() == null) {
                continue;
            }
            definitionIds.add(chargeItem.getDefinitionId());
        }
        Map<Long, ChargeItemDefinition> definitionById =
            this.chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
        Map<String, String> finTypeByCatalogProductKey = this.loadFinTypeCodeByCatalogPricingLine(chargeItems);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            String finTypeCode = this.resolveFinTypeCode(chargeItem, definitionById, finTypeByCatalogProductKey);
            if (StringUtils.isBlank(finTypeCode)) {
                continue;
            }
            finTypeByChargeItemId.put(chargeItem.getId(), finTypeCode);
        }
        return finTypeByChargeItemId;
    }

    private void mergeStaticRows(
        Map<String, BigDecimal> totals,
        List<PaymentStaticSnapshotQueryModel> staticRows,
        boolean negate) {
        for (PaymentStaticSnapshotQueryModel staticRow : staticRows) {
            if (staticRow == null || StringUtils.isBlank(staticRow.getTypeCode())) {
                continue;
            }
            BigDecimal lineAmount = staticRow.getAmount();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            if (negate) {
                lineAmount = lineAmount.negate();
            }
            String typeCode = this.normalizeCategoryCodeToFinType(staticRow.getTypeCode().trim());
            if (StringUtils.isBlank(typeCode)) {
                continue;
            }
            BigDecimal existing = totals.get(typeCode);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            totals.put(typeCode, existing.add(lineAmount));
        }
    }

    private void mergeChargeItemFallback(
        Map<String, BigDecimal> totals,
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, Set<Long>> fallbackChargeItemIdsByPaymentId) {
        Map<Long, PaymentReconciliationQueryModel> paymentById = new LinkedHashMap<>();
        Set<Long> chargeItemIds = new LinkedHashSet<>();
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel == null || paymentModel.getId() == null) {
                continue;
            }
            Set<Long> fallbackIds = fallbackChargeItemIdsByPaymentId.get(paymentModel.getId());
            if (fallbackIds == null || fallbackIds.isEmpty()) {
                continue;
            }
            paymentById.put(paymentModel.getId(), paymentModel);
            chargeItemIds.addAll(fallbackIds);
        }
        if (chargeItemIds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand chargeItemQueryCommand = new ChargeItemQueryCommand();
        chargeItemQueryCommand.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(chargeItemQueryCommand);
        Map<Long, String> finTypeByChargeItemId = this.mapFinTypeCodeByChargeItemId(chargeItems);
        Map<Long, Long> paymentIdByChargeItemId = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : fallbackChargeItemIdsByPaymentId.entrySet()) {
            for (Long chargeItemId : entry.getValue()) {
                paymentIdByChargeItemId.put(chargeItemId, entry.getKey());
            }
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            Long paymentId = paymentIdByChargeItemId.get(chargeItem.getId());
            PaymentReconciliationQueryModel paymentModel = paymentById.get(paymentId);
            if (paymentModel == null) {
                continue;
            }
            String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
            if (StringUtils.isBlank(finTypeCode)) {
                continue;
            }
            BigDecimal lineAmount = chargeItem.getTotalPrice();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            if (this.isRefundPayment(paymentModel)) {
                lineAmount = lineAmount.negate();
            }
            BigDecimal existing = totals.get(finTypeCode);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            totals.put(finTypeCode, existing.add(lineAmount));
        }
    }

    /**
     * 按 charge_item.product_table/product_id 批量加载目录实例定价行，统一取 {@code type_code}（财务类型）。
     *
     * @param chargeItems 待汇总收费明细
     * @return 目录复合键 → fin_type_code
     */
    private Map<String, String> loadFinTypeCodeByCatalogPricingLine(List<ChargeItem> chargeItems) {
        Map<String, String> finTypeByProductKey = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return finTypeByProductKey;
        }
        Map<String, Set<Long>> instanceIdsByTable = new LinkedHashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || StringUtils.isBlank(chargeItem.getProductTable())
                || chargeItem.getProductId() == null) {
                continue;
            }
            String productTable = chargeItem.getProductTable().trim();
            if (!this.isCatalogProductTable(productTable)) {
                continue;
            }
            instanceIdsByTable.computeIfAbsent(productTable, ignored -> new LinkedHashSet<>())
                .add(chargeItem.getProductId());
        }
        if (instanceIdsByTable.isEmpty()) {
            return finTypeByProductKey;
        }
        for (Map.Entry<String, Set<Long>> tableEntry : instanceIdsByTable.entrySet()) {
            ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
            queryCommand.setInstanceTable(tableEntry.getKey());
            queryCommand.setInstanceIds(new ArrayList<>(tableEntry.getValue()));
            List<ChargeItemDefinition> priceLines =
                this.chargeItemDefinitionQueryService.findPriceLinesByProjects(queryCommand);
            Map<String, List<ChargeItemDefinition>> linesByProductKey = new LinkedHashMap<>();
            for (ChargeItemDefinition priceLine : priceLines) {
                if (priceLine == null) {
                    continue;
                }
                String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    priceLine.getInstanceTable(), priceLine.getInstanceId());
                if (productKey == null) {
                    continue;
                }
                linesByProductKey.computeIfAbsent(productKey, ignored -> new ArrayList<>()).add(priceLine);
            }
            for (Map.Entry<String, List<ChargeItemDefinition>> groupEntry : linesByProductKey.entrySet()) {
                String finTypeCode = this.resolveFinTypeFromPricingLines(groupEntry.getValue());
                if (StringUtils.isBlank(finTypeCode)) {
                    continue;
                }
                finTypeByProductKey.put(groupEntry.getKey(), finTypeCode);
            }
        }
        return finTypeByProductKey;
    }

    /**
     * 从同一目录实例的多条定价行中解析财务类型：优先 {@link PriceType#SALE} 行 {@code type_code}。
     *
     * @param priceLines 同一实例下定价行
     * @return fin_type_code；无法解析时返回 null
     */
    private String resolveFinTypeFromPricingLines(List<ChargeItemDefinition> priceLines) {
        if (priceLines == null || priceLines.isEmpty()) {
            return null;
        }
        for (ChargeItemDefinition priceLine : priceLines) {
            if (priceLine == null || !PriceType.SALE.getCode().equals(priceLine.getPriceTypeCode())) {
                continue;
            }
            if (StringUtils.isNotBlank(priceLine.getTypeCode())) {
                return this.normalizeCategoryCodeToFinType(priceLine.getTypeCode().trim());
            }
        }
        for (ChargeItemDefinition priceLine : priceLines) {
            if (priceLine != null && StringUtils.isNotBlank(priceLine.getTypeCode())) {
                return this.normalizeCategoryCodeToFinType(priceLine.getTypeCode().trim());
            }
        }
        return null;
    }

    /**
     * 解析单条收费明细 fin_type：charge_item.definition_id 定价行优先，其次目录实例定价行 type_code。
     *
     * @param chargeItem                 收费明细
     * @param definitionById             definitionId → 定价行
     * @param finTypeByCatalogProductKey 目录复合键 → fin_type
     * @return fin_type_code；无法解析时返回 null
     */
    private String resolveFinTypeCode(
        ChargeItem chargeItem,
        Map<Long, ChargeItemDefinition> definitionById,
        Map<String, String> finTypeByCatalogProductKey) {
        if (this.isRegistrationChargeItem(chargeItem)) {
            return DailySettlementSharedConstants.REGISTRATION_FEE_FIN_TYPE_CODE;
        }
        if (chargeItem.getDefinitionId() != null && definitionById != null) {
            ChargeItemDefinition pricingLine = definitionById.get(chargeItem.getDefinitionId());
            if (pricingLine != null && StringUtils.isNotBlank(pricingLine.getTypeCode())) {
                String finTypeCode = this.normalizeCategoryCodeToFinType(pricingLine.getTypeCode().trim());
                if (StringUtils.isNotBlank(finTypeCode)) {
                    return finTypeCode;
                }
            }
        }
        if (StringUtils.isBlank(chargeItem.getProductTable()) || chargeItem.getProductId() == null
            || finTypeByCatalogProductKey == null) {
            return null;
        }
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            chargeItem.getProductTable().trim(), chargeItem.getProductId());
        if (productKey == null) {
            return null;
        }
        return finTypeByCatalogProductKey.get(productKey);
    }

    /**
     * 定价行 {@code type_code} 归一化为 fin_type 字典码（含历史药品短码 1/2/3/4 映射）。
     *
     * @param categoryCode 定价行 type_code 或快照 type_code
     * @return fin_type_code；空白时返回 null
     */
    private String normalizeCategoryCodeToFinType(String categoryCode) {
        if (StringUtils.isBlank(categoryCode)) {
            return null;
        }
        String normalized = categoryCode.trim();
        if (DailySettlementSharedConstants.MEDICATION_CATEGORY_WESTERN_CODE.equals(normalized)) {
            return DailySettlementSharedConstants.WESTERN_MEDICINE_FIN_TYPE_CODE;
        }
        if (DailySettlementSharedConstants.MEDICATION_CATEGORY_PATENT_CHINESE_CODE.equals(normalized)) {
            return DailySettlementSharedConstants.CHINESE_PATENT_MEDICINE_FIN_TYPE_CODE;
        }
        if (DailySettlementSharedConstants.MEDICATION_CATEGORY_HERBAL_CODE.equals(normalized)) {
            return DailySettlementSharedConstants.CHINESE_HERBAL_MEDICINE_FIN_TYPE_CODE;
        }
        if (DailySettlementSharedConstants.MEDICATION_CATEGORY_PURCHASED_CODE.equals(normalized)) {
            return DailySettlementSharedConstants.PURCHASED_MEDICINE_FIN_TYPE_CODE;
        }
        return normalized;
    }

    private boolean isCatalogProductTable(String productTable) {
        if (StringUtils.isBlank(productTable)) {
            return false;
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTable)) {
            return true;
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productTable)) {
            return true;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTable)) {
            return true;
        }
        return TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(productTable);
    }

    private boolean isRegistrationChargeItem(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return false;
        }
        if (GenerateSource.REGISTRATION_REGISTER.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            return true;
        }
        return TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(chargeItem.getServiceTable());
    }

    /**
     * 逆向退费单（UN_PAY / relationId 指向原单）须在日结汇总中取负；原单 REFUNDED 仍按正向收费项入账。
     */
    private boolean isRefundPayment(PaymentReconciliationQueryModel paymentModel) {
        if (paymentModel == null) {
            return false;
        }
        if (paymentModel.getRelationId() != null) {
            return true;
        }
        return PaymentType.UN_PAY.getCode().equals(paymentModel.getPaymentEnum());
    }

    private List<Long> parseChargeItemIds(String chargeItemIds) {
        List<Long> result = new ArrayList<>();
        if (StringUtils.isBlank(chargeItemIds)) {
            return result;
        }
        String[] parts = chargeItemIds.split(",");
        for (String part : parts) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            String trimmed = part.trim();
            try {
                result.add(Long.valueOf(trimmed));
            } catch (NumberFormatException numberFormatException) {
                // 脏数据跳过，避免整单查询失败
            }
        }
        return result;
    }
}
