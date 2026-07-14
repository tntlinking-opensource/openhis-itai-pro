/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.bridge.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class BookingBridgeModuleDescriptor implements WhaleModuleDescriptor {

    public String moduleId() {
        return "booking-bridge";
    }

    public String displayName() {
        return "HIS-Booking Adapter";
    }

    public String enabledProperty() {
        return "openhis.modules.booking.enabled";
    }

    public String[] dependsOnModuleIds() {
        return new String[] {"booking"};
    }

}
