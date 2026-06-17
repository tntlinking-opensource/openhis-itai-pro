/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊医保正式结算命令（2207，在本地落账前调用）。 */
@Data
@Accessors(chain = true)
public class OpInsuranceConfirmCommand {

    private Long paymentDraftId;
    private String chrgBchno;
    private Long encounterId;
}
