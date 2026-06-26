package com.openhis.chs.domain.gateway.facade;

import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;

/**
 * 医保网关透传能力（chs-application 实现；bridge remote 经 AppService HTTP 代理）。
 * <p>供 bridge 内结算挂接等场景注入；业务编排优先使用领域 facade（如 {@code ChsSessionOperations}）。</p>
 */
public interface ChsGatewayOperations {

    ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context);

    String signIn(String contractBusNo, InvokeContext context);

    byte[] downloadFile(String contractBusNo, Object input, InvokeContext context);
}
