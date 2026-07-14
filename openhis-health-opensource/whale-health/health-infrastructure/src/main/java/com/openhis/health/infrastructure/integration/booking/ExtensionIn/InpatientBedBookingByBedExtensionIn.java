/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.booking.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按床位查询当前有效预约入参。 */
@Data
@Accessors(chain = true)
public class InpatientBedBookingByBedExtensionIn {

    private Long bedLocationId;
}
