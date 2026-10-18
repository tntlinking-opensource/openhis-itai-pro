/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.auth.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 开发态明文 openid 登录。
 */
@Getter
@Setter
public class MiniappDevLoginRequest {

    private String openid;
    private String appId;
    private String unionId;
}
