package com.openhis.whale.booking.adapter.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class BookingAdapterModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "booking-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-Booking Bridge";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.booking.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"booking"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PORT;
    }
}
