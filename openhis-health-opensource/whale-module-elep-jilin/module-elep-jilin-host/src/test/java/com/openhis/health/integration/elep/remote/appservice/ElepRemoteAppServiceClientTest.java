/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.elep.remote.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.elep.domain.prescription.result.ElepEncounterPrescriptionItem;
import com.openhis.health.integration.elep.remote.config.ElepRemoteClientSettings;
import com.openhis.elep.domain.snapshot.ElepRxNoSnapshot;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ElepRemoteAppServiceClientTest {

    private MockWebServer server;
    private ElepRemoteAppServiceClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        ElepRemoteClientSettings settings =
                new ElepRemoteClientSettings(server.url("/").toString(), "test-key");
        client = new ElepRemoteAppServiceClient(RestClient.builder(), settings, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void postUsesRenamedContractsUploadRoute() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"code\":200,\"data\":{\"success\":true}}"));

        client.post(
                "elepPrescription",
                "upload",
                "{}",
                com.openhis.elep.domain.prescription.result.ElepUploadResult.class);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/app/elepPrescription/upload");
    }

    @Test
    void postUnwrapsResultAndCallsAppServiceRoute() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"code\":200,\"data\":{\"hiRxno\":\"RX-001\"}}"));

        ElepRxNoSnapshot snapshot = client.post(
                "elepPrescription",
                "findRxNoByPrescriptionNo",
                "{\"value\":\"P001\"}",
                ElepRxNoSnapshot.class);

        assertThat(snapshot.getHiRxno()).isEqualTo("RX-001");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/app/elepPrescription/findRxNoByPrescriptionNo");
        assertThat(request.getHeader("X-Elep-Api-Key")).isEqualTo("test-key");
    }

    @Test
    void postOptionalReturnsEmptyWhenDataNull() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"code\":200,\"data\":null}"));

        Optional<ElepRxNoSnapshot> snapshot = client.postOptional(
                "elepPrescription",
                "findRxNoByPrescriptionNo",
                "{\"value\":\"P001\"}",
                ElepRxNoSnapshot.class);

        assertThat(snapshot).isEmpty();
    }

    @Test
    void postSupportsGenericList() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"code\":200,\"data\":[]}"));

        List<ElepEncounterPrescriptionItem> items = client.post(
                "elepPrescription",
                "listEncounterPrescriptions",
                "{}",
                new TypeReference<List<ElepEncounterPrescriptionItem>>() {});

        assertThat(items).isEmpty();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/app/elepPrescription/listEncounterPrescriptions");
    }
}
