/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.host.proxy;

import com.zxplat.plugin.chs.gateway.config.ChsGatewayAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * gateway-proxy 独立进程入口（医保网段部署）。
 *
 * <p>打包：{@code module-chs-jilin-host-*-gateway-proxy.jar}</p>
 */
@SpringBootApplication
@Import({ChsGatewayProxyConfiguration.class, ChsGatewayAutoConfiguration.class, ChsGatewayProxySessionAutoConfiguration.class})
public class ChsGatewayProxyApplication {
}
