/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 慢特病备案行快照。 */
@Data
@Accessors(chain = true)
public class InsuranceOpspFilingLineSnapshot {

    private String opspDiseCode;
    private String opspDiseName;
    private String begndate;
    private String enddate;
}
