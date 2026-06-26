package com.openhis.health.integration.chs.remote.dto;

import com.openhis.chs.api.InvokeContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChsRemoteSignInRequest {

    private String contractBusNo;
    private InvokeContext context;
}
