/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院医保正式结算命令。 */
@Data
@Accessors(chain = true)
public class IpInsuranceConfirmCommand {

    private Long paymentDraftId;
    private Long preSettleId;
    private Long encounterId;
}
