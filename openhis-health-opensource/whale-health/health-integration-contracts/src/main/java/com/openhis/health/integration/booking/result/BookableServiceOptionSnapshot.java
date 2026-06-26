/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.result;

import lombok.Getter;
import lombok.Setter;

/** 可约号源类型选项（IF-REG-01 收窄）。 */
@Getter
@Setter
public class BookableServiceOptionSnapshot {

    private Long healthcareServiceId;
    private String serviceTypeName;
}
