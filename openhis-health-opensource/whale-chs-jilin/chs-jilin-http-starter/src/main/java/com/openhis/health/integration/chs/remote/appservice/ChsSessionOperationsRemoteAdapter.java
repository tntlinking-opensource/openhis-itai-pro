package com.openhis.health.integration.chs.remote.appservice;

import com.openhis.chs.domain.catalog.dto.request.SignInRequest;
import com.openhis.chs.domain.catalog.facade.ChsSessionOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * remote 模式：HTTP 代理至 chs-application {@code ChsSessionAppService}。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsSessionOperationsRemoteAdapter implements ChsSessionOperations {

    private static final String SERVICE = "chsSession";

    private final ChsRemoteAppServiceClient remoteClient;

    @Override
    public String signIn(SignInRequest request) {
        return remoteClient.post(SERVICE, "signIn", request, String.class);
    }
}
