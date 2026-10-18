/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.sign.request;

import lombok.Data;
import lombok.experimental.Accessors;

/** 医保签到入参 */
@Data
@Accessors(chain = true)
public class SignInsuranceRequest {

    /** 契约业务编号 */
    private String contractBusNo;
    /** 操作员 ID */
    private String operatorId;
    /** 操作员姓名 */
    private String operatorName;
    /** 签到 MAC 地址 */
    private String signMac;
    /** 签到 IP 地址 */
    private String signIp;
}
