/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * imaging 中间件连接配置（序号 57～58）。
 *
 * <p>前缀：{@code openhis.modules.pacs.imaging}。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "openhis.modules.pacs.imaging")
public class ImagingMiddlewareProperties {

    /** Orthanc 归档。 */
    private Orthanc orthanc = new Orthanc();

    /** MinIO 关键图桶。 */
    private Minio minio = new Minio();

    /** C-STORE 回调通知（Orthanc Lua → Whale）。 */
    private Notify notify = new Notify();

    /**
     * MWL 查询开关（仅文档/配置开关；{@code listModalityWorklist} 始终可调，院点可据此决定是否挂同步脚本）。
     */
    private boolean mwlEnabled = true;

    @Getter
    @Setter
    public static class Notify {
        /** 回调共享密钥；请求头 {@code X-Imaging-Notify-Token} 须与之相等。 */
        private String token = "whale-imaging-notify";

        /** 回调缺 {@code X-Tenant-Id} 时使用的默认租户。 */
        private Long defaultTenantId;
    }

    @Getter
    @Setter
    public static class Orthanc {
        /** 为 true 时装配真实 HttpOrthancStowClient。 */
        private boolean enabled = false;
        /** 例：http://localhost:8042 */
        private String baseUrl = "http://localhost:8042";
        private String username = "orthanc";
        private String password = "orthanc";
        /** HTTP 连接超时毫秒。 */
        private int connectTimeoutMs = 5000;
        /** HTTP 读超时毫秒。 */
        private int readTimeoutMs = 60000;
    }

    @Getter
    @Setter
    public static class Minio {
        /** 为 true 时装配真实 MinioKeyImageStoreClient。 */
        private boolean enabled = false;
        /** 例：http://localhost:9000 */
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "key-images";
        /** 预签名 URL 有效秒数。 */
        private int presignExpirySeconds = 600;
    }
}
