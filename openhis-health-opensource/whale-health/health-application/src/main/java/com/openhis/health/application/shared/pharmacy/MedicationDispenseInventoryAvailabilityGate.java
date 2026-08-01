/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.pharmacy;

import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 发药前库存充足断言（物理库存 − 待发预占，GAP-EXE-003；B.6.4 多聚合只读编排）。
 *
 * <p>物理量与预占必须同口径：有批号时双方均按该批号收窄，禁止「物理按批、预占按品」混算。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicationDispenseInventoryAvailabilityGate {

    /** 库存读侧：物理库存充足断言。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 药品发放读侧：待发预占量汇总。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 发药前库存充足断言（物理库存 − 待发预占；批号非空时预占仅同批）。
     *
     * <p>不足时异常文案会带上药品展示名（及批号），便于药房定位具体品种。</p>
     *
     * @param itemTableName                  库存项表名
     * @param medicationId                   药品主键
     * @param pharmacyLocationId             药房位置主键
     * @param lotNumber                      批号；非空则物理与预占均按该批过滤
     * @param requiredQuantityInBusinessUnit 业务单位需求量
     * @param businessUnitCode               业务单位编码
     * @param productDefinition              药品目录投影（单位换算 + 展示名）
     * @param excludeDispenseId              当前发放行主键；计入预占统计时排除自身
     */
    public void assertSufficientForMedicationDispenseWithReserve(
        String itemTableName,
        Long medicationId,
        Long pharmacyLocationId,
        String lotNumber,
        BigDecimal requiredQuantityInBusinessUnit,
        String businessUnitCode,
        ProductDefinitionDisplayDto productDefinition,
        Long excludeDispenseId) {
        // Pre：目录投影 → 换算源与展示名，供拆零换算与不足文案
        MedicationProductUnitConversionSource conversionSource =
            ProductDefinitionDisplaySupport.toUnitConversionSource(productDefinition);
        String productDisplayName =
            ProductDefinitionDisplaySupport.resolveProductDisplayName(productDefinition, "药品");
        // Pre：待发预占与物理批号同口径汇总（排除当前行，避免自占）
        BigDecimal reserveInSplitUnit = this.medicationDispenseQueryService.sumDispenseExeReserveQuantityInSplitUnit(
            pharmacyLocationId, medicationId, conversionSource, excludeDispenseId, lotNumber);
        // Gate：物理(批) − 预占(同批) ≥ 本行需求
        this.inventoryItemQueryService.assertSufficientForMedicationDispenseGivenReserve(
            itemTableName,
            medicationId,
            pharmacyLocationId,
            lotNumber,
            requiredQuantityInBusinessUnit,
            businessUnitCode,
            conversionSource,
            reserveInSplitUnit,
            productDisplayName);
    }
}
