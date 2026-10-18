/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.auth.wechat;

import java.util.Map;

import com.zxplat.plugin.miniapp.application.config.MiniappProperties;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 微信 jscode2session 客户端。
 */
@Slf4j
@Component
public class WechatCode2SessionClient {

    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final MiniappProperties miniappProperties;
    private final RestTemplate miniappRestTemplate;

    public WechatCode2SessionClient(
            MiniappProperties miniappProperties,
            @Qualifier("miniappRestTemplate") RestTemplate miniappRestTemplate) {
        this.miniappProperties = miniappProperties;
        this.miniappRestTemplate = miniappRestTemplate;
    }

    /**
     * 用 wx.login code 换 openid / unionid / session_key。
     */
    public Code2SessionResult exchange(String appId, String code) {
        if (appId == null || appId.isBlank() || code == null || code.isBlank()) {
            throw new ValidationException("code2session 失败：appId 与 code 不能为空");
        }
        String secret = miniappProperties.getWechatAppSecret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(
                "WECHAT_SECRET_MISSING", "未配置 zxplat.miniapp.wechat-app-secret");
        }
        String url = UriComponentsBuilder.fromHttpUrl(CODE2SESSION_URL)
            .queryParam("appid", appId.trim())
            .queryParam("secret", secret.trim())
            .queryParam("js_code", code.trim())
            .queryParam("grant_type", "authorization_code")
            .toUriString();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = miniappRestTemplate.getForObject(url, Map.class);
            if (body == null) {
                throw new BusinessException("WECHAT_CODE2SESSION_FAILED", "微信登录无响应");
            }
            Object errCode = body.get("errcode");
            if (errCode != null && !"0".equals(String.valueOf(errCode))) {
                String errMsg = String.valueOf(body.getOrDefault("errmsg", "unknown"));
                log.warn("wechat code2session failed: errcode={}, errmsg={}", errCode, errMsg);
                throw new BusinessException(
                    "WECHAT_CODE2SESSION_FAILED", "微信登录失败：" + errMsg);
            }
            Object openid = body.get("openid");
            if (openid == null || String.valueOf(openid).isBlank()) {
                throw new BusinessException("WECHAT_CODE2SESSION_FAILED", "微信登录未返回 openid");
            }
            Code2SessionResult result = new Code2SessionResult();
            result.openid = String.valueOf(openid);
            Object unionId = body.get("unionid");
            if (unionId != null && !String.valueOf(unionId).isBlank()) {
                result.unionId = String.valueOf(unionId);
            }
            Object sessionKey = body.get("session_key");
            if (sessionKey != null) {
                result.sessionKey = String.valueOf(sessionKey);
            }
            return result;
        } catch (RestClientException ex) {
            log.warn("wechat code2session http error", ex);
            throw new BusinessException("WECHAT_CODE2SESSION_FAILED", "微信登录网络异常");
        }
    }

    @Getter
    public static class Code2SessionResult {
        private String openid;
        private String unionId;
        private String sessionKey;
    }
}
