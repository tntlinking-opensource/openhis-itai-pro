/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按预约主键加载门诊预约入参。 */
@Data
@Accessors(chain = true)
public class OutpatientBookingLoadExtensionIn {

    /** 预约主键。 */
    private Long appointmentId;
}
