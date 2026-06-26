/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.command;

import lombok.Getter;
import lombok.Setter;

/** 离院里程碑后触发病案主档幂等建档。 */
@Getter
@Setter
public class DischargeCaseRecordBootstrapCommand {

    private Long encounterId;
    private Long operatorId;
    private String outWayCode;
}
