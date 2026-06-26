/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 待遇检查行快照。 */
@Data
@Accessors(chain = true)
public class InsuranceTreatmentLineSnapshot {

    private String psnNo;
    private String trtChkType;
    private String fundPayType;
    private String trtEnjymntFlag;
    private String begndate;
    private String enddate;
    private String trtChkRslt;
}
