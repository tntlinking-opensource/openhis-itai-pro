package com.openhis.elep.application.config;

import lombok.Data;

/** 电子处方终端配置（密钥、平台 URL、PDF 路径等）。 */
@Data
public class ElepContractConfig {

    private String busNo;
    private String contractName;
    private String fixmedinsCode;
    private String fixmedinsName;
    private String admVs;
    private String eleBaseUrl;
    private boolean enabled = true;

    private String preAppId;
    private String preAppSecret;
    private String prePrvKey;
    private String prePubKey;

    private String clientId;
    private String clientSecret;
    private String username;
    private String password;
    private String scope;
    private String grantType;

    private String templatePath;
    private String outputPath;
    private String hospitalSealPath;

    private int httpTimeoutMs = 40_000;
    private boolean catalogCacheEnabled = true;
    private int catalogCacheTtlMinutes = 30;
    private int catalogSearchCacheMaxEntries = 5000;
}
