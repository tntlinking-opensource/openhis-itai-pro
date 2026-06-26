/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 套用/续方命令。 */
@Data
@Accessors(chain = true)
public class CloneElepPrescriptionCommand {

    private Long sourcePrescriptionId;
    private Long targetEncounterId;
    private Long patientId;
    private Long contractId;
    private String initRxno;
}
