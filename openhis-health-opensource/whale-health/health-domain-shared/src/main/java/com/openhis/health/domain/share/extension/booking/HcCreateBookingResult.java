/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.booking;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检号源下单结果（跨模块 SPI）。
 */
@Getter
@Setter
public class HcCreateBookingResult {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务号。 */
    private String appointmentBusNo;
}
