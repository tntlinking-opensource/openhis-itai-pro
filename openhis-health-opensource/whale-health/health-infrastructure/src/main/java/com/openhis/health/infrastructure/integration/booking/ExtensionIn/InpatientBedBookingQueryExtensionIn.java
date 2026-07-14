/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.booking.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/** 按床位与拟入院日期查询预约入参。 */
@Data
@Accessors(chain = true)
public class InpatientBedBookingQueryExtensionIn {

    private Long bedLocationId;
    private LocalDate intendedDate;
}
