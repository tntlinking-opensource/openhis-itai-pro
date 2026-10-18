/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.auth.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MiniappSessionResponse {

    private Long accountId;
    private String sessionId;
    private Long patientId;
    private Long practitionerId;
    private String openid;
    private String appId;
    private Boolean bindRequired;
}
