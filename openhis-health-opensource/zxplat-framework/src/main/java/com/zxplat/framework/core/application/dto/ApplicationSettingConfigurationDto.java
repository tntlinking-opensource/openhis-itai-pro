/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用设置配置DTO
 * 包含所有对客户端可见的设置值
 * 参考ABP的ApplicationSettingConfigurationDto
 */
@Data
public class ApplicationSettingConfigurationDto {

    /**
     * 设置值映射
     * Key: 设置名称，Value: 设置值
     */
    private Map<String, String> values = new HashMap<>();
}
