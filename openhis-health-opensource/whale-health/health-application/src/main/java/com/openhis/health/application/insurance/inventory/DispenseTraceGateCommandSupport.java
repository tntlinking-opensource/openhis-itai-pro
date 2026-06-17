/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.inventory;

import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.whale.chs.jilin.port.model.DispenseTraceGateCommand;

/**
 * 发放行实体 → 医保追溯门控契约快照（3505/3506 上传前置）。
 */
public final class DispenseTraceGateCommandSupport {

    private DispenseTraceGateCommandSupport() {
    }

    /**
     * 将 {@link MedicationDispense} 映射为追溯门控命令。
     *
     * @param dispense 发药行实体；为 null 时返回 null
     * @return 追溯门控快照
     */
    public static DispenseTraceGateCommand from(MedicationDispense dispense) {
        if (dispense == null) {
            return null;
        }
        return new DispenseTraceGateCommand()
            .setMedicationId(dispense.getMedicationId())
            .setUnitCode(dispense.getUnitCode())
            .setLotNumber(dispense.getLotNumber())
            .setLocationId(dispense.getLocationId())
            .setBusNo(dispense.getBusNo());
    }
}
