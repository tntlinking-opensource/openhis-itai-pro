/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.ca;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CA 服务端验签策略（{@code ca.signature.verify-mode}）。
 */
@ConfigurationProperties(prefix = "ca.signature")
public class CaSignatureProperties {

    /**
     * dsvs | bc | skip
     */
    private String verifyMode = "skip";

    public String getVerifyMode() {
        return verifyMode;
    }

    public void setVerifyMode(String verifyMode) {
        this.verifyMode = verifyMode;
    }
}
