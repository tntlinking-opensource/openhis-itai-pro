/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.application.gateway.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.application.config.ElepContractConfig;
import com.openhis.elep.application.config.ElepContractConfigLoader;
import com.openhis.elep.application.gateway.crypto.HseEncAndDecUtil;
import com.openhis.elep.application.gateway.crypto.SignUtil;
import com.openhis.elep.domain.gateway.ElepOpCode;
import com.openhis.elep.domain.gateway.ElepPlatformResponse;
import com.openhis.whale.core.application.exception.BusinessException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 电子处方中台 HTTP 调用：HSE 加密 POST、SM4 解密响应。
 */
@Slf4j
@Component
public class ElepHttpAccessor {

    public ElepPlatformResponse post(String contractBusNo, ElepOpCode opCode, Object input) {
        ElepContractConfig config = requireConfig(contractBusNo);
        String url = buildUrl(config.getEleBaseUrl(), opCode.getDefaultPath());
        JSONObject body = toJsonObject(input);
        JSONObject encrypted;
        try {
            encrypted = HseEncAndDecUtil.encryptMsg(
                    config.getPreAppId(),
                    config.getPreAppSecret(),
                    config.getPrePrvKey(),
                    opCode.getCode(),
                    body);
        } catch (Exception e) {
            throw new BusinessException("ELEP_ENCRYPT_FAILED", "电子处方请求加密失败: " + e.getMessage());
        }
        String requestJson = buildWireJson(encrypted);
        log.debug("ELEP POST {} opCode={}", url, opCode.getCode());
        HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(config.getHttpTimeoutMs())
                .body(requestJson)
                .execute();
        if (response.getStatus() != 200) {
            throw new BusinessException("ELEP_HTTP_FAILED", "电子处方网络请求失败，HTTP " + response.getStatus());
        }
        ElepPlatformResponse platformResponse = JSON.parseObject(response.body(), ElepPlatformResponse.class);
        if (platformResponse == null) {
            throw new BusinessException("ELEP_PARSE_FAILED", "电子处方响应解析失败");
        }
        if (platformResponse.getCode() == 0 && StringUtils.isNotBlank(platformResponse.getEncData())) {
            try {
                String decrypted = HseEncAndDecUtil.sm4Decrypt(
                        config.getPreAppId(), config.getPreAppSecret(), platformResponse.getEncData());
                platformResponse.setDecryptedBody(decrypted);
            } catch (Exception e) {
                throw new BusinessException("ELEP_DECRYPT_FAILED", "电子处方响应解密失败: " + e.getMessage());
            }
        }
        return platformResponse;
    }

    private static ElepContractConfig requireConfig(String contractBusNo) {
        ElepContractConfig config = ElepContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null || !config.isEnabled()) {
            throw new BusinessException("ELEP_CONFIG_MISSING", "未找到或未启用的 elep-contract 配置: " + contractBusNo);
        }
        if (StringUtils.isBlank(config.getEleBaseUrl())) {
            throw new BusinessException("ELEP_BASE_URL_MISSING", "ele_base_url 未配置: " + contractBusNo);
        }
        if (StringUtils.isAnyBlank(config.getPreAppId(), config.getPreAppSecret(), config.getPrePrvKey())) {
            throw new BusinessException("ELEP_HSE_KEY_MISSING", "pre_app_id/pre_app_secret/pre_prv_key 未配置: " + contractBusNo);
        }
        return config;
    }

    private static String buildUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private static JSONObject toJsonObject(Object input) {
        if (input instanceof JSONObject jsonObject) {
            return jsonObject;
        }
        return JSON.parseObject(JSON.toJSONString(input));
    }

    /** 对齐 ybapp {@code sendDatas} 的 wire JSON 拼接顺序。 */
    private static String buildWireJson(JSONObject encrypted) {
        String encData = encrypted.getString("encData");
        String signData = encrypted.getString("signData");
        JSONObject copy = JSON.parseObject(encrypted.toJSONString());
        copy.remove("encData");
        copy.remove("signData");
        String sorted = SignUtil.getValue(copy);
        if (sorted.endsWith("}")) {
            sorted = sorted.substring(0, sorted.length() - 1);
        }
        return sorted + ",\"encData\":\"" + encData + "\",\"signData\":\"" + signData + "\"}";
    }
}
