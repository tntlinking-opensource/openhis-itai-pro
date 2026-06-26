package com.openhis.health.integration.chs.remote.gateway;

import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.dto.ChsFileUploadResult;
import com.openhis.chs.domain.gateway.facade.ChsGatewayOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * remote 模式 {@link InsuranceGatewayClient}：委托 {@link ChsGatewayOperations}，
 * 统一走 AppService HTTP（{@code /api/app/chsGateway/*}），不再直连 {@code /chs/gateway/*}。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class InsuranceGatewayClientRemoteImpl implements InsuranceGatewayClient {

    private final ChsGatewayOperations gatewayOperations;

    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        return gatewayOperations.invoke(contractBusNo, infNo, input, context);
    }

    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        return gatewayOperations.signIn(contractBusNo, context);
    }

    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        return gatewayOperations.downloadFile(contractBusNo, input, context);
    }

    @Override
    public ChsFileUploadResult uploadFile(
        String contractBusNo, byte[] zipBytes, String filename, InvokeContext context) {
        throw new UnsupportedOperationException("remote 模式请在对账 AppService 侧执行 9101 上传");
    }
}
