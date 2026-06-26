/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院平台就诊操作命令（2404/2405/2302 等，由 ContextLoader 补全 mdtrt_id）。 */
@Data
@Accessors(chain = true)
public class IpInsuranceEncounterCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 费用性质 id。 */
    private Long contractId;
}
