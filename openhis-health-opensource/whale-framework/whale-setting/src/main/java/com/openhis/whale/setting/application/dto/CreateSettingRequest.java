/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建设置请求
 *
 * @author Whale Framework
 */
@Data
public class CreateSettingRequest {

    @NotBlank(message = "设置名称不能为空")
    @Size(max = 256, message = "设置名称长度不能超过256个字符")
    private String name;

    private String value;

    @Size(max = 64, message = "提供者名称长度不能超过64个字符")
    private String providerName;

    @Size(max = 256, message = "提供者键长度不能超过256个字符")
    private String providerKey;

    @Size(max = 512, message = "描述长度不能超过512个字符")
    private String description;
}
