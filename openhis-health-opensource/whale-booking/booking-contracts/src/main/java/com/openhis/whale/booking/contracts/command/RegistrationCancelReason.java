package com.openhis.whale.booking.contracts.command;

import lombok.Getter;
import lombok.Setter;

/** 退号后预约释放策略入参。 */
@Getter
@Setter
public class RegistrationCancelReason {

    private String cancelReason;
    private String policyCode;
}
