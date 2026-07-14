/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.crypto;

import com.openhis.chs.gateway.config.ContractConfig;
import org.springframework.stereotype.Component;

/**
 * 国密加解密（C0 骨架：encrypt=false 时透传；生产接入 SM2/SM4） */
@Component
public class ChsSmCryptoService {

    public String signSm2(String plainJson, ContractConfig config) {
        if (config == null || !config.isEncrypt()) {
            return "";
        }
        throw new UnsupportedOperationException("SM2 签名尚未配置密钥，请设置 encrypt=false 或补齐 cli_prv_key");
    }

    public Object encryptInput(String plainJson, ContractConfig config) {
        if (config == null || !config.isEncrypt()) {
            return plainJson;
        }
        throw new UnsupportedOperationException("SM4 加密尚未配置，请设置 encrypt=false");
    }

    public void decryptOutputIfNeeded(com.openhis.chs.gateway.client.ResultBody resultBody, ContractConfig config) {
        if (config == null || !config.isEncrypt() || resultBody == null) {
            return;
        }
        throw new UnsupportedOperationException("SM4 解密尚未配置，请设置 encrypt=false");
    }
}
