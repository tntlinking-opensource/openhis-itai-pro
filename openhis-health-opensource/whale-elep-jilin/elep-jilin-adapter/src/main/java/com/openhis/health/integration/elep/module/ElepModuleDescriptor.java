package com.openhis.health.integration.elep.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class ElepModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "elep-jilin-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-ELEP Adapter";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.elep.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"elep"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.GATEWAY;
    }

    @Override
    public String[] featureKeys() {
        return new String[] {
                "elep.prescription-extension",
                "elep.catalog-search",
                "elep.gateway"
        };
    }
}
