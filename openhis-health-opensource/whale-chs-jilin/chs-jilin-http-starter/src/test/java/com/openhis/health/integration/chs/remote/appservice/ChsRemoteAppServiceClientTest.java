package com.openhis.health.integration.chs.remote.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.integration.chs.remote.config.ChsRemoteGatewaySettings;
import com.openhis.chs.domain.catalog.dto.request.SignInRequest;
import com.openhis.whale.core.application.dto.PagedResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ChsRemoteAppServiceClientTest {

    private MockWebServer server;
    private ChsRemoteAppServiceClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        ChsRemoteGatewaySettings settings =
                new ChsRemoteGatewaySettings(server.url("/").toString(), "test-key");
        client = new ChsRemoteAppServiceClient(RestClient.builder(), settings, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void postUnwrapsResultAndCallsAppServiceRoute() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"code\":200,\"data\":\"SIGN-001\"}"));

        String signNo = client.post("chsSession", "signIn", new SignInRequest().setContractBusNo("YB001"), String.class);

        assertThat(signNo).isEqualTo("SIGN-001");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/app/chsSession/signIn");
        assertThat(request.getHeader("X-Chs-Api-Key")).isEqualTo("test-key");
    }

    @Test
    void postSupportsGenericPagedResult() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"code\":200,\"data\":{\"content\":[],\"totalCount\":0,\"pageNo\":1,\"pageSize\":20}}"));

        PagedResult<?> page = client.post(
                "chsNationalCatalog",
                "queryNationalCatalogPage",
                "{}",
                new TypeReference<PagedResult<Object>>() {});

        assertThat(page.getTotalCount()).isZero();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/app/chsNationalCatalog/queryNationalCatalogPage");
    }
}
