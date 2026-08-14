/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 慢特病备案行快照。
 */
@Data
@Accessors(chain = true)
public class InsuranceOpspFilingLineSnapshot {

    /** 医疗类别（门诊慢病 140104 / 门诊特病 140201；5302A 打标，缺省默认 140104）。 */
    private String medicalTypeCode;

    /** 慢特病病种编码。 */
    private String opspDiseCode;

    /** 慢特病病种名称。 */
    private String opspDiseName;

    /** 备案开始日期。 */
    private String begndate;

    /** 备案结束日期。 */
    private String enddate;
}
