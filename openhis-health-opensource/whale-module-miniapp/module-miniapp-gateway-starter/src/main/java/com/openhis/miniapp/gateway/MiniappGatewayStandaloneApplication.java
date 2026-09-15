/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.gateway;

import com.openhis.miniapp.gateway.config.MiniappGatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 小程序 DMZ 网关独立进程入口。
 *
 * <p>打包：{@code module-miniapp-gateway-starter-*-gateway.jar}</p>
 */
@SpringBootApplication
@Import(MiniappGatewayAutoConfiguration.class)
public class MiniappGatewayStandaloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniappGatewayStandaloneApplication.class, args);
    }
}
