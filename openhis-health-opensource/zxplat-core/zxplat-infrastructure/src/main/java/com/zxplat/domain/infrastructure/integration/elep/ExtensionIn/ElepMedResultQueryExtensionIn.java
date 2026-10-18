/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方取药结果查询命令。
 *
 * <p>职责：按医保处方号向第三方平台查询患者是否已取药及取药明细 JSON。</p>
 */
@Data
@Accessors(chain = true)
public class ElepMedResultQueryExtensionIn {

    /** 医保合同主键。 */
    private Long contractId;

    /** 医保电子处方号 {@code hi_rxno}。 */
    private String hiRxno;
}
