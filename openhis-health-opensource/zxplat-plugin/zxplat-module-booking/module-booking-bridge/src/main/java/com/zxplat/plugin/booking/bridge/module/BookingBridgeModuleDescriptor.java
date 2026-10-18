/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class BookingBridgeModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "booking-bridge";
    }

    @Override
    public String displayName() {
        return "HIS-Booking Adapter";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.booking.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"booking"};
    }
}
