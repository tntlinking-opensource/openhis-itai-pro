/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 套用/续方 Client 命令。 */
@Data
@Accessors(chain = true)
public class ElepClonePrescriptionCommand {

    private Long sourcePrescriptionId;
    private Long targetEncounterId;
    private Long patientId;
    private Long contractId;
    private String contractBusNo;
    /** 续方时填源方 hi_rxno；套用时为空。 */
    private String initRxno;
    private Boolean continuationRx;
    private Boolean copyDiagnosis;
    private String newHospRxno;
    private java.util.Map<String, Object> mdtrtinfoPatch;
}
