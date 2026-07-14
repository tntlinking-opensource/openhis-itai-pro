/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class BookingModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "booking";
    }

    @Override
    public String displayName() {
        return "Whale Booking";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.booking.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"booking-bridge"};
    }

}
