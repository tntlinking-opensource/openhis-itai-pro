package com.openhis.chs.domain.gateway.dto;

import com.openhis.chs.api.InvokeContext;
import lombok.Data;
import lombok.experimental.Accessors;

/** {@link com.openhis.chs.application.gateway.ChsGatewayAppService#downloadFile} 入参。 */
@Data
@Accessors(chain = true)
public class ChsGatewayDownloadRequest {

    private String contractBusNo;
    private Object input;
    private InvokeContext context;
}
