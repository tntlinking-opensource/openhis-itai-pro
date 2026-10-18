/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医保电子处方号快照（2204 挂接、发药查方用）。
 */
@Data
@Accessors(chain = true)
public class ElepRxNoQueryExtensionOut {

    /** 医保电子处方号 {@code hi_rxno}。 */
    private String hiRxno;

    /** 院内处方号。 */
    private String hospRxno;

    /** HIS 侧本地状态枚举 code。 */
    private String localStatus;
}
