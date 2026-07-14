/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.elep.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方状态查询命令。
 *
 * <p>职责：按 HIS 处方或医保处方号查询平台侧处方状态（有效/已用/已撤销等）。</p>
 */
@Data
@Accessors(chain = true)
public class ElepQueryExtensionIn {

    /** HIS 处方主键（与 {@code hiRxno} 二选一或同时携带）。 */
    private Long prescriptionId;

    /** 医保合同主键。 */
    private Long contractId;

    /** 医保电子处方号。 */
    private String hiRxno;
}
