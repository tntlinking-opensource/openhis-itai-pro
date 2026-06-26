package com.openhis.health.integration.elep.remote.config;

import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * remote 模式 HIS 侧调用 ELEP 服务的运行时设置。
 */
@Getter
@Component
@ConditionalOnProperty(name = "openhis.elep.deployment.mode", havingValue = "remote")
public class ElepRemoteClientSettings {

    private final String baseUrl;
    private final String apiKey;

    public ElepRemoteClientSettings(ElepDeploymentRemoteProperties properties) {
        String configured = properties.getBaseUrl();
        String resolvedBaseUrl;
        if (StringUtils.hasText(configured)) {
            resolvedBaseUrl = configured;
        } else {
            resolvedBaseUrl = "http://127.0.0.1:8080";
        }
        this.baseUrl = stripTrailingSlash(resolvedBaseUrl);
        if (properties.getApiKey() == null) {
            this.apiKey = "";
        } else {
            this.apiKey = properties.getApiKey();
        }
    }

    /** 单元测试用。 */
    public ElepRemoteClientSettings(String baseUrl, String apiKey) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        if (apiKey == null) {
            this.apiKey = "";
        } else {
            this.apiKey = apiKey;
        }
    }

    private static String stripTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
