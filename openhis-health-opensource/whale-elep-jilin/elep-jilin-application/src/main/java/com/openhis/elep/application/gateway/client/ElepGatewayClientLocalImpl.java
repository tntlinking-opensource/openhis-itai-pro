/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.application.gateway.client;

import com.openhis.elep.api.ElepGatewayClient;
import com.openhis.elep.api.dto.ElepGatewayResponse;
import com.openhis.elep.api.dto.ElepInvokeContext;
import com.openhis.elep.domain.gateway.ElepOpCode;
import com.openhis.elep.domain.gateway.ElepPlatformResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 电子处方网关客户端 — embedded 同进程实现。
 * <p>委托 {@link ElepHttpAccessor} 完成 HSE 加密 HTTP，并映射为 {@link ElepGatewayResponse}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.elep.deployment.mode", havingValue = "embedded", matchIfMissing = true)
public class ElepGatewayClientLocalImpl implements ElepGatewayClient {

    private final ElepHttpAccessor httpAccessor;

    /**
     * 调用吉林省电子处方中台 A000x 接口。
     *
     * @param contractBusNo 契约业务编码（fin_contract.bus_no）
     * @param opCode        操作码
     * @param input         业务入参（通常为 {@code JSONObject}）
     * @param context       可选上下文，embedded 模式暂未使用
     * @return 网关统一响应（含解密后的业务 JSON）
     */
    @Override
    public ElepGatewayResponse invoke(
            String contractBusNo, ElepOpCode opCode, Object input, ElepInvokeContext context) {
        // Cmd：HSE 加密 POST，SM4 解密平台外层响应
        ElepPlatformResponse platformResponse = httpAccessor.post(contractBusNo, opCode, input);
        return toGatewayResponse(platformResponse);
    }

    private static ElepGatewayResponse toGatewayResponse(ElepPlatformResponse platformResponse) {
        boolean success = platformResponse.getCode() == 0;
        String bodyJson = platformResponse.getDecryptedBody();
        if (StringUtils.isBlank(bodyJson)) {
            bodyJson = null;
        }
        String message = platformResponse.getMessage();
        if (!success && StringUtils.isBlank(message)) {
            message = "电子处方中台返回失败，code=" + platformResponse.getCode();
        }
        return new ElepGatewayResponse()
                .setSuccess(success)
                .setCode(String.valueOf(platformResponse.getCode()))
                .setMessage(message)
                .setBodyJson(bodyJson);
    }
}
