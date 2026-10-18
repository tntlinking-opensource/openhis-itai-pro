/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方套用/续方命令。
 *
 * <p>职责：将已上传处方克隆到新就诊，供医生站「续方」场景调用第三方电子处方平台。</p>
 */
@Data
@Accessors(chain = true)
public class CloneElepPrescriptionExtensionIn {

    /** 源处方主键（HIS 侧 prescription group）。 */
    private Long sourcePrescriptionId;

    /** 目标就诊主键。 */
    private Long targetEncounterId;

    /** 患者主键，须与目标就诊归属一致。 */
    private Long patientId;

    /** 医保合同主键，决定出站 adapter 与定点信息。 */
    private Long contractId;

    /** 续方初始处方号（平台侧 init_rxno，可选）。 */
    private String initRxno;
}
