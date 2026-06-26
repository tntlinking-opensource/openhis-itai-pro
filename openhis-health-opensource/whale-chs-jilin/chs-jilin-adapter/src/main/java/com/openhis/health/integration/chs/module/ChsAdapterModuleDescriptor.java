package com.openhis.health.integration.chs.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class ChsAdapterModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "chs-jilin-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-CHS Adapter";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.chs.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"chs"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PUSH;
    }

    @Override
    public String[] featureKeys() {
        return new String[] {
                "insurance.gateway-bridge",
                "insurance.catalog",
                "insurance.session",
                "insurance.settlement-hook"
        };
    }
}
