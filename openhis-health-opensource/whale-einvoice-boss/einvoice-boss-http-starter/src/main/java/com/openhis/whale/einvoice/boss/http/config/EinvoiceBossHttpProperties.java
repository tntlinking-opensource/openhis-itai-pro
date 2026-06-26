package com.openhis.whale.einvoice.boss.http.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP 中转配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "einvoice.boss.http")
public class EinvoiceBossHttpProperties {

    /** 真实博思平台 base URL（含 /api/medical）。 */
    private String targetBaseUrl = "http://127.0.0.1:8080/api/medical";
}
