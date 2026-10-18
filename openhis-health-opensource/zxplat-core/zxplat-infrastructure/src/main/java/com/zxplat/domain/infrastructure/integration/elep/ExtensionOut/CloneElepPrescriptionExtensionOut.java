/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 套用/续方后生成的草稿处方摘要。
 */
@Data
@Accessors(chain = true)
public class CloneElepPrescriptionExtensionOut {

    /** 新草稿处方主键。 */
    private Long prescriptionId;

    /** 院内处方号。 */
    private String hospRxno;

    /** 续方初始处方号（平台 init_rxno）。 */
    private String initRxno;

    /** HIS 侧本地状态枚举 code。 */
    private String localStatus;
}
