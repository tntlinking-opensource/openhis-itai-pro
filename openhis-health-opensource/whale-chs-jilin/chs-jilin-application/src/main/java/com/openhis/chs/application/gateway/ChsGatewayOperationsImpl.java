package com.openhis.chs.application.gateway;

import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.domain.gateway.facade.ChsGatewayOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** embedded：同 JVM 网关透传 facade 实现。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsGatewayOperationsImpl implements ChsGatewayOperations {

    private final InsuranceGatewayClient gatewayClient;

    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        return gatewayClient.invoke(contractBusNo, infNo, input, context);
    }

    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        return gatewayClient.signIn(contractBusNo, context);
    }

    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        return gatewayClient.downloadFile(contractBusNo, input, context);
    }
}
