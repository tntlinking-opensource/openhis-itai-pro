package com.openhis.whale.unionpay.bpc.adapter.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 银联 BPC adapter 模块描述符。
 */
@Component
public class UnionPayBpcAdapterModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "unionpay-bpc-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-UnionPay-BPC Adapter";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.unionpay-bpc.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"unionpay-bpc"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PUSH;
    }
}
