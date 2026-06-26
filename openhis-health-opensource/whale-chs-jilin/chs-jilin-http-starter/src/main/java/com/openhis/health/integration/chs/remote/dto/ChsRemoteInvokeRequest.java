package com.openhis.health.integration.chs.remote.dto;

import com.openhis.chs.api.InvokeContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * remote 模式网关 invoke 请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChsRemoteInvokeRequest {

    private String contractBusNo;
    private String infNo;
    private Object input;
    private InvokeContext context;
}
