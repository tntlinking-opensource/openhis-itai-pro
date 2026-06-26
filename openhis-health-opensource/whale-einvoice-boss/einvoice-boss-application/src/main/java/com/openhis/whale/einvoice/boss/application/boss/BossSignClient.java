package com.openhis.whale.einvoice.boss.application.boss;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.application.profile.BossPlatformProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 博思 HTTP 签名与调用客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BossSignClient {

    private final EinvoiceBossProperties properties;
    private final List<BossPlatformProfile> platformProfiles;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用博思接口（业务 JSON 明文入参）。
     *
     * @param serviceId 博思 serviceId
     * @param payloadJson 业务 JSON 明文
     * @return 响应 JSON 字符串
     */
    public String invoke(String serviceId, String payloadJson) {
        String appId = properties.getEndpoint().getAppId();
        String appKey = properties.getEndpoint().getAppKey();
        String noise = UUID.randomUUID().toString().replace("-", "");
        String dataBase64 = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String sign = BossSignSupport.sign(appId, dataBase64, noise, appKey);

        JSONObject envelope = new JSONObject();
        envelope.put("appid", appId);
        envelope.put("data", dataBase64);
        envelope.put("noise", noise);
        envelope.put("version", "1.0");
        envelope.put("sign", sign);

        String url = resolveUrl(serviceId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(envelope.toJSONString(), headers);
        log.debug("Boss invoke serviceId={}, url={}", serviceId, url);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        return response.getBody();
    }

    private String resolveUrl(String serviceId) {
        BossPlatformProfile profile = resolveActiveProfile();
        String base = properties.getEndpoint().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = profile.resolvePath(serviceId);
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private BossPlatformProfile resolveActiveProfile() {
        String profileId = properties.getPlatform().getProfile();
        for (BossPlatformProfile profile : platformProfiles) {
            if (profile.profileId().equals(profileId)) {
                return profile;
            }
        }
        return platformProfiles.get(0);
    }
}
