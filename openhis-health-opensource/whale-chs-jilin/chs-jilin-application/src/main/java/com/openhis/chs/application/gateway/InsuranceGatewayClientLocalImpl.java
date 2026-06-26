package com.openhis.chs.application.gateway;

import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.dto.ChsFileUploadResult;
import com.openhis.chs.application.gateway.client.ChsHttpAccessor;
import com.openhis.chs.application.gateway.session.InsuranceSignSessionAppService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 医保网关客户端 - 同进程实现。
 */
@Component
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "embedded", matchIfMissing = true)
public class InsuranceGatewayClientLocalImpl implements InsuranceGatewayClient {

    private final ChsHttpAccessor chsHttpAccessor;
    private final InsuranceSignSessionAppService signSessionService;

    public InsuranceGatewayClientLocalImpl(ChsHttpAccessor chsHttpAccessor,
                                           InsuranceSignSessionAppService signSessionService) {
        this.chsHttpAccessor = chsHttpAccessor;
        this.signSessionService = signSessionService;
    }

    @Override
    public ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context) {
        if (!ChsInfNo.SIGN_IN.equals(infNo)) {
            signSessionService.ensureSigned(contractBusNo, context);
        }
        return chsHttpAccessor.send(contractBusNo, infNo, input, context);
    }

    @Override
    public String signIn(String contractBusNo, InvokeContext context) {
        return signSessionService.signIn(contractBusNo, context);
    }

    @Override
    public byte[] downloadFile(String contractBusNo, Object input, InvokeContext context) {
        signSessionService.ensureSigned(contractBusNo, context);
        return chsHttpAccessor.downloadFile(contractBusNo, ChsInfNo.FILE_DOWNLOAD, input, context);
    }

    @Override
    public ChsFileUploadResult uploadFile(
        String contractBusNo, byte[] zipBytes, String filename, InvokeContext context) {
        signSessionService.ensureSigned(contractBusNo, context);
        return chsHttpAccessor.uploadFile(contractBusNo, zipBytes, filename, context);
    }
}
