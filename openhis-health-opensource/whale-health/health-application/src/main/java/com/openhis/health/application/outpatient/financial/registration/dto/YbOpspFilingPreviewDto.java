/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 5301 慢特病备案预览行。 */
@Data
@Accessors(chain = true)
public class YbOpspFilingPreviewDto {

    private String opspDiseCode;
    private String opspDiseName;
    private String begndate;
    private String enddate;
}
