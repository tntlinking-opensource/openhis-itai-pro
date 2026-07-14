/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

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
import com.openhis.health.domain.share.financial.DailySettlementRefundPaymentRules;
import com.openhis.health.domain.share.utils.FinTypeCodeMappingSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
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
public class DailySettlementFinTypeAmountLoader {

    /** 收费明细读侧：回落汇总时批量加载 charge_item。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 定价行读侧：解析 charge_item.definition_id 对应历史 type_code。 */
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
            boolean hasStaticRows = staticRows != null && !staticRows.isEmpty();
            if (hasStaticRows) {
                // Pre：快照行先入账；同时收集快照已覆盖的 charge_item，避免回落重复计数
                this.mergeStaticRows(totals, staticRows, this.isRefundPayment(paymentModel));
                for (PaymentStaticSnapshotQueryModel staticRow : staticRows) {
                    staticCoveredChargeItemIds.addAll(this.parseChargeItemIds(staticRow.getChargeItemIds()));
                }
                // Pre：已有 static 时整单禁止回落 charge_item（快照未带 charge_item_ids 时亦如此，BR-OP-DCL-011）
                continue;
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

    /**
     * 将 {@code fin_payment_rec_static} 快照行按 fin_type 累加入账；退费单取负。
     *
     * @param totals     累加目标（就地修改）
     * @param staticRows 单 payment 统计快照行
     * @param negate     是否按退费取负
     */
    private void mergeStaticRows(
        Map<String, BigDecimal> totals,
        List<PaymentStaticSnapshotQueryModel> staticRows,
        boolean negate) {
        for (PaymentStaticSnapshotQueryModel staticRow : staticRows) {
            if (staticRow == null || StringUtils.isEmpty(staticRow.getTypeCode())) {
                continue;
            }
            BigDecimal lineAmount = staticRow.getAmount();
            if (lineAmount == null) {
                lineAmount = BigDecimal.ZERO;
            }
            lineAmount = lineAmount.abs();
            if (negate) {
                lineAmount = lineAmount.negate();
            }
            String typeCode = this.normalizeCategoryCodeToFinType(staticRow.getTypeCode().trim());
            if (StringUtils.isEmpty(typeCode)) {
                continue;
            }
            BigDecimal existing = totals.get(typeCode);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            totals.put(typeCode, existing.add(lineAmount));
        }
    }

    /**
     * 快照未覆盖的 payment 按 charge_item 回落汇总：批量读明细后按 fin_type 入账。
     *
     * @param totals                          fin_type 累加目标（就地修改）
     * @param paymentModels                   展示过滤后的 payment 读模型
     * @param fallbackChargeItemIdsByPaymentId paymentId → 待回落 charge_item 集合
     */
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
        Map<Long, ChargeItem> chargeItemById = new LinkedHashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                chargeItemById.put(chargeItem.getId(), chargeItem);
            }
        }
        Map<Long, String> finTypeByChargeItemId = this.mapFinTypeCodeByChargeItemId(chargeItems);
        // Pre：各 payment 独立回落，正向/逆向各自带符号入账（与费用明细逐 payment 取负一致）
        for (Map.Entry<Long, Set<Long>> entry : fallbackChargeItemIdsByPaymentId.entrySet()) {
            PaymentReconciliationQueryModel paymentModel = paymentById.get(entry.getKey());
            if (paymentModel == null) {
                continue;
            }
            boolean refundPayment = this.isRefundPayment(paymentModel);
            for (Long chargeItemId : entry.getValue()) {
                ChargeItem chargeItem = chargeItemById.get(chargeItemId);
                if (chargeItem == null) {
                    continue;
                }
                String finTypeCode = finTypeByChargeItemId.get(chargeItemId);
                BigDecimal lineAmount = chargeItem.getTotalPrice();
                if (lineAmount == null) {
                    lineAmount = BigDecimal.ZERO;
                }
                lineAmount = lineAmount.abs();
                if (refundPayment) {
                    lineAmount = lineAmount.negate();
                }
                BigDecimal existing = totals.get(finTypeCode);
                if (existing == null) {
                    existing = BigDecimal.ZERO;
                }
                totals.put(finTypeCode, existing.add(lineAmount));
            }
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
        if (StringUtils.isEmpty(pricingLine.getTypeCode())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "定价行缺少财务类型 type_code，无法汇总，definitionId=" + chargeItem.getDefinitionId());
        }
        String finTypeCode = this.normalizeCategoryCodeToFinType(pricingLine.getTypeCode().trim());
        if (StringUtils.isEmpty(finTypeCode)) {
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

    /**
     * 号源/挂号收费须强制归入诊察费 fin_type（1012），与 BR-OP-DCL-011 口径一致。
     *
     * @param chargeItem 收费明细
     * @return 挂号类收费为 true
     */
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
     * 逆向退费单须在日结 fin_type 汇总中取负（**BR-OP-DCL-011** / **BR-OP-FDT-012**）。
     *
     * @param paymentModel payment 读模型
     * @return 是否退费单
     */
    private boolean isRefundPayment(PaymentReconciliationQueryModel paymentModel) {
        if (paymentModel == null) {
            return false;
        }
        return DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            paymentModel.getPaymentEnum(),
            paymentModel.getRelationId(),
            paymentModel.getStatusEnum());
    }

    /**
     * 解析 payment/charge_item 逗号分隔 ID 串；非法数字跳过不阻断整单。
     *
     * @param chargeItemIds 逗号分隔 charge_item 主键串
     * @return 解析成功的 ID 列表
     */
    private List<Long> parseChargeItemIds(String chargeItemIds) {
        List<Long> result = new ArrayList<>();
        if (StringUtils.isEmpty(chargeItemIds)) {
            return result;
        }
        String[] parts = chargeItemIds.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
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
