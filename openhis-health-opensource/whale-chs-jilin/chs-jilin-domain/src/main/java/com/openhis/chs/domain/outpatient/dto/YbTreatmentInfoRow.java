/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2001 待遇信息行。 */
@Data
@Accessors(chain = true)
public class YbTreatmentInfoRow {

    private String psnNo;
    private String trtChkType;
    private String fundPayType;
    private String trtEnjymntFlag;
    private String begndate;
    private String enddate;
    private String trtChkRslt;
}
