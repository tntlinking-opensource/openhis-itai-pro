package com.openhis.whale.einvoice.boss.application.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 博思电子票据模块描述符。
 */
@Component
public class EinvoiceBossModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "einvoice-boss";
    }

    @Override
    public String displayName() {
        return "Whale EInvoice Boss";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.einvoice-boss.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"einvoice-boss-adapter"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.EVENT;
    }
}
