/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序 Module 配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zxplat.miniapp")
public class MiniappProperties {

    /**
     * 患者端写路径系统操作人（adm_practitioner.id）。
     */
    private Long systemOperatorPractitionerId;

    /** 默认小程序 appId（autoLogin 未传时使用）。 */
    private String defaultAppId;

    /** 会话有效小时数。 */
    private int sessionTtlHours = 72;

    /**
     * 开发态：允许用 mock:openid 或明文 openid 登录，不调微信 code2session。
     */
    private boolean mockLoginEnabled = true;

    /** 微信 appSecret（正式 code2session 用；骨架期可空）。 */
    private String wechatAppSecret;

    /**
     * DMZ 网关共享密钥（可选）。配置后所有 {@code /api/miniapp/**} 须带
     * {@link #GATEWAY_API_KEY_HEADER}，防止绕过网关直连内网 BFF。
     */
    private String gatewayApiKey;

    public static final String GATEWAY_API_KEY_HEADER = "X-Miniapp-Gateway-Key";
}
