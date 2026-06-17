package com.openhis.health.bridge.mrms.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class MrmsBridgeModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "mrms-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-MRMS Bridge";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.mrms.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"mrms"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.EVENT;
    }

    @Override
    public String[] featureKeys() {
        return new String[] {"mrms.port-callback", "mrms.discharge-event"};
    }
}
