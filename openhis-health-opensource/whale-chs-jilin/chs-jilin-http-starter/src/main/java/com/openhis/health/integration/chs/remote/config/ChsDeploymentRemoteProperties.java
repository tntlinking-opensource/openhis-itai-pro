package com.openhis.health.integration.chs.remote.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * remote 模式 HIS 侧调用 chs-application 的配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "openhis.chs.deployment.remote")
public class ChsDeploymentRemoteProperties {

    /** chs-application 根地址，如 http://chs-host:8091 */
    private String baseUrl = "http://127.0.0.1:8091";

    /** 可选 API Key，对应 Header {@code X-Chs-Api-Key} */
    private String apiKey = "";
}
