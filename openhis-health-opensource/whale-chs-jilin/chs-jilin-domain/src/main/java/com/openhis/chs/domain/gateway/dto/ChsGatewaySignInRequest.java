package com.openhis.chs.domain.gateway.dto;

import com.openhis.chs.api.InvokeContext;
import lombok.Data;
import lombok.experimental.Accessors;

/** {@link com.openhis.chs.application.gateway.ChsGatewayAppService#signIn} 入参。 */
@Data
@Accessors(chain = true)
public class ChsGatewaySignInRequest {

    private String contractBusNo;
    private InvokeContext context;
}
