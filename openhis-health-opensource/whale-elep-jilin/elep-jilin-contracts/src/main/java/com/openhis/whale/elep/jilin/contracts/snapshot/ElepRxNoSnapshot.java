/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.elep.jilin.contracts.snapshot;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2204 挂接用 hi_rxno 快照。 */
@Data
@Accessors(chain = true)
public class ElepRxNoSnapshot {

    private String hiRxno;
    private String hospRxno;
    private String localStatus;
}
