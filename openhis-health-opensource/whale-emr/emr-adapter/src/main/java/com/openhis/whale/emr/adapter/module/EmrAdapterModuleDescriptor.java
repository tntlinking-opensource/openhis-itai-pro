package com.openhis.whale.emr.adapter.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class EmrAdapterModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "emr-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-EMR Adapter";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.emr.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"emr"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PUSH;
    }

    @Override
    public String[] featureKeys() {
        return new String[] {"emr.metadata-reader"};
    }
}
