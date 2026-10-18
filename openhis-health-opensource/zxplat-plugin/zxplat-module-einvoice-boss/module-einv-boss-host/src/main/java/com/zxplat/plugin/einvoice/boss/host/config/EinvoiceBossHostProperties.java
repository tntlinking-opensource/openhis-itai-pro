/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.host.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP 中转配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "einvoice.boss.http")
public class EinvoiceBossHostProperties {

    /** 真实博思平台 base URL（含 /api/medical）。 */
    private String targetBaseUrl = "http://127.0.0.1:8080/api/medical";

    /** 连接超时（毫秒）。 */
    private int connectTimeoutMs = 5000;

    /** 读取超时（毫秒）。 */
    private int readTimeoutMs = 30000;
}
