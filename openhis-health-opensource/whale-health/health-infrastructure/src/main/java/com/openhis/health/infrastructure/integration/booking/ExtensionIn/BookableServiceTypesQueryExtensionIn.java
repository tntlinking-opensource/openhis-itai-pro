/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.booking.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/** 查询当日可约号源类型入参。 */
@Data
@Accessors(chain = true)
public class BookableServiceTypesQueryExtensionIn {

    private Long organizationId;
    private Long practitionerId;
    private LocalDate date;
}
