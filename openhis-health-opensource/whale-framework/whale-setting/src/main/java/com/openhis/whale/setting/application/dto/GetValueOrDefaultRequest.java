/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 获取设置值（不存在时返回默认值）请求（多参合并为单参）
 *
 * @author Whale Framework
 */
@Data
public class GetValueOrDefaultRequest {

    @NotBlank(message = "设置名称不能为空")
    private String name;

    /** 不存在时返回的默认值，可为 null */
    private String defaultValue;
}
