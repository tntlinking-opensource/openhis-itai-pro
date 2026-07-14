/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.gateway.config;

import lombok.Data;

/**
 * 银联 BPC 院点契约配置（unionpay-bpc-contract.properties）。
 */
@Data
public class BpcContractConfig {

    private String fixmedinsCode;
    private String contractName;
    private boolean enabled;
    private String transport;
    private String mid;
    private String tid;
    private String posNo;
    private String requestUrl;
    private String md5Secret;
    private String acquirerProfile;
    private String signVersion;
    private String merTradeNoRule = "PAYMENT_INDEX_TS";
    private int queryTimeoutSec = 300;
    private int pollIntervalMs = 2000;
}
