/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.share.constants.PractitionerWorkloadConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 科室/医生工作量行成本 enrich（BR-RPT-WKL-010；只读，B.6.1 querysupport）。
 *
 * <p>锚 {@code charge_item.definition_id} 历史定价行；诊疗 PURCHASE 缺失回退 SALE；药耗优先 LOT_NUMBER_COST。
 * 禁止回落当前目录 ACTIVE 价覆盖历史业务。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadCostEnricher {

    private final ChargeItemQueryService chargeItemQueryService;

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 在 payment 预解析 scope 内批量计算 chargeItem 行成本。
     *
     * @param query               须含 {@link PractitionerWorkloadPageQuery#getResolvedPaymentChargeItemLinks()}
     * @param scopedChargeItemIds 当前筛选（科室/医生/就诊）命中后的 chargeItem 主键；空集合时直接返回空 map
     * @return chargeItemId → 行成本（6 位 HALF_UP；退费行由调用方按 payment 符号冲减）
     * @throws BusinessException 成本锚点无法解析
     */
    public Map<Long, BigDecimal> computeCostByChargeItemScope(
        PractitionerWorkloadPageQuery query,
        Collection<Long> scopedChargeItemIds) {
        Map<Long, BigDecimal> costByChargeItemId = new LinkedHashMap<>();
        if (query == null) {
            return costByChargeItemId;
        }
        LinkedHashSet<Long> allowedChargeItemIds = this.normalizeChargeItemIdScope(scopedChargeItemIds);
        if (allowedChargeItemIds.isEmpty()) {
            return costByChargeItemId;
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return costByChargeItemId;
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null) {
                continue;
            }
            // 仅计算当前筛选 scope 内 chargeItem，避免其它医生脏数据拖垮整单（BR-RPT-WKL-005）
            if (!allowedChargeItemIds.contains(link.getChargeItemId())) {
                continue;
            }
            chargeItemIds.add(link.getChargeItemId());
        }
        if (chargeItemIds.isEmpty()) {
            return costByChargeItemId;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        Set<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getDefinitionId() != null) {
                definitionIds.add(chargeItem.getDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> definitionById =
            this.loadDefinitionSnapshotWithInstanceSiblings(definitionIds);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal lineCost = this.resolveLineCost(chargeItem, definitionById);
            costByChargeItemId.put(chargeItem.getId(), lineCost);
        }
        return costByChargeItemId;
    }

    /**
     * 归一化 chargeItem 主键 scope；null/空元素忽略。
     */
    private LinkedHashSet<Long> normalizeChargeItemIdScope(Collection<Long> scopedChargeItemIds) {
        LinkedHashSet<Long> allowedChargeItemIds = new LinkedHashSet<>();
        if (scopedChargeItemIds == null) {
            return allowedChargeItemIds;
        }
        for (Long chargeItemId : scopedChargeItemIds) {
            if (chargeItemId != null) {
                allowedChargeItemIds.add(chargeItemId);
            }
        }
        return allowedChargeItemIds;
    }

    /**
     * 加载定价锚点及同 instance 兄弟行（PURCHASE/SALE 回落须命中历史快照，BR-RPT-WKL-010）。
     */
    private Map<Long, ChargeItemDefinition> loadDefinitionSnapshotWithInstanceSiblings(Collection<Long> definitionIds) {
        Map<Long, ChargeItemDefinition> definitionById =
            this.chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
        LinkedHashSet<String> visitedInstanceKeys = new LinkedHashSet<>();
        List<ChargeItemDefinition> seedDefinitions = new ArrayList<>(definitionById.values());
        for (ChargeItemDefinition seed : seedDefinitions) {
            if (seed == null || seed.getInstanceId() == null) {
                continue;
            }
            String instanceKey = this.buildInstanceKey(seed.getInstanceTable(), seed.getInstanceId());
            if (!visitedInstanceKeys.add(instanceKey)) {
                continue;
            }
            List<ChargeItemDefinition> siblings =
                this.chargeItemDefinitionQueryService.listHistoricalDefinitionsByInstance(
                    seed.getInstanceTable(), seed.getInstanceId());
            for (ChargeItemDefinition sibling : siblings) {
                if (sibling != null && sibling.getId() != null) {
                    definitionById.put(sibling.getId(), sibling);
                }
            }
        }
        return definitionById;
    }

    /**
     * 收费实例复合键（instanceTable:instanceId），表名为空时仅 id 字符串。
     *
     * @param instanceTable 实例表名
     * @param instanceId    实例 id
     * @return Map 碰撞安全键
     */
    private String buildInstanceKey(String instanceTable, Long instanceId) {
        if (StringUtils.isEmpty(instanceTable)) {
            return String.valueOf(instanceId);
        }
        return instanceTable.trim() + ":" + instanceId;
    }

    /**
     * 单行成本：每包装单价 × 数量（BR-RPT-WKL-010）。
     *
     * @param chargeItem      收费明细
     * @param definitionById  历史定价行快照
     * @return 行成本
     */
    private BigDecimal resolveLineCost(
        ChargeItem chargeItem,
        Map<Long, ChargeItemDefinition> definitionById) {
        if (chargeItem.getDefinitionId() == null) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_103,
                "统计数据不完整，请联系管理员核对收费项目配置");
        }
        ChargeItemDefinition definition = definitionById.get(chargeItem.getDefinitionId());
        if (definition == null) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_103,
                "统计数据不完整，请联系管理员核对收费项目配置");
        }
        BigDecimal unitCost = this.resolveUnitCostPrice(chargeItem, definition, definitionById);
        BigDecimal quantity = chargeItem.getQuantityValue();
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        return MonetaryAmountSupport.lineTotal(unitCost, quantity);
    }

    /**
     * 解析每包装成本单价：药耗 LOT_NUMBER_COST/PURCHASE；诊疗 PURCHASE→SALE 回退。
     */
    private BigDecimal resolveUnitCostPrice(
        ChargeItem chargeItem,
        ChargeItemDefinition definition,
        Map<Long, ChargeItemDefinition> definitionById) {
        String priceTypeCode = definition.getPriceTypeCode();
        if (StringUtils.isNotEmpty(priceTypeCode)) {
            if (PriceType.PURCHASE.getCode().equals(priceTypeCode)
                || PriceType.LOT_NUMBER_COST.getCode().equals(priceTypeCode)) {
                return this.requirePriceValue(definition);
            }
            if (PriceType.SALE.getCode().equals(priceTypeCode)
                && this.isActivityServiceTable(chargeItem.getServiceTable())) {
                // 诊疗仅有售价时按院点规则成本=售价
                return this.requirePriceValue(definition);
            }
        }
        if (this.isMedicationOrDeviceServiceTable(chargeItem.getServiceTable())) {
            // 药耗无批号价时回落 PURCHASE 历史行
            ChargeItemDefinition purchase = this.findSiblingDefinitionByPriceType(
                definition, definitionById, PriceType.PURCHASE.getCode());
            if (purchase != null) {
                return this.requirePriceValue(purchase);
            }
        }
        if (this.isRegistrationChargeItem(chargeItem) || this.isActivityServiceTable(chargeItem.getServiceTable())) {
            return this.resolveActivityLikeUnitCostPrice(definition, definitionById);
        }
        throw new BusinessException(
            PractitionerWorkloadConstants.EX_RPT_WKL_103,
            "统计数据不完整，请联系管理员核对收费项目配置");
    }

    /**
     * 诊疗/挂号：PURCHASE 优先，缺失回退 SALE（对齐 AS-RPT-001 §1.3.1）。
     */
    private BigDecimal resolveActivityLikeUnitCostPrice(
        ChargeItemDefinition definition,
        Map<Long, ChargeItemDefinition> definitionById) {
        ChargeItemDefinition purchase = this.findSiblingDefinitionByPriceType(
            definition, definitionById, PriceType.PURCHASE.getCode());
        if (purchase != null) {
            return this.requirePriceValue(purchase);
        }
        ChargeItemDefinition sale = this.findSiblingDefinitionByPriceType(
            definition, definitionById, PriceType.SALE.getCode());
        if (sale != null) {
            return this.requirePriceValue(sale);
        }
        String priceTypeCode = definition.getPriceTypeCode();
        if (PriceType.SALE.getCode().equals(priceTypeCode) || PriceType.PURCHASE.getCode().equals(priceTypeCode)) {
            return this.requirePriceValue(definition);
        }
        throw new BusinessException(
            PractitionerWorkloadConstants.EX_RPT_WKL_103,
            "统计数据不完整，请联系管理员核对收费项目配置");
    }

    /**
     * 同 instance 下按 price_type 查找兄弟定价行（历史快照，非当前 ACTIVE 目录）。
     */
    private ChargeItemDefinition findSiblingDefinitionByPriceType(
        ChargeItemDefinition anchor,
        Map<Long, ChargeItemDefinition> definitionById,
        String priceTypeCode) {
        if (anchor == null || anchor.getInstanceId() == null) {
            return null;
        }
        for (ChargeItemDefinition candidate : definitionById.values()) {
            if (candidate == null || candidate.getInstanceId() == null) {
                continue;
            }
            if (!anchor.getInstanceId().equals(candidate.getInstanceId())) {
                continue;
            }
            if (StringUtils.isNotEmpty(anchor.getInstanceTable())
                && StringUtils.isNotEmpty(candidate.getInstanceTable())
                && !anchor.getInstanceTable().trim().equals(candidate.getInstanceTable().trim())) {
                continue;
            }
            if (StringUtils.isEmpty(candidate.getPriceTypeCode())) {
                continue;
            }
            if (priceTypeCode.equals(candidate.getPriceTypeCode())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 定价行必须有非空 price_value，否则整单失败（EX-RPT-WKL-103）。
     */
    private BigDecimal requirePriceValue(ChargeItemDefinition definition) {
        if (definition.getPriceValue() == null) {
            throw new BusinessException(
                PractitionerWorkloadConstants.EX_RPT_WKL_103,
                "统计数据不完整，请联系管理员核对收费项目配置");
        }
        return definition.getPriceValue();
    }

    /**
     * 服务表是否为诊疗类（wkf_service_request）。
     *
     * @param serviceTable 持久化 service_table
     * @return 诊疗为 true
     */
    private boolean isActivityServiceTable(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            return false;
        }
        return TableNameConstants.WKF_SERVICE_REQUEST.equals(serviceTable.trim());
    }

    /**
     * 服务表是否为药/耗请求表（med_medication_request 或 wkf_device_request）。
     *
     * @param serviceTable 持久化 service_table
     * @return 药耗为 true
     */
    private boolean isMedicationOrDeviceServiceTable(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            return false;
        }
        String normalized = serviceTable.trim();
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)) {
            return true;
        }
        return TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized);
    }

    /** 识别挂号 chargeItem（与工作量读侧口径一致）。 */
    private boolean isRegistrationChargeItem(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return false;
        }
        if (GenerateSource.REGISTRATION_REGISTER.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            return true;
        }
        return PractitionerWorkloadConstants.CHARGE_ITEM_CONTEXT_REGISTER.equals(chargeItem.getContextEnum());
    }
}
