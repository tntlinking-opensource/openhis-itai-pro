package com.openhis.whale.mrms.application.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class MrmsModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "mrms";
    }

    @Override
    public String displayName() {
        return "Whale MRMS";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.mrms.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"mrms-adapter"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.EVENT;
    }
}
