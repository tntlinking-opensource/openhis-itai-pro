/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.host.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * remote 模式 HIS 侧调用 ELEP 服务（embedded health-app）的配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zxplat.elep.deployment.remote")
public class ElepDeploymentRemoteProperties {

    /** ELEP 服务根地址，如 http://elep-host:8080 */
    private String baseUrl = "http://127.0.0.1:8080";

    /** 可选 API Key，对应 Header {@code X-Elep-Api-Key} */
    private String apiKey = "";
}
