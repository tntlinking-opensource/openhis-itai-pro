/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.client;

import lombok.Builder;
import lombok.Data;

/** 医保调用上下文（经办人等信息），可选 */
@Data
@Builder(toBuilder = true)
public class InvokeContext {

    /** 经办人 ID */
    private String operatorId;
    /** 经办人姓名 */
    private String operatorName;
    /** 参保地医保区划（部分接口如 1101 需从入参或此处传入） */
    private String insuplcAdmdvs;
    /** 9001 签到 MAC 加密串（HeaSecReadInfo GetClientID，前端/插件传入） */
    private String signMac;
    /** 9001 签到 IP（前端/插件传入） */
    private String signIp;
    /** 可选：覆盖 OAuth 用户名（备用账号轮换签到） */
    private String oauthUsername;
    /** 可选：覆盖 OAuth 密码（备用账号轮换签到） */
    private String oauthPassword;
}
