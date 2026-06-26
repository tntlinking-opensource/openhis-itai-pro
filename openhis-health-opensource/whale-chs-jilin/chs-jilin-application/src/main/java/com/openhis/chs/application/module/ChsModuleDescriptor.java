package com.openhis.chs.application.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class ChsModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "chs";
    }

    @Override
    public String displayName() {
        return "Whale CHS";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.chs.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"chs-jilin-adapter"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.GATEWAY;
    }
}
