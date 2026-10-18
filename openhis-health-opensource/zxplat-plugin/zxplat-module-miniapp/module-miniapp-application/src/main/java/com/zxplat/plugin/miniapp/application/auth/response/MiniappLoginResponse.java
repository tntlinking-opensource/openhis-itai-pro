/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.auth.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录响应。
 */
@Getter
@Setter
public class MiniappLoginResponse {

    private String token;
    private Long accountId;
    private String sessionId;
    private Long patientId;
    private Long practitionerId;
    private Boolean bindRequired;
    private String openid;
    private String appId;
}
