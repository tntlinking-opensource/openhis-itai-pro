/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院医保就诊平台操作命令（2404/2405/2302 等）。 */
@Data
@Accessors(chain = true)
public class IpInsuranceEncounterCommand {

    private Long encounterId;
    private Long contractId;
}
