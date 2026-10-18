/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class BookingModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "booking";
    }

    @Override
    public String displayName() {
        return "ZXPlat Booking";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.booking.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"booking-bridge"};
    }

}
