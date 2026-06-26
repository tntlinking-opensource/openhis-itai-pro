/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院平台就诊操作命令（2404/2405 等，由 Port 补全 mdtrt_id）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeEncounterCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 费用性质 id。 */
    private Long contractId;
}
