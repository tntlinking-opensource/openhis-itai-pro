/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfopenapi.request;

import com.zxplat.plugin.campus.application.campusgfrequestrecord.request.CreateAdjustRequestRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAdjustRequestFromOpenApiRequest extends CreateAdjustRequestRequest {

    @NotBlank
    private String securityKey;

    /**
     * 遗留兼容：为 true 且未传 chargeModeEnum 时默认 SELF_PAY_GF。
     */
    private Boolean legacyDefaultModeCompat;
}
