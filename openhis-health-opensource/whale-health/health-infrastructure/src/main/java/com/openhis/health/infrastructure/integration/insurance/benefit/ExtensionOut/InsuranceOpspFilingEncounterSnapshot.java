/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 就诊侧慢特病备案查询上下文（人员编号 + 费用性质 + 参保地）。
 */
@Data
@Accessors(chain = true)
public class InsuranceOpspFilingEncounterSnapshot {

    /** 费用性质主键。 */
    private Long contractId;

    /** 医保人员编号。 */
    private String psnNo;

    /** 参保地医保区划（跨省 5301 必传；可空则网关回落就医地）。 */
    private String insurancePoolAreaCode;
}
