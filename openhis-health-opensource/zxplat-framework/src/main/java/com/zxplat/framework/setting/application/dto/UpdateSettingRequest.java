/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.setting.application.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新设置请求
 *
 * @author OpenHIS Contributors
 */
@Data
public class UpdateSettingRequest {

    private String value;

    @Size(max = 512, message = "描述长度不能超过512个字符")
    private String description;
}
