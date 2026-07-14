/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GetEffectiveValueRequest {

    @NotBlank(message = "设置名称不能为空")
    private String name;
}
