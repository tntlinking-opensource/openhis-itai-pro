package com.openhis.chs.application.gateway;

import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.domain.gateway.dto.ChsFileDownloadResult;
import com.openhis.chs.domain.gateway.dto.ChsGatewayDownloadRequest;
import com.openhis.chs.domain.gateway.dto.ChsGatewayInvokeRequest;
import com.openhis.chs.domain.gateway.dto.ChsGatewaySignInRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 网关透传 AppService（remote 模式：{@code POST /api/app/chsGateway/*}）。
 *
 * <p>无业务编排，仅将请求转发至 {@link InsuranceGatewayClient}。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsGatewayAppService {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 通用 infno 透传 invoke。
     *
     * @param request 契约 bus_no、infno、input 与调用上下文
     * @return 平台 ResultBody
     */
    public com.openhis.chs.api.ResultBody invoke(ChsGatewayInvokeRequest request) {
        // Cmd：直接委托网关客户端，无院内状态变更
        return gatewayClient.invoke(
                request.getContractBusNo(),
                request.getInfNo(),
                request.getInput(),
                request.getContext());
    }

    /**
     * 9001 签到透传。
     *
     * @param request 契约与 MAC/IP 上下文
     * @return sign_no
     */
    public String signIn(ChsGatewaySignInRequest request) {
        return gatewayClient.signIn(request.getContractBusNo(), request.getContext());
    }

    /**
     * 9102 文件下载透传。
     *
     * @param request 契约、9102 入参与上下文
     * @return ZIP 二进制内容包装
     */
    public ChsFileDownloadResult downloadFile(ChsGatewayDownloadRequest request) {
        byte[] content = gatewayClient.downloadFile(
                request.getContractBusNo(), request.getInput(), request.getContext());
        return new ChsFileDownloadResult().setContent(content);
    }
}
