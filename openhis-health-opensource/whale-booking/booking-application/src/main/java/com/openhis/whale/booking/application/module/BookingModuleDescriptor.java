package com.openhis.whale.booking.application.module;

import com.openhis.whale.core.module.IntegrationMode;
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
        return new String[] {"booking-adapter"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PORT;
    }
}
