/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2001 待遇检查预览行。 */
@Data
@Accessors(chain = true)
public class YbTreatmentInfoPreviewDto {

    private String psnNo;
    private String trtChkType;
    private String fundPayType;
    private String trtEnjymntFlag;
    private String begndate;
    private String enddate;
    private String trtChkRslt;
}
