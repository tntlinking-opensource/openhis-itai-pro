package com.openhis.whale.unionpay.bpc.application.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 银联 BPC 模块描述符。
 */
@Component
public class UnionPayBpcModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "unionpay-bpc";
    }

    @Override
    public String displayName() {
        return "Whale UnionPay BPC";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.unionpay-bpc.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"unionpay-bpc-adapter"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PUSH;
    }
}
