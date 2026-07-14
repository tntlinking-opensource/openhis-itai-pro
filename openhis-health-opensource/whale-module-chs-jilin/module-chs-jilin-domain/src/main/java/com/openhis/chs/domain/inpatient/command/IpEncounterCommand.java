/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院平台就诊操作命令）404/2405/2302 等，ContextLoader 补全 mdtrt_id） */
@Data
@Accessors(chain = true)
public class IpEncounterCommand {

    private Long encounterId;
    private Long contractId;
}
