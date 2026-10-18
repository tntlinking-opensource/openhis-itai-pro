/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 套用/续方命令（Bridge）。 */
@Data
@Accessors(chain = true)
public class ElepCloneCommand {

    private Long sourcePrescriptionId;
    private Long targetEncounterId;
    private Long patientId;
    private Long contractId;
    private String contractBusNo;
    /** true=续方（initRxno=源 hi_rxno）。 */
    private Boolean continuationRx;
    /** 默认 true。 */
    private Boolean copyDiagnosis;
    /** 新院内处方号；空则自动生成。 */
    private String newHospRxno;
    /** 覆盖 mdtrtinfo 字段（新就诊信息）。 */
    private Map<String, Object> mdtrtinfoPatch;
}
