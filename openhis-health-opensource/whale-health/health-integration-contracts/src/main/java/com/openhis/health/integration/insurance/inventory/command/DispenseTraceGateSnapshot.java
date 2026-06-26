/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 发药追溯码门控所需发放行快照。 */
@Data
@Accessors(chain = true)
public class DispenseTraceGateSnapshot {

    private Long medicationId;
    private String unitCode;
    private String lotNumber;
    private Long locationId;
    private String busNo;
}
