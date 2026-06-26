package com.openhis.health.integration.chs.remote.appservice;

import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.domain.gateway.dto.ChsFileDownloadResult;
import com.openhis.chs.domain.gateway.dto.ChsGatewayDownloadRequest;
import com.openhis.chs.domain.gateway.dto.ChsGatewayInvokeRequest;
import com.openhis.chs.domain.gateway.dto.ChsGatewaySignInRequest;
import com.openhis.chs.domain.gateway.facade.ChsGatewayOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * remote 模式：网关透传经 AppService HTTP（与 session/catalog 同构，方案 1A）。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsGatewayOperationsRemoteAdapter implements ChsGatewayOperations {

    private static final String SERVICE = "chsGateway";

    private final ChsRemoteAppServiceClient remoteClient;

    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        ChsGatewayInvokeRequest body = new ChsGatewayInvokeRequest()
                .setContractBusNo(contractBusNo)
                .setInfNo(infNo)
                .setInput(input)
                .setContext(context);
        return remoteClient.post(SERVICE, "invoke", body, ResultBody.class);
    }

    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        ChsGatewaySignInRequest body =
                new ChsGatewaySignInRequest().setContractBusNo(contractBusNo).setContext(context);
        return remoteClient.post(SERVICE, "signIn", body, String.class);
    }

    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        ChsGatewayDownloadRequest body = new ChsGatewayDownloadRequest()
                .setContractBusNo(contractBusNo)
                .setInput(input)
                .setContext(context);
        ChsFileDownloadResult result =
                remoteClient.post(SERVICE, "downloadFile", body, ChsFileDownloadResult.class);
        if (result == null) {
            return null;
        }
        return result.getContent();
    }
}
