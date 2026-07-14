/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.booking.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/** 查询可约时段入参。 */
@Data
@Accessors(chain = true)
public class BookableSlotsQueryExtensionIn {

    private Long organizationId;
    private Long practitionerId;
    private Long healthcareServiceId;
    private LocalDate date;
}
