package com.openhis.health.integration.chs.remote.config;

import com.openhis.chs.domain.support.ChsTextSupport;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * remote 模式 HIS 侧调用 chs-application 的运行时设置。
 */
@Getter
@Component
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsRemoteGatewaySettings {

    private final String baseUrl;
    private final String apiKey;

    public ChsRemoteGatewaySettings(ChsDeploymentRemoteProperties properties) {
        String configured = properties.getBaseUrl();
        String resolvedBaseUrl;
        if (StringUtils.hasText(configured)) {
            resolvedBaseUrl = configured;
        } else {
            resolvedBaseUrl = "http://127.0.0.1:8091";
        }
        this.baseUrl = ChsTextSupport.stripTrailingSlash(resolvedBaseUrl);
        if (properties.getApiKey() == null) {
            this.apiKey = "";
        } else {
            this.apiKey = properties.getApiKey();
        }
    }

    /** 单元测试用。 */
    public ChsRemoteGatewaySettings(String baseUrl, String apiKey) {
        this.baseUrl = ChsTextSupport.stripTrailingSlash(baseUrl);
        if (apiKey == null) {
            this.apiKey = "";
        } else {
            this.apiKey = apiKey;
        }
    }
}
