package com.openhis.chs.application.deployment.dto;

import com.openhis.chs.api.InvokeContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChsRemoteInvokeRequest {

    private String contractBusNo;
    private String infNo;
    private Object input;
    private InvokeContext context;
}
