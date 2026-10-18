/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.integration.peis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.zxplat.application.shared.advice.OrderChargePricingAssembler;
import com.zxplat.application.shared.product.ClinicalCatalogPriceResolver;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.plugin.peis.domain.port.HisProductCatalogPort;

import lombok.RequiredArgsConstructor;

/**
 * HIS 适配：产品展示 / 草稿定价 / 行金额 → {@link HisProductCatalogPort}。
 */
@Component
@RequiredArgsConstructor
public class HisProductCatalogPortAdapter implements HisProductCatalogPort {

    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    @Override
    public Map<String, ProductDefinitionDisplayDto> loadProductDisplayAsMap(
            List<ProductDefinitionBatchLookup> lookups) {
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    @Override
    public ChargeItemDefinition resolveDraftChargePriceLine(
            String productTableName,
            Long productDefinitionId,
            ProductDefinitionDisplayDto productRow,
            Long organizationId,
            Long encounterId) {
        return clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
                productTableName,
                productDefinitionId,
                productRow,
                organizationId,
                encounterId,
                null,
                null);
    }

    @Override
    public void applyOrderChargePricing(
            CreateChargeItemCommand.Item item,
            ProductDefinitionDisplayDto productRow,
            ChargeItemDefinition priceLine,
            BigDecimal quantity,
            String quantityUnitCode) {
        OrderChargePricingAssembler.applyOrderChargePricing(
                item, productRow, priceLine, quantity, quantityUnitCode);
    }
}
