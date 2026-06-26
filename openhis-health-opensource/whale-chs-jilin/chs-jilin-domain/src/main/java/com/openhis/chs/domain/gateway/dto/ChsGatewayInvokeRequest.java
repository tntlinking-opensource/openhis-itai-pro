package com.openhis.chs.domain.gateway.dto;

import com.openhis.chs.api.InvokeContext;
import lombok.Data;
import lombok.experimental.Accessors;

/** {@link com.openhis.chs.application.gateway.ChsGatewayAppService#invoke} 入参。 */
@Data
@Accessors(chain = true)
public class ChsGatewayInvokeRequest {

    private String contractBusNo;
    private String infNo;
    private Object input;
    private InvokeContext context;
}
