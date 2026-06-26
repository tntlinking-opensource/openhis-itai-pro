package com.openhis.health.integration.chs.remote.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.integration.chs.remote.config.ChsRemoteGatewaySettings;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * remote 模式：经 AppService HTTP 调用 chs-application（{@code POST /api/app/{service}/{method}}）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsRemoteAppServiceClient {

    private static final String API_KEY_HEADER = "X-Chs-Api-Key";

    private final RestClient restClient;
    private final ChsRemoteGatewaySettings settings;
    private final ObjectMapper objectMapper;

    public ChsRemoteAppServiceClient(
            RestClient.Builder restClientBuilder,
            ChsRemoteGatewaySettings settings,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    public <T> T post(String serviceName, String methodName, Object body, Class<T> returnType) {
        JsonNode data = postForDataNode(serviceName, methodName, body);
        if (returnType == Void.class || returnType == void.class) {
            return null;
        }
        return objectMapper.convertValue(data, returnType);
    }

    public <T> T post(String serviceName, String methodName, Object body, TypeReference<T> typeRef) {
        JsonNode data = postForDataNode(serviceName, methodName, body);
        return objectMapper.convertValue(data, objectMapper.constructType(typeRef));
    }

    private JsonNode postForDataNode(String serviceName, String methodName, Object body) {
        String url = settings.getBaseUrl() + "/api/app/" + serviceName + "/" + methodName;
        Object requestBody;
        if (body == null) {
            requestBody = "{}";
        } else {
            requestBody = body;
        }
        String response = restClient.post()
                .uri(url)
                .headers(this::applySecurityHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.path("success").asBoolean(false)) {
                String errorCode = root.path("errorCode").asText("REMOTE_ERROR");
                String message = root.path("message").asText("CHS remote 调用失败");
                throw new BusinessException(errorCode, message);
            }
            return root.get("data");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("REMOTE_ERROR", "解析 CHS remote 响应失败: " + ex.getMessage(), ex);
        }
    }

    private void applySecurityHeaders(HttpHeaders headers) {
        if (StringUtils.hasText(settings.getApiKey())) {
            headers.set(API_KEY_HEADER, settings.getApiKey());
        }
    }
}
