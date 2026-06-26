/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.command;

import lombok.Getter;
import lombok.Setter;

/** 退号后预约释放策略入参。 */
@Getter
@Setter
public class OutpatientRegistrationCancelCommand {

    private String cancelReason;
    private String policyCode;
}
