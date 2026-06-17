package com.openhis.whale.report.adapter.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class ReportAdapterModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "report-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-Report Bridge";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.report.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"report"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PORT;
    }
}
