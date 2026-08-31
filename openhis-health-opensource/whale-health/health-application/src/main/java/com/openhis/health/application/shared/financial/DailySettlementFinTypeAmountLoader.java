/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentStaticSnapshotQueryModel;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.financial.DailySettlementRefundPaymentRules;
import com.openhis.health.domain.share.utils.FinTypeCodeMappingSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门/住院日结：按 {@code fin_type_code} 汇总展示金额（只读 enrich，B.6.1 querysupport）。
 *
 * <p>仅读取 {@code fin_payment_rec_static} 快照；禁止回落 {@code charge_item.total_price}。
 * 结算时分类快照由 {@link PaymentFinTypeStaticSnapshotWriter} 写入（金额=收费行 <strong>代数和</strong>，禁绝对值）。
 * 号源/挂号收费在写快照时已强制归入
 * {@link DailySettlementSharedConstants#CONSULTATION_FEE_FIN_TYPE_CODE}（诊察费 1012）。</p>
 */
@Component
@RequiredArgsConstructor
public class DailySettlementFinTypeAmountLoader {

    /** 定价行读侧：解析 charge_item.definition_id 对应历史 type_code（写快照时复用）。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 汇总展示用 payment 在 fin_type_code 各字典码下的金额。
     *
     * @param paymentModels           展示过滤后的 payment 读模型
     * @param staticRowsByPaymentId   paymentId → 统计快照
     * @return fin_type_code value → 合计金额
     * @throws BusinessException 业务收费流水缺少分类快照时
     */
    public Map<String, BigDecimal> aggregateFinTypeAmounts(
        List<PaymentReconciliationQueryModel> paymentModels,
        Map<Long, List<PaymentStaticSnapshotQueryModel>> staticRowsByPaymentId) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        if (paymentModels == null || paymentModels.isEmpty()) {
            return totals;
        }
        for (PaymentReconciliationQueryModel paymentModel : paymentModels) {
            if (paymentModel == null || paymentModel.getId() == null) {
                continue;
            }
            if (this.isDepositPayment(paymentModel)) {
                continue;
            }
            List<PaymentStaticSnapshotQueryModel> staticRows = null;
            if (staticRowsByPaymentId != null) {
                staticRows = staticRowsByPaymentId.get(paymentModel.getId());
            }
            if (staticRows == null || staticRows.isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    "支付流水缺少费用分类快照，无法汇总日结财务类型，paymentId=" + paymentModel.getId());
            }
            this.mergeStaticRows(totals, staticRows, this.isRefundPayment(paymentModel));
        }
        return totals;
    }

    /**
     * 批量解析收费明细 fin_type_code（结算写快照、费用明细细分类口径一致）。
     *
     * <p>分类仅锚 {@code definition_id} 历史定价行 {@code type_code}（挂号/号源强制诊察费）；
     * <strong>不</strong>依赖 {@code unit_price}/{@code total_price}。费用明细硬筛轻量投影
     * （未选单价）与完整实体均可调用；缺 {@code definition_id} 或锚点无法解析时整批失败。</p>
     *
     * @param chargeItems 收费明细列表（实体或仅填 id/definitionId/挂号识别字段的 stub）；空时返回空 Map
     * @return chargeItemId → fin_type_code
     * @throws BusinessException 任一条缺 definition_id，或定价锚点无法解析 fin_type
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
            // Gate：fin_type 只认定价锚点；禁止因投影未选单价误报「脏数据」
            this.requireDefinitionIdForFinType(chargeItem);
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
            // 保留快照代数符号（冲正负行已在写快照时入账）；退费单再整体取负
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
     * 校验收费行具备 fin_type 解析所需定价锚点；缺失时拒绝汇总。
     *
     * <p>单价/总额属金额展示与落库完整性范畴，由创建路径与金额 IF 保证；本方法不校验，
     * 以免费用明细硬筛 stub（Criteria 未选 {@code unit_price}）被误判为脏数据。</p>
     *
     * @param chargeItem 待解析收费明细（实体或 stub）
     * @throws BusinessException definition_id 为空
     */
    private void requireDefinitionIdForFinType(ChargeItem chargeItem) {
        if (chargeItem.getDefinitionId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "收费明细缺少定价锚点 definition_id，无法汇总财务类型，chargeItemId=" + chargeItem.getId());
        }
    }

    /**
     * 解析单条收费明细 fin_type：仅锚 charge_item.definition_id 对应历史定价行 type_code。
     *
     * @param chargeItem     收费明细（调用方已校验 definition_id；挂号识别可依赖 generateSource/serviceTable）
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

    private boolean isRefundPayment(PaymentReconciliationQueryModel paymentModel) {
        if (paymentModel == null) {
            return false;
        }
        return DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            paymentModel.getPaymentEnum(),
            paymentModel.getRelationId(),
            paymentModel.getStatusEnum());
    }

    private boolean isDepositPayment(PaymentReconciliationQueryModel paymentModel) {
        if (paymentModel == null) {
            return false;
        }
        return PaymentKind.HOSPITAL_DEPOSIT.equals(paymentModel.getKindEnum());
    }
}
