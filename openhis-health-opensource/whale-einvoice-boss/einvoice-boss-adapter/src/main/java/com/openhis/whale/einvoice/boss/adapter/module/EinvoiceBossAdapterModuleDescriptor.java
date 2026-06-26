package com.openhis.whale.einvoice.boss.adapter.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 博思电子票据 adapter 模块描述符。
 */
@Component
public class EinvoiceBossAdapterModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "einvoice-boss-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-EInvoice Adapter";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.einvoice-boss.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"einvoice-boss"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PUSH;
    }
}
