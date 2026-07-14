/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.snapshot;

import lombok.Data;
import lombok.experimental.Accessors;

/** 医保电子处方号快照（2204 查药、续方等）。 */
@Data
@Accessors(chain = true)
public class ElepRxNoSnapshot {

    private String hiRxno;
    private String hospRxno;
    private String localStatus;
}
