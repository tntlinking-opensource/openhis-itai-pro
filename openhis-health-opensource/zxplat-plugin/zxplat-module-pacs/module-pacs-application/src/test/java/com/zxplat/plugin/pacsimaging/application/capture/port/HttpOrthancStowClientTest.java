/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.pacsimaging.application.config.ImagingMiddlewareProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HttpOrthancStowClient} 基于本地 HttpServer 的联调单元测试。
 */
class HttpOrthancStowClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/instances", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"ParentStudy\":\"study-1\",\"ParentSeries\":\"series-1\"}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/studies/study-1", exchange -> {
            byte[] body = "{\"MainDicomTags\":{\"StudyInstanceUID\":\"1.2.3.STUDY\"}}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/series/series-1", exchange -> {
            byte[] body = "{\"MainDicomTags\":{\"SeriesInstanceUID\":\"1.2.3.SERIES\"}}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void store_parsesStudyAndSeriesUid() {
        ImagingMiddlewareProperties props = new ImagingMiddlewareProperties();
        props.getOrthanc().setEnabled(true);
        props.getOrthanc().setBaseUrl("http://127.0.0.1:" + port);
        props.getOrthanc().setUsername("orthanc");
        props.getOrthanc().setPassword("orthanc");
        HttpOrthancStowClient client = new HttpOrthancStowClient(props, new ObjectMapper());

        OrthancStowClient.StowResult result = client.store(new byte[] {0x00, 0x01, 0x02});

        assertEquals("1.2.3.STUDY", result.studyInstanceUid());
        assertEquals("1.2.3.SERIES", result.seriesInstanceUid());
    }

    @Test
    void store_rejectsEmptyBytes() {
        ImagingMiddlewareProperties props = new ImagingMiddlewareProperties();
        props.getOrthanc().setBaseUrl("http://127.0.0.1:" + port);
        HttpOrthancStowClient client = new HttpOrthancStowClient(props, new ObjectMapper());

        BusinessException ex = assertThrows(BusinessException.class, () -> client.store(new byte[0]));
        assertEquals(ErrorCodeConstants.PACS_IMAGING_STUDY_BIND_FAILED, ex.getErrorCode());
    }
}
