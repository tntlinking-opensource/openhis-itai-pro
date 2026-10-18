/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院平台就诊操作命令（2404/2405 等，由 Client 补全 mdtrt_id）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeEncounterExtensionIn {

    /** 就诊 id。 */
    private Long encounterId;
    /** 费用性质 id。 */
    private Long contractId;
}
