/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.assembler;

import com.openhis.health.application.medicaltech.charging.dto.MedicalTechDevicePricingWriteItemDto;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.constants.MedicalTechChargingConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IF-MT-CHG-02 写路径专项装配：权威定价重算（同护士耗材路径，锁定 performLocation=行库房）。
 */
@Component
@RequiredArgsConstructor
public class MedicalTechChargingWriteAssembler {

    /** 写路径权威定价（LOT_NUMBER_PRICE / SALE）。 */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /** 产品定义展示读模型批量装载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 写路径入账前批量重算价目与单价（后端定权威；前端 unitPrice 仅展示参考）。
     *
     * @param items          待提交写行（须已带 locationId）
     * @param organizationId 开立机构（登录 TECH 或就诊科室；供 FIFO 定价）
     * @param encounterId    就诊主键
     */
    public void reapplyAuthoritativeWritePricing(
        List<MedicalTechDevicePricingWriteItemDto> items,
        Long organizationId,
        Long encounterId) {
        if (items == null || items.isEmpty()) {
            return;
        }
        // Pre：本批写行涉及的耗材定义去重，一次 batch load
        LinkedHashMap<String, ProductDefinitionBatchLookup> lookupByKey = new LinkedHashMap<>();
        for (MedicalTechDevicePricingWriteItemDto row : items) {
            if (row == null || row.getDefinitionId() == null) {
                continue;
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.ADM_DEVICE_DEFINITION, row.getDefinitionId());
            if (!lookupByKey.containsKey(productKey)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
                lookup.setProductDefinitionId(row.getDefinitionId());
                lookupByKey.put(productKey, lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookupByKey.values()));
        for (MedicalTechDevicePricingWriteItemDto row : items) {
            // Gate：缺定义或库房视为非法划价行
            if (row == null || row.getDefinitionId() == null || row.getLocationId() == null) {
                throw new BusinessException(
                    MedicalTechChargingConstants.EX_MT_CHG_202, "划价项目无效或价格异常，请重新选品");
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.ADM_DEVICE_DEFINITION, row.getDefinitionId());
            ProductDefinitionDisplayDto productRow = productByKey.get(productKey);
            ChargeItemDefinition selected = this.clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
                TableNameConstants.ADM_DEVICE_DEFINITION,
                row.getDefinitionId(),
                productRow,
                organizationId,
                encounterId,
                row.getLocationId(),
                null,
                row.getLotNumber());
            // Gate：无价目时拒绝落账
            if (selected == null || selected.getPriceValue() == null) {
                throw new BusinessException(
                    MedicalTechChargingConstants.EX_MT_CHG_202, "划价项目无效或价格异常，请重新选品");
            }
            BigDecimal authoritativeUnitPrice = this.resolveAuthoritativeUnitPriceForSubmittedUnit(
                productRow, selected, row.getUnitCode());
            row.setChargeItemDefinitionId(selected.getId());
            row.setUnitPrice(authoritativeUnitPrice);
            // 数量已 Gate 为正；按权威单价算行金额
            if (row.getQuantity() == null) {
                throw new BusinessException(
                    MedicalTechChargingConstants.EX_MT_CHG_202, "划价项目无效或价格异常，请重新选品");
            }
            row.setTotalPrice(MonetaryAmountSupport.lineTotal(authoritativeUnitPrice, row.getQuantity()));
        }
    }

    /**
     * 将包装锚定价线性换算为提交 unitCode 对应槽单价。
     *
     * @param productRow        产品读模型；空时回落包装锚价
     * @param selected          命中定价行
     * @param submittedUnitCode 前端提交划价单位
     * @return 与提交单位同口径的入账单价
     */
    private BigDecimal resolveAuthoritativeUnitPriceForSubmittedUnit(
        ProductDefinitionDisplayDto productRow,
        ChargeItemDefinition selected,
        String submittedUnitCode) {
        BigDecimal packageAnchoredPrice = selected.getPriceValue();
        // 无产品读模型或无单位：直接用包装锚价
        if (productRow == null || StringUtils.isEmpty(submittedUnitCode)) {
            return packageAnchoredPrice;
        }
        String bandPricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler
            .resolveDoctorStationBandPricingUnitCode(productRow, selected.getPriceTypeCode());
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow, bandPricingUnitCode, packageAnchoredPrice);
        // 无单位槽：回落包装锚价
        if (bands == null || bands.isEmpty()) {
            return packageAnchoredPrice;
        }
        String normalizedUnitCode = submittedUnitCode.trim();
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || StringUtils.isEmpty(band.getUnitCode())) {
                continue;
            }
            // 命中提交单位：用槽单价；槽价空则回落包装锚价
            if (normalizedUnitCode.equals(band.getUnitCode().trim())) {
                if (band.getUnitPrice() != null) {
                    return band.getUnitPrice();
                }
                return packageAnchoredPrice;
            }
        }
        // 提交单位不在槽内：回落包装锚价（与护士写路径一致）
        return packageAnchoredPrice;
    }
}
