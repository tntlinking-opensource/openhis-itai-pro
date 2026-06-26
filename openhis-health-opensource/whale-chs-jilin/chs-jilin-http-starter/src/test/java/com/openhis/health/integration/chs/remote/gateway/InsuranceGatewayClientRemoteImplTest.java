package com.openhis.health.integration.chs.remote.gateway;

import com.openhis.chs.api.ResultBody;
import com.openhis.chs.domain.gateway.facade.ChsGatewayOperations;
import com.openhis.health.integration.chs.remote.appservice.ChsGatewayOperationsRemoteAdapter;
import com.openhis.health.integration.chs.remote.appservice.ChsRemoteAppServiceClient;
import com.openhis.health.integration.chs.remote.config.ChsRemoteGatewaySettings;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InsuranceGatewayClientRemoteImplTest {

    private MockWebServer server;
    private InsuranceGatewayClientRemoteImpl client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        ChsRemoteGatewaySettings settings =
                new ChsRemoteGatewaySettings(server.url("/").toString(), "");
        ChsRemoteAppServiceClient remoteClient =
                new ChsRemoteAppServiceClient(RestClient.builder(), settings, new com.fasterxml.jackson.databind.ObjectMapper());
        ChsGatewayOperations gatewayOps = new ChsGatewayOperationsRemoteAdapter(remoteClient);
        client = new InsuranceGatewayClientRemoteImpl(gatewayOps);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void invokePostsToChsGatewayAppService() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"code\":200,\"data\":{\"infcode\":\"0\",\"errMsg\":\"ok\"}}"));

        ResultBody body = client.invoke("YB001", "1312", Map.of("k", "v"), null);

        assertThat(body.getInfcode()).isEqualTo("0");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/app/chsGateway/invoke");
        assertThat(request.getMethod()).isEqualTo("POST");
    }
}
