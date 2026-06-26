package com.openhis.chs.application.deployment.web;

import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.application.deployment.dto.ChsRemoteInvokeRequest;
import com.openhis.chs.application.deployment.dto.ChsRemoteSignInRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * remote 模式网关 HTTP 入口（仅 chs-server 进程暴露）。
 */
@RestController
@RequestMapping("/chs/gateway")
public class ChsGatewayController {

    private static final Logger log = LoggerFactory.getLogger(ChsGatewayController.class);
    private static final String API_KEY_HEADER = "X-Chs-Api-Key";

    private final InsuranceGatewayClient gatewayClient;
    private final String configuredApiKey;

    public ChsGatewayController(
            InsuranceGatewayClient gatewayClient,
            @Value("${openhis.chs.deployment.remote.api-key:}") String configuredApiKey) {
        this.gatewayClient = gatewayClient;
        this.configuredApiKey = configuredApiKey;
    }

    /** remote 模式：透传 invoke 至 chs-server。 */
    @PostMapping("/invoke")
    public ResultBody invoke(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody ChsRemoteInvokeRequest request) {
        assertApiKey(apiKey);
        long startNanos = System.nanoTime();
        try {
            ResultBody body = gatewayClient.invoke(
                    request.getContractBusNo(),
                    request.getInfNo(),
                    request.getInput(),
                    request.getContext());
            String infcode = null;
            if (body != null) {
                infcode = body.getInfcode();
            }
            log.info(
                    "CHS server remote invoke: contractBusNo={}, infno={}, infcode={}, durationMs={}",
                    request.getContractBusNo(),
                    request.getInfNo(),
                    infcode,
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return body;
        } catch (RuntimeException ex) {
            log.warn(
                    "CHS server remote invoke failed: contractBusNo={}, infno={}, durationMs={}, error={}",
                    request.getContractBusNo(),
                    request.getInfNo(),
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    ex.toString());
            throw ex;
        }
    }

    /** remote 模式：9001 签到。 */
    @PostMapping("/sign-in")
    public String signIn(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody ChsRemoteSignInRequest request) {
        assertApiKey(apiKey);
        long startNanos = System.nanoTime();
        try {
            String signNo = gatewayClient.signIn(request.getContractBusNo(), request.getContext());
            log.info(
                    "CHS server remote signIn: contractBusNo={}, durationMs={}",
                    request.getContractBusNo(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return signNo;
        } catch (RuntimeException ex) {
            log.warn(
                    "CHS server remote signIn failed: contractBusNo={}, durationMs={}, error={}",
                    request.getContractBusNo(),
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    ex.toString());
            throw ex;
        }
    }

    /** remote 模式：9102 文件下载（二进制流）。 */
    @PostMapping(value = "/download-file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] downloadFile(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody ChsRemoteInvokeRequest request) {
        assertApiKey(apiKey);
        long startNanos = System.nanoTime();
        try {
            byte[] bytes = gatewayClient.downloadFile(
                    request.getContractBusNo(),
                    request.getInput(),
                    request.getContext());
            int size = 0;
            if (bytes != null) {
                size = bytes.length;
            }
            log.info(
                    "CHS server remote downloadFile: contractBusNo={}, size={}, durationMs={}",
                    request.getContractBusNo(),
                    size,
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return bytes;
        } catch (RuntimeException ex) {
            log.warn(
                    "CHS server remote downloadFile failed: contractBusNo={}, durationMs={}, error={}",
                    request.getContractBusNo(),
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    ex.toString());
            throw ex;
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }

    private void assertApiKey(String apiKey) {
        if (!StringUtils.hasText(configuredApiKey)) {
            return;
        }
        if (!configuredApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid CHS API key");
        }
    }
}

