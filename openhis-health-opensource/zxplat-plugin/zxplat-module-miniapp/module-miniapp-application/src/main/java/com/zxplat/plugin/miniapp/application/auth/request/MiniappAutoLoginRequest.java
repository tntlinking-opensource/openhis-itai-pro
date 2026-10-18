/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.auth.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序自动登录（wx.login code）。
 */
@Getter
@Setter
public class MiniappAutoLoginRequest {

    /** 微信 wx.login 返回的 code；mock 态可传 mock:{openid} 或明文 openid。 */
    private String code;

    /** 小程序 appId（可空，回落配置 defaultAppId）。 */
    private String appId;
}
