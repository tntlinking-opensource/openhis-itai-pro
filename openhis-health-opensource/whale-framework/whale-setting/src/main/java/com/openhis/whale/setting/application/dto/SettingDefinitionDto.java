/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.application.dto;

import lombok.Data;

/**
 * 配置项定义DTO（由配置源枚举返回）
 */
@Data
public class SettingDefinitionDto {

    /** 设置名称（key） */
    private String name;

    /** 标题（展示名） */
    private String title;
}
