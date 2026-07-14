/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 退号后预约释放策略入参。
 */
@Getter
@Setter
public class RegistrationCancelReason {

    /** 退号原因文案。 */
    private String cancelReason;

    /** 策略码（覆盖院点默认 onCancelPolicy，可选）。 */
    private String policyCode;

    /** 操作人主键（释号审计）。 */
    private Long operatorId;
}
