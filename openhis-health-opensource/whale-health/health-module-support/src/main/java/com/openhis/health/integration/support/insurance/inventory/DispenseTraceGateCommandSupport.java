/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.insurance.inventory;

import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.integration.insurance.inventory.command.DispenseTraceGateSnapshot;

/**
 * 发放行实体 → 第三方追溯门控契约快照（3505/3506 上传前置）。
 */
public final class DispenseTraceGateCommandSupport {

    private DispenseTraceGateCommandSupport() {
    }

    /**
     * 将 {@link MedicationDispense} 映射为追溯门控快照。
     *
     * @param dispense 发药行实体；为 null 时返回 null
     * @return 追溯门控快照
     */
    public static DispenseTraceGateSnapshot from(MedicationDispense dispense) {
        if (dispense == null) {
            return null;
        }
        return new DispenseTraceGateSnapshot()
            .setMedicationId(dispense.getMedicationId())
            .setUnitCode(dispense.getUnitCode())
            .setLotNumber(dispense.getLotNumber())
            .setLocationId(dispense.getLocationId())
            .setBusNo(dispense.getBusNo());
    }
}
