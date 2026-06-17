/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 发药追溯码门控所需发放行快照（避免 contracts 依赖 health-domain 实体）。 */
@Data
@Accessors(chain = true)
public class DispenseTraceGateCommand {

    private Long medicationId;
    private String unitCode;
    private String lotNumber;
    private Long locationId;
    private String busNo;
}
