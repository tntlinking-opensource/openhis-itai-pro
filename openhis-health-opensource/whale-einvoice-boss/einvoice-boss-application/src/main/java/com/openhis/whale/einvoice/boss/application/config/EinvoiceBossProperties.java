package com.openhis.whale.einvoice.boss.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 博思电子票据模块配置项。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "einvoice.boss")
public class EinvoiceBossProperties {

    /** 开票模式：async（默认）或 sync。 */
    private Issue issue = new Issue();

    /** 平台配置档标识。 */
    private Platform platform = new Platform();

    /** HTTP 端点配置。 */
    private Endpoint endpoint = new Endpoint();

    /** 待补偿 UI 配置。 */
    private Compensation compensation = new Compensation();

    @Getter
    @Setter
    public static class Compensation {
        /** 顶栏提醒阈值（≥N 条显示 Alert）。 */
        private int alertThreshold = 1;
        /** 超时待查分钟数。 */
        private int staleMinutes = 30;
    }

    @Getter
    @Setter
    public static class Issue {
        /** 开票模式。 */
        private String mode = "async";
    }

    @Getter
    @Setter
    public static class Platform {
        /** 平台 profile 标识。 */
        private String profile = "boss-medical-cn-v1";
        /** 开票点编码（博思 placeCode）。 */
        private String placeCode = "001";
    }

    @Getter
    @Setter
    public static class Endpoint {
        /** 博思 API 根路径（含 /api/medical）。 */
        private String baseUrl = "http://127.0.0.1:8080/api/medical";
        /** 博思 appId。 */
        private String appId = "";
        /** 博思 appKey。 */
        private String appKey = "";
    }
}
