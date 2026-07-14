/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollChargeLineResolved;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * IF-IP-ARG-08：住院滚费定价解析与 Charge 行构建支撑。
 */
@Component
@RequiredArgsConstructor
public class NurseStationAutoRollWriteAssembler {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 解析诊疗活动开立/执行数量单位持久化码（packageUnitCode，禁止回落汉字缺省「次」）。
     *
     * @param activityId 诊疗活动定义主键
     * @return 单位 code；未维护时为 null
     */
    public String resolveActivityOrderUnitCode(Long activityId) {
        if (activityId == null) {
            return null;
        }
        ProductDefinitionDisplayDto product = this.loadActivityProduct(activityId);
        if (product == null) {
            return null;
        }
        return ProductDefinitionCatalogUnitBandsAssembler.resolveDoctorStationBandPricingUnitCode(product, null);
    }

    /**
     * 按已锚定的定价 id 解析滚费入账行（床位配置分支）。
     *
     * @param chargeDefinitionId 配置上的定价 id
     * @param activityId         床位服务 activityId
     * @param quantity           数量
     * @param organizationId     机构（定价择优上下文）
     * @return 解析结果
     */
    public AutoRollChargeLineResolved resolveAutoRollChargeLine(
        Long chargeDefinitionId,
        Long activityId,
        BigDecimal quantity,
        Long organizationId) {
        AutoRollChargeLineResolved resolved = new AutoRollChargeLineResolved();
        resolved.setResolved(false);
        if (chargeDefinitionId == null || activityId == null) {
            return resolved;
        }
        // Pre：按配置锚定的定价 id 加载定义行
        Map<Long, ChargeItemDefinition> definitionMap =
            chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(Collections.singleton(chargeDefinitionId));
        ChargeItemDefinition definition = definitionMap.get(chargeDefinitionId);
        if (!this.isChargeDefinitionUsable(definition)) {
            return resolved;
        }
        BigDecimal qty = quantity;
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            qty = BigDecimal.ONE;
        }
        BigDecimal unitPrice = definition.getPriceValue();
        // Pre：定价行无显式单价时，走目录择优（同 BR-IP-PRC-017）
        if (unitPrice == null) {
            ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
                .productTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION)
                .productDefinitionId(activityId)
                .organizationId(organizationId)
                .priceLines(chargeItemDefinitionQueryService.findSaleItemByInstanceId(
                    this.buildInstanceQuery(activityId)))
                .build();
            ChargeItemDefinition picked = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
            if (picked == null || picked.getPriceValue() == null) {
                return resolved;
            }
            unitPrice = picked.getPriceValue();
            definition = picked;
        }
        resolved.setResolved(true);
        resolved.setChargeDefinitionId(definition.getId());
        resolved.setActivityId(activityId);
        resolved.setUnitCode(this.resolveActivityOrderUnitCode(activityId));
        resolved.setUnitPrice(unitPrice);
        resolved.setTotalPrice(unitPrice.multiply(qty));
        return resolved;
    }

    /**
     * 护理分支：按 activityId 反查默认定价 id。
     *
     * @param activityId 护理 activityId
     * @return 定价 id；无则 null
     */
    public Long resolveNursingChargeDefinitionId(Long activityId) {
        if (activityId == null) {
            return null;
        }
        List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findSaleItemByInstanceId(
            this.buildInstanceQuery(activityId));
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
            .productTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION)
            .productDefinitionId(activityId)
            .priceLines(lines)
            .build();
        ChargeItemDefinition picked = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
        if (picked == null || !this.isChargeDefinitionUsable(picked)) {
            return null;
        }
        return picked.getId();
    }

    /**
     * 定价行是否可用于滚费落账。
     *
     * @param definition 定价行
     * @return 可用时 true
     */
    private boolean isChargeDefinitionUsable(ChargeItemDefinition definition) {
        if (definition == null || Boolean.TRUE.equals(definition.getIsDeleted())) {
            return false;
        }
        if (StringUtils.isNotEmpty(definition.getStatusEnum())
            && PublicationStatus.DISCARDED.getCode().equals(definition.getStatusEnum())) {
            return false;
        }
        return true;
    }

    /**
     * 构建按 activity 实例查价命令。
     *
     * @param activityId 活动定义 id
     * @return 查询命令
     */
    private ChargeItemDefinitionQueryCommand buildInstanceQuery(Long activityId) {
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        cmd.setInstanceId(activityId);
        return cmd;
    }

    /**
     * 加载诊疗活动产品读模型。
     *
     * @param activityId 活动定义主键
     * @return 产品读模型；缺失时 null
     */
    private ProductDefinitionDisplayDto loadActivityProduct(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> loaded =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        return loaded.get(ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId));
    }
}
