/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentStaticSnapshotQueryModel;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.share.utils.FinTypeCodeMappingSupport;
import com.openhis.whale.core.application.exception.BusinessException;
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
 * fin_type 解析：仅锚 {@link ChargeItem#getDefinitionId()} 对应历史定价行 {@code type_code}；
 * {@code unit_price}/{@code total_price} 或 {@code definition_id} 缺失时直接 {@link BusinessException}，
 * 禁止回落当前目录定价行。号源/挂号收费强制归入
 * {@link DailySettlementSharedConstants#CONSULTATION_FEE_FIN_TYPE_CODE}（诊察费 1012）。</p>
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
     * @throws BusinessException 回落 charge_item 时单价/总额或 definition_id 缺失，或定价锚点无法解析 fin_type
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
     * @throws BusinessException 任一条 charge_item 单价/总额或 definition_id 缺失，或定价锚点无法解析 fin_type
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
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            this.requirePersistedChargeItemPriceAndDefinitionId(chargeItem);
            String finTypeCode = this.resolveFinTypeCode(chargeItem, definitionById);
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
            BigDecimal lineAmount = chargeItem.getTotalPrice();
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
     * 校验 charge_item 已落库单价、总额与定价锚点；缺失时拒绝汇总。
     *
     * @param chargeItem 待汇总收费明细
     * @throws BusinessException 单价/总额或 definition_id 为空
     */
    private void requirePersistedChargeItemPriceAndDefinitionId(ChargeItem chargeItem) {
        if (chargeItem.getDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "收费明细缺少定价锚点 definition_id，无法汇总财务类型，chargeItemId=" + chargeItem.getId());
        }
        if (chargeItem.getUnitPrice() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "收费明细单价为空，无法汇总财务类型，chargeItemId=" + chargeItem.getId());
        }
        if (chargeItem.getTotalPrice() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "收费明细总额为空，无法汇总财务类型，chargeItemId=" + chargeItem.getId());
        }
    }

    /**
     * 解析单条收费明细 fin_type：仅锚 charge_item.definition_id 对应历史定价行 type_code。
     *
     * @param chargeItem     收费明细（调用方已校验单价/总额/definition_id）
     * @param definitionById definitionId → 定价行
     * @return fin_type_code
     * @throws BusinessException 定价锚点缺失或 type_code 无法解析
     */
    private String resolveFinTypeCode(
        ChargeItem chargeItem,
        Map<Long, ChargeItemDefinition> definitionById) {
        if (this.isRegistrationChargeItem(chargeItem)) {
            return DailySettlementSharedConstants.CONSULTATION_FEE_FIN_TYPE_CODE;
        }
        ChargeItemDefinition pricingLine = null;
        if (definitionById != null) {
            pricingLine = definitionById.get(chargeItem.getDefinitionId());
        }
        if (pricingLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_NOT_FOUND,
                "收费明细定价锚点不存在，无法汇总财务类型，chargeItemId="
                    + chargeItem.getId()
                    + ", definitionId="
                    + chargeItem.getDefinitionId());
        }
        if (StringUtils.isBlank(pricingLine.getTypeCode())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "定价行缺少财务类型 type_code，无法汇总，definitionId=" + chargeItem.getDefinitionId());
        }
        String finTypeCode = this.normalizeCategoryCodeToFinType(pricingLine.getTypeCode().trim());
        if (StringUtils.isBlank(finTypeCode)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "定价行财务类型无法归一化，definitionId=" + chargeItem.getDefinitionId());
        }
        return finTypeCode;
    }

    /**
     * 定价行 {@code type_code} 归一化为 fin_type 字典码（含历史药品短码 1/2/3/4 映射）。
     *
     * @param categoryCode 定价行 type_code 或快照 type_code
     * @return fin_type_code；空白时返回 null
     */
    private String normalizeCategoryCodeToFinType(String categoryCode) {
        return FinTypeCodeMappingSupport.normalizeCategoryCodeToFinType(categoryCode);
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
