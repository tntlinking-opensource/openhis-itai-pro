package com.openhis.whale.report.application.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class ReportModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "report";
    }

    @Override
    public String displayName() {
        return "Whale Report";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.report.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"report-adapter"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PORT;
    }
}
