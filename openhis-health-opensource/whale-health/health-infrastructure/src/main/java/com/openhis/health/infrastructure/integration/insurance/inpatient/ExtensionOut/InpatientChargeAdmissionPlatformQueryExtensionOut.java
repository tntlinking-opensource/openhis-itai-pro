/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院医保入院登记平台快照（列表 enrichment）。
 */
@Data
@Accessors(chain = true)
public class InpatientChargeAdmissionPlatformQueryExtensionOut {

    /** 就诊主键。 */
    private Long encounterId;

    /** 医保合同主键。 */
    private Long contractId;

    /** 医保就诊 ID mdtrt_id。 */
    private String mdtrtId;

    /** 是否已在平台完成入院登记。 */
    private Boolean platformAdmitted;
}
