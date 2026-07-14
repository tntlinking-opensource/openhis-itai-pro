/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.pharmacy;

import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 发药前库存充足断言（物理库存 − 待发预占，GAP-EXE-003；B.6.4 多聚合只读编排）。
 */
@Component
@RequiredArgsConstructor
public class MedicationDispenseInventoryAvailabilityGate {

    /** 库存读侧：物理库存充足断言。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 药品发放读侧：待发预占量汇总。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 发药前库存充足断言（物理库存 − 全局待发预占）。
     *
     * @param itemTableName                  库存项表名
     * @param medicationId                   药品主键
     * @param pharmacyLocationId             药房位置主键
     * @param lotNumber                      批号
     * @param requiredQuantityInBusinessUnit 业务单位需求量
     * @param businessUnitCode               业务单位编码
     * @param productDefinition              药品单位换算定义
     * @param excludeDispenseId              当前发放行主键；计入预占统计时排除自身
     */
    public void assertSufficientForMedicationDispenseWithReserve(
        String itemTableName,
        Long medicationId,
        Long pharmacyLocationId,
        String lotNumber,
        BigDecimal requiredQuantityInBusinessUnit,
        String businessUnitCode,
        MedicationProductUnitConversionSource productDefinition,
        Long excludeDispenseId) {
        BigDecimal reserveInSplitUnit = this.medicationDispenseQueryService.sumDispenseExeReserveQuantityInSplitUnit(
            pharmacyLocationId, medicationId, productDefinition, excludeDispenseId);
        this.inventoryItemQueryService.assertSufficientForMedicationDispenseGivenReserve(
            itemTableName,
            medicationId,
            pharmacyLocationId,
            lotNumber,
            requiredQuantityInBusinessUnit,
            businessUnitCode,
            productDefinition,
            reserveInSplitUnit);
    }
}
