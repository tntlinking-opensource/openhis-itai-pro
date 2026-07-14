/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.elep.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * 患者历史电子处方摘要行（续方列表展示）。
 */
@Data
@Accessors(chain = true)
public class PatientElepPrescriptionSummaryQueryExtensionOut {

    /** HIS 处方主键。 */
    private Long prescriptionId;

    /** 医保电子处方号。 */
    private String hiRxno;

    /** 院内处方号。 */
    private String hospRxno;

    /** HIS 侧本地状态枚举 code。 */
    private String localStatus;

    /** 开方医师姓名。 */
    private String prscDrName;

    /** 开方科室名称。 */
    private String prscDeptName;

    /** 主诊断名称。 */
    private String mainDiagName;

    /** 药品摘要（展示用，如「阿莫西林等 3 味」）。 */
    private String drugSummary;

    /** 开方时间（UTC）。 */
    private Instant prscTime;
}
