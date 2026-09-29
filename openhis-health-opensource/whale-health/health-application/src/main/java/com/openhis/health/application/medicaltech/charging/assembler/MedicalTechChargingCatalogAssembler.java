/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.assembler;

import com.openhis.health.application.medicaltech.charging.dto.MedicalTechDeviceCatalogRowDto;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * IF-MT-CHG-01：医技耗材选品目录装配（库存驱动；锁定 TECH 下属耗材房）。
 *
 * <p>同构护士划价耗材库存目录（含 unitBands），但不走入院科室取药模板。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalTechChargingCatalogAssembler {

    /** 临床目录价目解析（批号价 / 参考售价）。 */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /** 产品定义展示读模型批量装载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 按库存批号页装配医技耗材选品行。
     *
     * @param inventoryRows 当前页库存项（已限定 TECH 耗材房）
     * @return 选品行
     */
    public List<MedicalTechDeviceCatalogRowDto> assembleCatalogRows(List<InventoryItemModel> inventoryRows) {
        List<MedicalTechDeviceCatalogRowDto> rows = new ArrayList<>();
        if (inventoryRows == null || inventoryRows.isEmpty()) {
            return rows;
        }
        // Pre：批量加载本页耗材产品定义
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
        for (InventoryItemModel inv : inventoryRows) {
            if (inv == null || inv.getItemId() == null) {
                continue;
            }
            String tableName = inv.getItemTable();
            if (StringUtils.isEmpty(tableName)) {
                tableName = TableNameConstants.ADM_DEVICE_DEFINITION;
            } else {
                tableName = tableName.trim();
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(tableName, inv.getItemId());
            if (!seenKeys.add(key)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(tableName);
            lookup.setProductDefinitionId(inv.getItemId());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        List<ProductDefinitionDisplayDto> products = new ArrayList<>(productByKey.values());
        Map<String, List<ChargeItemDefinition>> priceByKey =
            this.clinicalCatalogPriceResolver.loadPriceLinesGrouped(products);
        for (InventoryItemModel inv : inventoryRows) {
            if (inv == null || inv.getItemId() == null) {
                continue;
            }
            String tableName = inv.getItemTable();
            if (StringUtils.isEmpty(tableName)) {
                tableName = TableNameConstants.ADM_DEVICE_DEFINITION;
            } else {
                tableName = tableName.trim();
            }
            // 仅耗材定义表
            if (!TableNameConstants.ADM_DEVICE_DEFINITION.equals(tableName)) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(tableName, inv.getItemId());
            ProductDefinitionDisplayDto productRow = productByKey.get(compositeKey);
            // 无有效产品定义：跳过
            if (productRow == null) {
                continue;
            }
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(compositeKey, List.of());
            MedicalTechDeviceCatalogRowDto dto = new MedicalTechDeviceCatalogRowDto();
            dto.setDefinitionId(productRow.getProductDefinitionId());
            dto.setItemName(productRow.getProductName());
            dto.setItemCode(productRow.getProductBusNo());
            dto.setSpecification(productRow.getTotalVolume());
            dto.setUnitCode(ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitCode(productRow));
            dto.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitName(productRow));
            dto.setLocationId(inv.getLocationId());
            // 批号回传写路径，供 IF-02 锁定 LOT_NUMBER_PRICE
            dto.setLotNumber(inv.getLotNumber());
            BigDecimal net = inv.getQuantity();
            if (net == null) {
                net = BigDecimal.ZERO;
            }
            // 负数量展示为 0，仍保留该库存行
            if (net.compareTo(BigDecimal.ZERO) < 0) {
                net = BigDecimal.ZERO;
            }
            dto.setAvailableQty(net);
            Map<String, ChargeItemDefinition> lotPriceIndex =
                this.clinicalCatalogPriceResolver.indexLotNumberPriceRows(lines);
            ChargeItemDefinition lotPrice = this.clinicalCatalogPriceResolver.resolveLotNumberPriceLineForLot(
                lotPriceIndex, inv.getLotNumber());
            // 无批号价则回落参考售价，避免空单价进目录
            if (lotPrice == null) {
                lotPrice = this.clinicalCatalogPriceResolver.selectReferenceSalePriceLine(lines);
            }
            if (lotPrice != null) {
                dto.setUnitPrice(lotPrice.getPriceValue());
            }
            // 单位槽：与护士划价耗材同构；行头默认拆零
            this.applyUnitBandsPreferSplit(dto, productRow, lotPrice);
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 写入 unitBands，并将行头默认到拆零槽（有则覆盖 unitCode/unitName/unitPrice）。
     *
     * @param dto        目录行
     * @param productRow 产品读模型
     * @param selected   命中价目；可空
     */
    private void applyUnitBandsPreferSplit(
        MedicalTechDeviceCatalogRowDto dto,
        ProductDefinitionDisplayDto productRow,
        ChargeItemDefinition selected) {
        String selectedPriceTypeCode = null;
        if (selected != null) {
            selectedPriceTypeCode = selected.getPriceTypeCode();
        }
        String bandPricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler
            .resolveDoctorStationBandPricingUnitCode(productRow, selectedPriceTypeCode);
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow, bandPricingUnitCode, dto.getUnitPrice());
        dto.setUnitBands(bands);
        // 耗材：默认按拆零单位划价
        ProductDefinitionCatalogUnitBandDto splitBand =
            this.findBandByKind(bands, ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT);
        if (splitBand == null || StringUtils.isEmpty(splitBand.getUnitCode())) {
            return;
        }
        dto.setUnitCode(splitBand.getUnitCode());
        dto.setUnitName(splitBand.getUnitName());
        // 仅当槽上已写出有效单价时才覆盖行头
        if (splitBand.getUnitPrice() != null
            && splitBand.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            dto.setUnitPrice(splitBand.getUnitPrice());
        }
    }

    /**
     * 按 bandKind 查找单位槽。
     *
     * @param bands    单位槽列表
     * @param bandKind 槽语义
     * @return 命中槽；未命中 null
     */
    private ProductDefinitionCatalogUnitBandDto findBandByKind(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind) {
        if (bands == null || bands.isEmpty() || StringUtils.isEmpty(bandKind)) {
            return null;
        }
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (bandKind.equals(band.getBandKind())) {
                return band;
            }
        }
        return null;
    }
}
