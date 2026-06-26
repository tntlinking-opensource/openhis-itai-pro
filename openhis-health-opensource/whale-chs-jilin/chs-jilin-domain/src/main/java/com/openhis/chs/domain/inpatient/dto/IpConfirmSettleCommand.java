/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2304 正式结算命令。 */
@Data
@Accessors(chain = true)
public class IpConfirmSettleCommand {

    private Long paymentDraftId;
    private Long preSettleId;
    private Long operatorId;
    private Long tenantId;
}
