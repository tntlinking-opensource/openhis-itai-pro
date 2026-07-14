/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.foundation.session.request;

import lombok.Data;
import lombok.experimental.Accessors;

/** 医保会话签到入参（兼容旧路由） */
@Data
@Accessors(chain = true)
public class InsuranceSessionSignInRequest {

    /** 契约业务编号 */
    private String contractBusNo;
    /** 签到 MAC 地址 */
    private String mac;
    /** 签到 IP 地址 */
    private String ip;
}
